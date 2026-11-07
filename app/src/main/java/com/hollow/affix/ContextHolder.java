package com.hollow.affix;

import android.content.Context;

/**
 * 全局 Context 持有者，供 Logger 等静态工具在无 Context 参数时使用。
 */
public class ContextHolder {

    private static Context app;

    public static void init(Context ctx) {
        if (app == null && ctx != null) {
            app = ctx.getApplicationContext();
        }
    }

    public static Context get() {
        return app;
    }
}