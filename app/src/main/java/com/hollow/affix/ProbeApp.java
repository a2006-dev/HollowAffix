package com.hollow.affix;

import android.app.Application;

public class ProbeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ContextHolder.init(this);
        // 一次性修正：之前误框选的错误区域存进了 SharedPreferences，
        // 启动时强制回到实测默认值（之后正常框选仍可覆盖）
        RegionStore.reset();
        Logger.initSession();
    }
}