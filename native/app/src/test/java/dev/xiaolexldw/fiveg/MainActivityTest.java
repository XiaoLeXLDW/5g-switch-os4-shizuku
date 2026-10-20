/* Copyright (c) 2026 XiaoLeXLDW. All rights reserved. */
package dev.xiaolexldw.fiveg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ScrollView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    private FakeController fake;

    @Before public void prepare() {
        fake = new FakeController(state(-1, false, FiveGController.Access.READY));
        TestActivity.nextController = fake;
    }

    @Test public void stateUpdatesNeverWriteAndOnlyReadyKnownSwitchIsEnabled() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            Switch toggle = tagged(activity, "fiveg-switch");
            assertFalse(toggle.isEnabled());
            assertFalse(toggle.isChecked());
            assertEquals(0, fake.writeCount);

            fake.emit(state(1, true, FiveGController.Access.READY));
            assertTrue(toggle.isChecked());
            assertFalse(toggle.isEnabled());
            assertEquals(0, fake.writeCount);

            fake.emit(state(1, false, FiveGController.Access.READY));
            assertTrue(toggle.isEnabled());
            assertEquals("已开启", ((TextView) tagged(activity, "state-title")).getText().toString());
            assertEquals(0, fake.writeCount);

            toggle.performClick();
            assertEquals(1, fake.writeCount);
            assertFalse(fake.lastRequestedEnabled);
            assertTrue(toggle.isChecked());

            fake.emit(state(-1, false, FiveGController.Access.PERMISSION_REQUIRED));
            assertFalse(toggle.isEnabled());
            assertEquals(1, fake.writeCount);
        }
    }

    @Test public void authorizationAppearsOnlyWhenRequiredAndRequestsPermissionOnClick() {
        fake.value = state(-1, false, FiveGController.Access.PERMISSION_REQUIRED);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            assertEquals(View.VISIBLE, tagged(activity, "access-card").getVisibility());
            tagged(activity, "authorize").performClick();
            assertEquals(1, fake.permissionRequests);
            fake.emit(state(0, false, FiveGController.Access.READY));
            assertEquals(View.GONE, tagged(activity, "access-card").getVisibility());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void failedReadShowsErrorInsteadOfLoadingOrSuccess() {
        fake.value = new FiveGController.State(-1, false, "未能确认设置", "请刷新后重试。",
                FiveGController.Access.READY);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            assertEquals("未能确认设置", ((TextView) tagged(activity, "state-title")).getText().toString());
            assertEquals("请刷新后重试。", ((TextView) tagged(activity, "state-detail")).getText().toString());
            Switch toggle = tagged(activity, "fiveg-switch");
            assertFalse(toggle.isEnabled());
            assertFalse(toggle.isChecked());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void tileNameHasOriginalDefaultAndSavesSixUnicodeCodePoints() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            assertEquals("5G·", TilePreferences.label(activity));
            tagged(activity, "tile-label").performClick();
            drainMainLooper();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            EditText input = dialog.getWindow().getDecorView().findViewWithTag("tile-label-input");
            input.setText("😀😁😂😃😄😅😆");
            String accepted = input.getText().toString();
            assertEquals(6, accepted.codePointCount(0, accepted.length()));
            assertEquals("😀😁😂😃😄😅", accepted);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            drainMainLooper();
            assertFalse(dialog.isShowing());
            assertEquals(accepted, TilePreferences.label(activity));
            assertEquals(accepted, ((TextView) tagged(activity, "tile-label-value")).getText().toString());
        }
    }

    @Test public void blankTileNameStaysInDialogWithoutSaving() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            tagged(activity, "tile-label").performClick();
            drainMainLooper();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            EditText input = dialog.getWindow().getDecorView().findViewWithTag("tile-label-input");
            input.setText("   ");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            drainMainLooper();
            assertTrue(dialog.isShowing());
            assertNotNull(input.getError());
            assertEquals("5G·", TilePreferences.label(activity));
        }
    }

    @Test public void networkRowOpensSystemMobileNetworkSettings() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            tagged(activity, "network-settings").performClick();
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals(Settings.ACTION_DATA_ROAMING_SETTINGS, launched.getAction());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void aboutOnlyOffersTheRepositoryLinkAndNoAuthorFooter() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            assertFalse(allText(activity.getWindow().getDecorView()).contains("XiaoLeXLDW"));
            tagged(activity, "about").performClick();
            drainMainLooper();
            AlertDialog about = ShadowAlertDialog.getLatestAlertDialog();
            View github = about.getWindow().getDecorView().findViewWithTag("github-link");
            assertNotNull(github);
            assertFalse(allText(about.getWindow().getDecorView()).contains("XiaoLeXLDW"));
            github.performClick();
            drainMainLooper();
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals(Intent.ACTION_VIEW, launched.getAction());
            assertEquals("https://github.com/XiaoLeXLDW/5g-switch-os4-shizuku", launched.getDataString());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void cancellingHideInstructionsLeavesLauncherVisibleAndDoesNotWrite5G() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            Switch hide = tagged(activity, "hide-launcher-switch");
            assertFalse(hide.isChecked());
            hide.performClick();
            drainMainLooper();
            AlertDialog instructions = ShadowAlertDialog.getLatestAlertDialog();
            String message = Shadows.shadowOf(instructions).getMessage().toString();
            assertTrue(message.contains("先将 5G 按钮添加到控制中心"));
            assertTrue(message.contains("按住控制中心的 5G 按钮"));
            assertFalse(hide.isChecked());
            assertFalse(LauncherVisibility.isHidden(activity));
            instructions.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            drainMainLooper();
            assertFalse(hide.isChecked());
            assertFalse(LauncherVisibility.isHidden(activity));
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void hideAndRestoreFollowPackageManagerStateAcrossActivityRecreation() throws IOException {
        fake.value = state(1, false, FiveGController.Access.READY);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            TestActivity activity = lifecycle.get();
            ((Switch) tagged(activity, "hide-launcher-switch")).performClick();
            drainMainLooper();
            AlertDialog instructions = ShadowAlertDialog.getLatestAlertDialog();
            instructions.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            drainMainLooper();
            assertTrue(LauncherVisibility.isHidden(activity));
            assertTrue(((Switch) tagged(activity, "hide-launcher-switch")).isChecked());
            assertEquals(0, fake.writeCount);
            capture(activity, "native-light-hidden-launcher.png");

            lifecycle.recreate();
            drainMainLooper();
            activity = lifecycle.get();
            Switch hide = tagged(activity, "hide-launcher-switch");
            assertTrue(hide.isChecked());
            hide.performClick();
            drainMainLooper();
            assertFalse(LauncherVisibility.isHidden(activity));
            assertFalse(hide.isChecked());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void lifecycleRefreshesAndUnsubscribesWithoutWriting() {
        ActivityController<TestActivity> lifecycle = launch();
        assertEquals(1, fake.listeners.size());
        assertEquals(1, fake.refreshCount);
        lifecycle.pause().stop();
        drainMainLooper();
        assertTrue(fake.listeners.isEmpty());
        // performRestart(true) resets Activity's stopped flag and invokes onStart on API 35.
        lifecycle.restart().resume();
        drainMainLooper();
        assertEquals(1, fake.listeners.size());
        assertEquals(2, fake.refreshCount);
        assertEquals(0, fake.writeCount);
        lifecycle.pause().stop().destroy();
        drainMainLooper();
        assertTrue(fake.listeners.isEmpty());
    }

    @Test public void capturesNativeLightDarkAndAuthorizationScreens() throws IOException {
        fake.value = state(1, false, FiveGController.Access.READY);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            capture(lifecycle.get(), "native-light-enabled.png");
        }
        RuntimeEnvironment.setQualifiers("w393dp-h852dp-night-xhdpi");
        fake.value = state(0, false, FiveGController.Access.READY);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            capture(lifecycle.get(), "native-dark-disabled.png");
        }
        RuntimeEnvironment.setQualifiers("w393dp-h852dp-notnight-xhdpi");
        fake.value = state(-1, false, FiveGController.Access.PERMISSION_REQUIRED);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            capture(lifecycle.get(), "native-light-authorization.png");
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    public void narrowLargeTextScreenKeepsActionsScrollable() throws IOException {
        Configuration configuration = new Configuration(RuntimeEnvironment.getApplication()
                .getResources().getConfiguration());
        configuration.fontScale = 1.5f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(configuration,
                RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        fake.value = state(1, false, FiveGController.Access.READY);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            capture(activity, "native-narrow-large-text.png");
            View about = tagged(activity, "about");
            ScrollView scroll = tagged(activity, "main-scroll");
            assertTrue(scroll.canScrollVertically(1));
            assertTrue(scroll.fullScroll(View.FOCUS_DOWN));
            assertTrue(about.getGlobalVisibleRect(new Rect()));
            assertTrue(about.getMeasuredHeight() > 0);
            about.performClick();
            drainMainLooper();
            assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
        }
    }

    private ActivityController<TestActivity> launch() {
        return Robolectric.buildActivity(TestActivity.class).setup();
    }

    private static void drainMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static String allText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) text.append(allText(group.getChildAt(i)));
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> T tagged(MainActivity activity, String tag) {
        return (T) activity.findViewById(android.R.id.content).findViewWithTag(tag);
    }

    private static FiveGController.State state(int enabled, boolean busy, FiveGController.Access access) {
        return new FiveGController.State(enabled, busy, "", "", access);
    }

    /** Draws the actual native View hierarchy, including native glyph rendering. */
    private static void capture(MainActivity activity, String filename) throws IOException {
        String output = System.getProperty("fiveg.screenshot.dir");
        if (output == null || output.isBlank()) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(activity.getResources().getConfiguration().screenWidthDp * density);
        int height = Math.round(activity.getResources().getConfiguration().screenHeightDp * density);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File directory = new File(output);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create screenshot directory: " + directory);
        }
        try (FileOutputStream stream = new FileOutputStream(new File(directory, filename))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }
    }

    public static final class TestActivity extends MainActivity {
        static FakeController nextController;
        @Override protected UiController createUiController() { return nextController; }
    }

    private static final class FakeController implements MainActivity.UiController {
        FiveGController.State value;
        final List<FiveGController.Listener> listeners = new ArrayList<>();
        int writeCount;
        int permissionRequests;
        int refreshCount;
        boolean lastRequestedEnabled;

        FakeController(FiveGController.State state) { value = state; }
        @Override public FiveGController.State state() { return value; }
        @Override public void addListener(FiveGController.Listener listener) {
            listeners.add(listener);
            listener.onStateChanged(value);
        }
        @Override public void removeListener(FiveGController.Listener listener) { listeners.remove(listener); }
        @Override public void refresh() { refreshCount++; }
        @Override public void setEnabled(boolean enabled) {
            writeCount++;
            lastRequestedEnabled = enabled;
        }
        @Override public void requestPermission() { permissionRequests++; }
        void emit(FiveGController.State next) {
            value = next;
            for (FiveGController.Listener listener : new ArrayList<>(listeners)) listener.onStateChanged(next);
        }
    }
}
