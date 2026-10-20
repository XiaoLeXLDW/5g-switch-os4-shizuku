/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import android.app.Application;

/** Initializes shared state without applying any previously saved radio preference. */
public final class FiveGApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        TilePreferences.label(this);
        FiveGController.get(this).refresh();
    }
}
