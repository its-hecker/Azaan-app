package com.hecker.azaan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        // Only schedules future alarms. Never launches playback at boot.
        AlarmScheduler.schedule(c);
    }
}
