package com.hecker.azaan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

public final class AlarmScheduler {
    private AlarmScheduler() {}
    static boolean exactAllowed(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    private static PendingIntent intent(Context c, Prayer p, long time) {
        return PendingIntent.getBroadcast(c, 100 + p.ordinal(), new Intent(c, AlarmReceiver.class)
            .setAction("com.hecker.azaan.ALARM." + p.name()).putExtra("prayer", p.name()).putExtra("scheduled", time),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static synchronized void schedule(Context c) {
        AppSettings s = new AppSettings(c);
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent refresh = PendingIntent.getBroadcast(c, 200,
            new Intent(c, RescheduleReceiver.class).setAction("com.hecker.azaan.REFRESH"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(refresh);
        for (Prayer p : Prayer.values()) {
            alarms.cancel(intent(c, p, 0));
            s.prefs.edit().remove("expected_" + p.name()).apply();
        }
        if (!s.active() || !s.configured()) return;
        // This maintenance alarm only recalculates times, never starts playback.
        // It also recovers when a twilight crossing reappears after a polar season.
        long tomorrow = LocalDate.now(s.zone()).plusDays(1).atStartOfDay(s.zone()).plusMinutes(5).toInstant().toEpochMilli();
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrow, refresh);
        // Inexact alarms cannot reliably launch media foreground services in the background.
        if (!exactAllowed(c)) { s.status("Allow Alarms & reminders to schedule azaan."); return; }
        if (!Notifications.allowed(c)) { s.status("Allow notifications, then reopen Azaan to restore alerts."); return; }
        List<AlarmPlanner.Event> events = AlarmPlanner.upcoming(Instant.now(), s.zone(), s.latitude(), s.longitude(), s.method(), s.offsets(), s.enabled());
        EnumSet<Prayer> done = EnumSet.noneOf(Prayer.class);
        try {
            for (AlarmPlanner.Event event : events) {
                if (!done.add(event.prayer)) continue;
                long at = event.time.toEpochMilli();
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(c, event.prayer, at));
                s.prefs.edit().putLong("expected_" + event.prayer.name(), at).apply();
            }
        } catch (SecurityException e) {
            for (Prayer p : Prayer.values()) alarms.cancel(intent(c, p, 0));
            s.status("Alarms permission changed. Allow Alarms & reminders and reopen Azaan.");
        }
    }
}
