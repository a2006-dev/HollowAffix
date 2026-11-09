package com.hollow.affix;

import android.app.Application;

public class ProbeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ContextHolder.init(this);
        Logger.initSession();

        new Thread(() -> {
            try {
                Logger.cleanup();
                Logger.purgeGalleryShots(this);
            } catch (Throwable ignored) {}
        }, "cleanup").start();
    }
}
