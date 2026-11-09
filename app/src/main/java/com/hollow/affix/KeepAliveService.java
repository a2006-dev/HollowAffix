package com.hollow.affix;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class KeepAliveService extends Service {

    private static final String CH_ID = "hollow_keep";
    private static final int NOTIFY_ID = 1001;

    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        startForeground(NOTIFY_ID, buildNotification());
        Logger.appendTo("trace.txt", "KeepAliveService 已启动");
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CH_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CH_ID, "异环OCR服务", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                ch.enableLights(false);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CH_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("异环OCR服务")
                .setContentText("OCR 识别服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        Logger.appendTo("trace.txt", "KeepAliveService 已停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
