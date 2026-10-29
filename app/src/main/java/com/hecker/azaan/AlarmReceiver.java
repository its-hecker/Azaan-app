package com.hecker.azaan;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.time.Instant;

public final class AlarmReceiver extends BroadcastReceiver {
    @SuppressLint("ApplySharedPref") // Persist deduplication before handing playback to another component.
    @Override public void onReceive(Context context, Intent intent) {
        AppSettings s = new AppSettings(context);
        Prayer prayer;
        try { prayer = Prayer.valueOf(intent.getStringExtra("prayer")); }
        catch (Exception e) { return; }
        long at = intent.getLongExtra("scheduled", 0);
        long expected = s.prefs.getLong("expected_" + prayer.name(), -1);
        String key = prayer.name() + ":" + Instant.ofEpochMilli(at).atZone(s.zone()).toLocalDate();
        boolean play = at == expected && AlarmPlanner.shouldPlay(System.currentTimeMillis(), at,
            s.active() && s.prayerEnabled(prayer), key, s.prefs.getString("last_" + prayer.name(), ""));
        if (play) s.prefs.edit().putString("last_" + prayer.name(), key).commit();
        AlarmScheduler.schedule(context);
        if (!play) return;
        try {
            context.startForegroundService(new Intent(context, PlaybackService.class).setAction(PlaybackService.PLAY)
                .putExtra("prayer", prayer.name()).putExtra("automatic", true));
        } catch (RuntimeException e) {
            s.status("Android blocked azaan playback. Open the app and check alarm and battery settings.");
            Notifications.message(context, prayer.title + " prayer time", s.status());
        }
    }
}
