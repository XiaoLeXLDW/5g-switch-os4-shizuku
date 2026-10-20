package dev.xiaolexldw.fiveg;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Hides only the launcher shortcut, leaving Quick Settings and deep links available. */
public final class LauncherVisibility {
    private static final String LAUNCHER_CLASS = "com.ysy.switcherfiveg.LauncherActivity";
    private static final String SETTINGS_CLASS = "com.ysy.switcherfiveg.MainActivity";

    private LauncherVisibility() { }

    public static boolean isHidden(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(launcher(context));
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    public static void setHidden(Context context, boolean hidden) {
        context.getPackageManager().setComponentEnabledSetting(launcher(context),
                hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** Always opens settings, including when no launcher shortcut is enabled. */
    public static Intent settingsIntent(Context context) {
        return new Intent().setComponent(new ComponentName(context.getPackageName(), SETTINGS_CLASS))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private static ComponentName launcher(Context context) {
        return new ComponentName(context.getPackageName(), LAUNCHER_CLASS);
    }
}
