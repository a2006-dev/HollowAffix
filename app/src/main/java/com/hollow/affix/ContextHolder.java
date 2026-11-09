package com.hollow.affix;

import android.content.Context;

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
