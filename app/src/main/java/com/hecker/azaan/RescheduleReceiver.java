package com.hecker.azaan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)
                && !"com.hecker.azaan.REFRESH".equals(action)) return;
        // Only schedules future alarms. Never launches playback at boot.
        AlarmScheduler.schedule(c);
    }
}
