package com.hecker.azaan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class Notifications {
    static final String PLAYBACK = "azaan_playback";
    static final String STATUS = "azaan_status";
    static final int PLAYBACK_ID = 1;
    private Notifications() {}
    static void channels(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        NotificationChannel playback = new NotificationChannel(PLAYBACK, "Azaan playback", NotificationManager.IMPORTANCE_LOW);
        playback.setDescription("Playback controls for azaan and translations");
        playback.setSound(null, null);
        m.createNotificationChannel(playback);
        m.createNotificationChannel(new NotificationChannel(STATUS, "Setup and playback messages", NotificationManager.IMPORTANCE_DEFAULT));
    }
    static boolean allowed(Context c) {
        return (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    static PendingIntent open(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static Notification playback(Context c, String title, String detail) {
        PendingIntent stop = PendingIntent.getService(c, 1, new Intent(c, PlaybackService.class).setAction(PlaybackService.STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, PLAYBACK).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(detail).setContentIntent(open(c))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }
    static void message(Context c, String title, String detail) {
        channels(c);
        if (!allowed(c)) return;
        c.getSystemService(NotificationManager.class).notify(2,
            new Notification.Builder(c, STATUS).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
                .setContentIntent(open(c)).setAutoCancel(true).build());
    }
}
