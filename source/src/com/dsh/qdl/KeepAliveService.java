package com.dsh.qdl;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** 只为把进程保活（前台服务 + 通知），下载线程仍在 MainActivity 里跑。 */
public class KeepAliveService extends Service {
    static final String CH = "qdl_dl";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Object builder;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                Class<?> ncc = Class.forName("android.app.NotificationChannel");
                Object channel = ncc.getConstructor(String.class, CharSequence.class, int.class)
                        .newInstance(CH, "下载进度", 2);
                NotificationManager.class.getMethod("createNotificationChannel", ncc).invoke(nm, channel);
                builder = Class.forName("android.app.Notification$Builder")
                        .getConstructor(Context.class, String.class).newInstance(this, CH);
            } else {
                builder = new Notification.Builder(this);
            }
            Class<?> bc = builder.getClass();
            bc.getMethod("setContentTitle", CharSequence.class).invoke(builder, "正在下载");
            bc.getMethod("setContentText", CharSequence.class).invoke(builder, "切到后台也会继续");
            bc.getMethod("setSmallIcon", int.class).invoke(builder, android.R.drawable.stat_sys_download);
            bc.getMethod("setOngoing", boolean.class).invoke(builder, true);
            try {
                Intent open = new Intent(this, MainActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);
                bc.getMethod("setContentIntent", PendingIntent.class).invoke(builder, pi);
            } catch (Throwable ignored) { }
            Notification n = (Notification) bc.getMethod("build").invoke(builder);
            startForeground(1001, n);
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
