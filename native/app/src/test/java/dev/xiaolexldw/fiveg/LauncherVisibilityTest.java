package dev.xiaolexldw.fiveg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public final class LauncherVisibilityTest {
    private static final String MAIN_CLASS = "com.ysy.switcherfiveg.MainActivity";
    private static final String ALIAS_CLASS = "com.ysy.switcherfiveg.LauncherActivity";
    private static final String TILE_CLASS = "com.ysy.switcherfiveg.SwitcherTileService";
    private Application application;
    private PackageManager packageManager;

    @Before public void prepare() {
        application = RuntimeEnvironment.getApplication();
        packageManager = application.getPackageManager();
    }

    @Test public void launcherQueryTracksVisibleHiddenAndRestoredState() {
        assertFalse(LauncherVisibility.isHidden(application));
        assertOnlyLauncherAlias();
        assertNotNull(packageManager.getLaunchIntentForPackage(application.getPackageName()));

        LauncherVisibility.setHidden(application, true);
        assertTrue(LauncherVisibility.isHidden(application));
        assertTrue(launcherActivities().isEmpty());
        assertNull(packageManager.getLaunchIntentForPackage(application.getPackageName()));

        LauncherVisibility.setHidden(application, false);
        assertFalse(LauncherVisibility.isHidden(application));
        assertOnlyLauncherAlias();
        assertNotNull(packageManager.getLaunchIntentForPackage(application.getPackageName()));
    }

    @Test public void readsPackageManagerStateWithoutACachedPreference() {
        ComponentName alias = component(ALIAS_CLASS);
        packageManager.setComponentEnabledSetting(alias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        assertTrue(LauncherVisibility.isHidden(application));
        packageManager.setComponentEnabledSetting(alias,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        assertFalse(LauncherVisibility.isHidden(application));
        assertOnlyLauncherAlias();
    }

    @Test public void hiddenLauncherStillResolvesQuickSettingsAndDeepLinksToRealActivity() {
        LauncherVisibility.setHidden(application, true);
        Intent quickSettings = new Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES")
                .setPackage(application.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertResolvesToMainActivity(quickSettings);
        application.startActivity(quickSettings);
        assertEquals(quickSettings.getAction(), Shadows.shadowOf(application).getNextStartedActivity().getAction());
        try (ActivityController<com.ysy.switcherfiveg.MainActivity> lifecycle =
                     Robolectric.buildActivity(com.ysy.switcherfiveg.MainActivity.class, quickSettings).setup()) {
            assertEquals(quickSettings.getAction(), lifecycle.get().getIntent().getAction());
            assertNotNull(lifecycle.get().findViewById(android.R.id.content).findViewWithTag("main-scroll"));
        }
        assertResolvesToMainActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("com.ysy.switcherfiveg://localhost")));
        assertResolvesToMainActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("com.ysy.switcherfiveg.hyperos4://localhost")));
    }

    @Test public void explicitSettingsIntentStartsRealActivityWithoutLauncher() {
        LauncherVisibility.setHidden(application, true);
        assertNull(packageManager.getLaunchIntentForPackage(application.getPackageName()));
        Intent settings = LauncherVisibility.settingsIntent(application);
        assertEquals(component(MAIN_CLASS), settings.getComponent());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP, settings.getFlags());
        assertResolvesToMainActivity(settings);
        application.startActivity(settings);
        assertEquals(component(MAIN_CLASS), Shadows.shadowOf(application).getNextStartedActivity().getComponent());
        try (ActivityController<com.ysy.switcherfiveg.MainActivity> lifecycle =
                     Robolectric.buildActivity(com.ysy.switcherfiveg.MainActivity.class, settings).setup()) {
            assertNotNull(lifecycle.get().findViewById(android.R.id.content).findViewWithTag("main-scroll"));
        }
    }

    @Test public void visibilityChangesNeverDisableRealActivityOrTileService() throws Exception {
        ComponentName main = component(MAIN_CLASS);
        ComponentName tile = component(TILE_CLASS);
        for (boolean hidden : new boolean[] {true, false, true}) {
            LauncherVisibility.setHidden(application, hidden);
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    packageManager.getComponentEnabledSetting(main));
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    packageManager.getComponentEnabledSetting(tile));
            assertTrue(packageManager.getActivityInfo(main, 0).enabled);
            assertTrue(packageManager.getServiceInfo(tile, 0).enabled);
        }
    }

    private ComponentName component(String className) {
        return new ComponentName(application.getPackageName(), className);
    }

    private List<ResolveInfo> launcherActivities() {
        return packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(application.getPackageName()), 0);
    }

    private void assertOnlyLauncherAlias() {
        List<ResolveInfo> launchers = launcherActivities();
        assertEquals(1, launchers.size());
        ActivityInfo alias = launchers.get(0).activityInfo;
        assertEquals(ALIAS_CLASS, alias.name);
        assertEquals(MAIN_CLASS, alias.targetActivity);
    }

    private void assertResolvesToMainActivity(Intent intent) {
        ResolveInfo resolved = packageManager.resolveActivity(intent, 0);
        assertNotNull(resolved);
        assertEquals(MAIN_CLASS, resolved.activityInfo.name);
    }
}
