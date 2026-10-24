/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

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
@Config(sdk = 35, application = Application.class, qualifiers = "en-rUS-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityLocalizationTest {
    private FakeController fake;

    @Before public void prepare() {
        fake = new FakeController(state(1, FiveGController.Access.READY));
        TestActivity.nextController = fake;
    }

    @Test public void englishStatusPermissionAndFailureMessagesAreAccessible() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            assertEquals("5G Switch·A17", activity.getString(R.string.app_name));
            assertEquals("On", text(activity, "state-title"));
            Switch toggle = tagged(activity, "fiveg-switch");
            assertEquals("5G Switch", toggle.getContentDescription().toString());
            assertEquals("On", toggle.getStateDescription().toString());
            fake.emit(state(0, FiveGController.Access.READY));
            assertEquals("Off", text(activity, "state-title"));
            assertEquals("Off", toggle.getStateDescription().toString());

            fake.emit(FiveGController.State.fromResources(activity, -1, false,
                    R.string.controller_permission_required, R.string.controller_allow_to_switch,
                    FiveGController.Access.PERMISSION_REQUIRED));
            assertEquals("Awaiting permission", text(activity, "state-title"));
            assertEquals("Permission required", text(activity, "access-title"));
            assertEquals("Grant permission", text(activity, "authorize"));
            assertEquals("Unknown status", toggle.getStateDescription().toString());
            tagged(activity, "authorize").performClick();
            assertEquals(1, fake.permissionRequests);

            fake.emit(FiveGController.State.fromResources(activity, -1, false,
                    R.string.controller_unconfirmed, R.string.controller_refresh_state,
                    FiveGController.Access.READY));
            assertEquals("Could not confirm setting", text(activity, "state-title"));
            assertEquals("Refresh to check the current 5G state", text(activity, "state-detail"));
            assertFalse(toggle.isEnabled());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void englishDialogsTranslateValidationRecoveryAndAccessibility() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            tagged(activity, "tile-label").performClick();
            drainMainLooper();
            AlertDialog name = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Quick Settings tile name", Shadows.shadowOf(name).getTitle().toString());
            assertEquals("Save", name.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            assertEquals("Cancel", name.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
            assertTrue(allText(name.getWindow().getDecorView()).contains("Up to 6 characters"));
            EditText input = name.getWindow().getDecorView().findViewWithTag("tile-label-input");
            input.setText("   ");
            name.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("Enter a name", input.getError().toString());
            assertTrue(name.isShowing());
            name.dismiss();

            Switch hide = tagged(activity, "hide-launcher-switch");
            assertEquals("Hide launcher icon", hide.getContentDescription().toString());
            hide.performClick();
            drainMainLooper();
            AlertDialog recovery = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Hide launcher icon", Shadows.shadowOf(recovery).getTitle().toString());
            String message = Shadows.shadowOf(recovery).getMessage().toString();
            assertTrue(message.contains("Add the 5G tile to Quick Settings first."));
            assertTrue(message.contains("touch and hold the 5G tile"));
            assertEquals("Hide icon", recovery.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            recovery.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertFalse(LauncherVisibility.isHidden(activity));

            tagged(activity, "about").performClick();
            drainMainLooper();
            AlertDialog about = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("About", Shadows.shadowOf(about).getTitle().toString());
            assertEquals("Done", about.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            View github = about.getWindow().getDecorView().findViewWithTag("github-link");
            assertNotNull(github);
            assertEquals("Open the GitHub project", github.getContentDescription().toString());
            assertEquals(0, fake.writeCount);
        }
    }

    @Test @Config(qualifiers = "fr-rFR-w393dp-h852dp-xhdpi")
    public void unsupportedSystemLanguageFallsBackToEnglish() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            assertEquals("5G Switch·A17", activity.getString(R.string.app_name));
            assertEquals("On", text(activity, "state-title"));
            assertEquals("Refresh", text(activity, "refresh"));
            assertTrue(allText(activity.getWindow().getDecorView()).contains("Mobile network settings"));
            assertEquals(0, fake.writeCount);
        }
    }

    @Test @Config(qualifiers = "zh-rTW-w393dp-h852dp-xhdpi")
    public void traditionalChineseUsesItsOwnTranslationInsteadOfEnglishFallback() throws IOException {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            assertEquals("5G開關·A17", activity.getString(R.string.app_name));
            assertEquals("已開啟", text(activity, "state-title"));
            assertEquals("重新整理", text(activity, "refresh"));
            assertEquals("隱藏啟動器圖示", tagged(activity, "hide-launcher-switch")
                    .getContentDescription().toString());
            capture(activity, "native-zh-Hant-light-enabled.png");
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void changingLanguagePreservesCustomTileNameAndNeverWrites5G() {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            TilePreferences.setLabel(activity, "我的 5G");
            lifecycle.recreate();
            drainMainLooper();
            assertEquals("我的 5G", text(lifecycle.get(), "tile-label-value"));
            RuntimeEnvironment.setQualifiers("zh-rCN-w393dp-h852dp-xhdpi");
            lifecycle.recreate();
            drainMainLooper();
            assertEquals("已开启", text(lifecycle.get(), "state-title"));
            assertEquals("我的 5G", text(lifecycle.get(), "tile-label-value"));
            RuntimeEnvironment.setQualifiers("en-rUS-w393dp-h852dp-xhdpi");
            lifecycle.recreate();
            drainMainLooper();
            assertEquals("On", text(lifecycle.get(), "state-title"));
            assertEquals("我的 5G", TilePreferences.label(lifecycle.get()));
            assertEquals(0, fake.writeCount);
        }
    }

    @Test public void rendersEnglishLightDarkAndPermissionScreens() throws IOException {
        try (ActivityController<TestActivity> lifecycle = launch()) {
            capture(lifecycle.get(), "native-en-light-enabled.png");
            assertVisibleTextFits(lifecycle.get().getWindow().getDecorView());
        }
        RuntimeEnvironment.setQualifiers("en-rUS-w393dp-h852dp-night-xhdpi");
        fake.value = state(0, FiveGController.Access.READY);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            capture(lifecycle.get(), "native-en-dark-disabled.png");
            assertVisibleTextFits(lifecycle.get().getWindow().getDecorView());
        }
        RuntimeEnvironment.setQualifiers("en-rUS-w393dp-h852dp-notnight-xhdpi");
        fake.value = state(-1, FiveGController.Access.PERMISSION_REQUIRED);
        try (ActivityController<TestActivity> lifecycle = launch()) {
            capture(lifecycle.get(), "native-en-light-authorization.png");
            assertVisibleTextFits(lifecycle.get().getWindow().getDecorView());
        }
        assertEquals(0, fake.writeCount);
    }

    @Test @Config(qualifiers = "en-rUS-w320dp-h640dp-xhdpi")
    public void englishLargeTextKeepsSettingsReachableWithoutClipping() throws IOException {
        Configuration configuration = new Configuration(RuntimeEnvironment.getApplication()
                .getResources().getConfiguration());
        configuration.fontScale = 1.5f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(configuration,
                RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        try (ActivityController<TestActivity> lifecycle = launch()) {
            MainActivity activity = lifecycle.get();
            capture(activity, "native-en-narrow-large-text.png");
            assertVisibleTextFits(activity.getWindow().getDecorView());
            ScrollView scroll = tagged(activity, "main-scroll");
            assertTrue(scroll.canScrollVertically(1));
            assertTrue(scroll.fullScroll(View.FOCUS_DOWN));
            capture(activity, "native-en-narrow-large-text-bottom.png");
            View about = tagged(activity, "about");
            assertTrue(about.getGlobalVisibleRect(new Rect()));
            about.performClick();
            drainMainLooper();
            assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
            assertEquals(0, fake.writeCount);
        }
    }

    private ActivityController<TestActivity> launch() {
        return Robolectric.buildActivity(TestActivity.class).setup();
    }

    private static void drainMainLooper() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }

    private static String text(MainActivity activity, String tag) {
        return ((TextView) tagged(activity, tag)).getText().toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> T tagged(MainActivity activity, String tag) {
        return (T) activity.findViewById(android.R.id.content).findViewWithTag(tag);
    }

    private static String allText(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result.append(allText(group.getChildAt(i)));
        }
        return result.toString();
    }

    private static void assertVisibleTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            if (label.getLayout() != null && label.length() != 0) {
                int available = label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight();
                for (int line = 0; line < label.getLayout().getLineCount(); line++) {
                    // LineWidth includes trailing wrap spaces; LineMax measures visible text.
                    assertTrue("Text overflows horizontally: " + label.getText(),
                            label.getLayout().getLineMax(line) <= available + 1);
                    assertEquals("Text is ellipsized: " + label.getText(),
                            0, label.getLayout().getEllipsisCount(line));
                }
                assertTrue("Text overflows vertically: " + label.getText(),
                        label.getLayout().getHeight() <= label.getHeight()
                                - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertVisibleTextFits(group.getChildAt(i));
        }
    }

    private static FiveGController.State state(int enabled, FiveGController.Access access) {
        return new FiveGController.State(enabled, false, "", "", access);
    }

    private static void capture(MainActivity activity, String filename) throws IOException {
        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(activity.getResources().getConfiguration().screenWidthDp * density);
        int height = Math.round(activity.getResources().getConfiguration().screenHeightDp * density);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        String output = System.getProperty("fiveg.screenshot.dir");
        if (output == null || output.isBlank()) return;
        File directory = new File(output);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create " + directory);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, filename))) {
            decor.draw(new Canvas(bitmap));
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
        FakeController(FiveGController.State initial) { value = initial; }
        @Override public FiveGController.State state() { return value; }
        @Override public void addListener(FiveGController.Listener listener) {
            listeners.add(listener);
            listener.onStateChanged(value);
        }
        @Override public void removeListener(FiveGController.Listener listener) { listeners.remove(listener); }
        @Override public void refresh() { }
        @Override public void setEnabled(boolean enabled) { writeCount++; }
        @Override public void requestPermission() { permissionRequests++; }
        void emit(FiveGController.State next) {
            value = next;
            for (FiveGController.Listener listener : new ArrayList<>(listeners)) listener.onStateChanged(next);
        }
    }
}
