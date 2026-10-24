/* Copyright (c) 2026 XiaoLeXLDW. All rights reserved. */
package dev.xiaolexldw.fiveg;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Small native settings surface for the user's 5G preference. */
public class MainActivity extends Activity {
    private static final String TILE_COMPONENT = "com.ysy.switcherfiveg.SwitcherTileService";
    private static final String REPOSITORY_URL = "https://github.com/XiaoLeXLDW/5g-switch-os4-shizuku";
    private static final int MAX_LABEL_CODE_POINTS = 6;
    private static final Typeface SYSTEM_SANS = Typeface.create("sans-serif", Typeface.NORMAL);

    private UiController controller;
    private final FiveGController.Listener stateListener = this::render;
    private Palette colors;
    private TextView stateTitle;
    private TextView stateDetail;
    private TextView accessTitle;
    private TextView accessDetail;
    private TextView tileLabel;
    private Switch toggle;
    private Switch launcherSwitch;
    private Button accessButton;
    private Button refreshButton;
    private LinearLayout accessCard;
    private boolean updatingSwitch;
    private boolean updatingLauncherSwitch;
    private FiveGController.Access currentAccess = FiveGController.Access.SERVICE_STOPPED;

    /** Keeps Activity tests independent from Shizuku and device services. */
    protected interface UiController {
        FiveGController.State state();
        void addListener(FiveGController.Listener listener);
        void removeListener(FiveGController.Listener listener);
        void refresh();
        void setEnabled(boolean enabled);
        void requestPermission();
    }

    protected UiController createUiController() {
        FiveGController service = FiveGController.get(getApplicationContext());
        return new UiController() {
            @Override public FiveGController.State state() { return service.state(); }
            @Override public void addListener(FiveGController.Listener listener) {
                service.addListener(listener);
            }
            @Override public void removeListener(FiveGController.Listener listener) {
                service.removeListener(listener);
            }
            @Override public void refresh() { service.refresh(); }
            @Override public void setEnabled(boolean enabled) { service.setEnabled(enabled); }
            @Override public void requestPermission() { service.requestPermission(); }
        };
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        colors = new Palette(dark);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        controller = createUiController();
        buildContent();
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            bars.setSystemBarsAppearance(dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        render(controller.state());
    }

    @Override protected void onStart() {
        super.onStart();
        controller.addListener(stateListener);
    }

    @Override protected void onResume() {
        super.onResume();
        tileLabel.setText(TilePreferences.label(this));
        refreshLauncherSwitch();
        controller.refresh();
    }

    @Override protected void onStop() {
        controller.removeListener(stateListener);
        super.onStop();
    }

    private void buildContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(colors.page);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setTag("main-scroll");
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout stage = new FrameLayout(this);
        scroll.addView(stage, new ScrollView.LayoutParams(-1, -1));
        WidthLimitedColumn column = new WidthLimitedColumn();
        column.setPadding(dp(22), dp(30), dp(22), dp(28));
        stage.addView(column, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageIcon(TileIcon.create());
        icon.setImageTintList(ColorStateList.valueOf(colors.accent));
        icon.setBackground(round(colors.accentSurface, 15));
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView heading = text(getString(R.string.ui_title), 26, colors.ink, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.setMarginStart(dp(13));
        headingParams.setMarginEnd(dp(8));
        header.addView(heading, headingParams);
        refreshButton = button(getString(R.string.ui_refresh), false);
        refreshButton.setTag("refresh");
        refreshButton.setOnClickListener(view -> controller.refresh());
        header.addView(refreshButton, new LinearLayout.LayoutParams(-2, -2));
        column.addView(header);

        LinearLayout hero = vertical();
        hero.setPadding(dp(26), dp(24), dp(26), dp(26));
        hero.setBackground(round(colors.hero, 30));
        hero.addView(text(getString(R.string.ui_current_state), 13, colors.heroMuted, false));
        LinearLayout main = horizontal();
        main.setGravity(Gravity.CENTER_VERTICAL);
        TextView fiveG = text(getString(R.string.five_g_label), 66, colors.heroInk, true);
        fiveG.setLetterSpacing(0f);
        main.addView(fiveG, new LinearLayout.LayoutParams(0, -2, 1));
        toggle = new Switch(this);
        toggle.setTag("fiveg-switch");
        toggle.setShowText(false);
        toggle.setContentDescription(getString(R.string.ui_title));
        toggle.setMinHeight(dp(56));
        toggle.setSwitchMinWidth(dp(57));
        toggle.setSplitTrack(false);
        toggle.setThumbTintList(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked}, new int[0]},
                new int[] {colors.switchDisabled, colors.accent, colors.switchOff}));
        toggle.setTrackTintList(new ColorStateList(new int[][] {
                new int[] {android.R.attr.state_checked}, new int[0]},
                new int[] {colors.switchTrackOn, colors.switchTrackOff}));
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (!updatingSwitch) {
                // Keep the visible switch on the confirmed value until the read-back completes.
                updatingSwitch = true;
                toggle.setChecked(controller.state().enabled == 1);
                updatingSwitch = false;
                controller.setEnabled(enabled);
            }
        });
        main.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        hero.addView(main, new LinearLayout.LayoutParams(-1, -2));
        stateTitle = text(getString(R.string.ui_reading), 26, colors.heroInk, true);
        stateTitle.setTag("state-title");
        stateTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        hero.addView(stateTitle, new LinearLayout.LayoutParams(-1, -2));
        stateDetail = text("", 14, colors.heroMuted, false);
        stateDetail.setTag("state-detail");
        stateDetail.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(10);
        hero.addView(stateDetail, detailParams);
        addWithTopMargin(column, hero, 28);

        accessCard = vertical();
        accessCard.setTag("access-card");
        accessCard.setPadding(dp(22), dp(20), dp(22), dp(18));
        accessCard.setBackground(round(colors.card, 24));
        accessTitle = text("", 17, colors.ink, true);
        accessTitle.setTag("access-title");
        accessCard.addView(accessTitle);
        accessDetail = text("", 14, colors.muted, false);
        accessDetail.setLineSpacing(dp(3), 1f);
        addWithTopMargin(accessCard, accessDetail, 7);
        accessButton = button(getString(R.string.ui_grant_permission), true);
        accessButton.setTag("authorize");
        accessButton.setOnClickListener(view -> {
            if (currentAccess == FiveGController.Access.PERMISSION_REQUIRED) {
                controller.requestPermission();
            } else if (currentAccess == FiveGController.Access.SERVICE_STOPPED) {
                openShizuku();
            }
        });
        LinearLayout.LayoutParams authorizeParams = new LinearLayout.LayoutParams(-1, -2);
        authorizeParams.topMargin = dp(16);
        accessCard.addView(accessButton, authorizeParams);
        addWithTopMargin(column, accessCard, 16);

        TextView section = text(getString(R.string.ui_settings), 13, colors.muted, true);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(30);
        sectionParams.bottomMargin = dp(11);
        sectionParams.setMarginStart(dp(3));
        column.addView(section, sectionParams);

        LinearLayout settings = vertical();
        settings.setBackground(round(colors.card, 24));
        tileLabel = text(TilePreferences.label(this), 14, colors.muted, false);
        tileLabel.setTag("tile-label-value");
        settings.addView(settingsRow(getString(R.string.ui_tile_name), tileLabel, "tile-label", this::showLabelDialog));
        divider(settings);
        settings.addView(settingsRow(getString(R.string.ui_mobile_network_settings), null, "network-settings", this::openNetworkSettings));
        divider(settings);
        settings.addView(launcherVisibilityRow());
        divider(settings);
        settings.addView(settingsRow(getString(R.string.ui_about), null, "about", this::showAbout));
        column.addView(settings, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        root.requestApplyInsets();
    }

    void render(FiveGController.State state) {
        if (state == null) return;
        state = state.localized(this);
        currentAccess = state.access;
        boolean known = state.enabled == 0 || state.enabled == 1;
        updatingSwitch = true;
        toggle.setChecked(state.enabled == 1);
        toggle.setEnabled(state.access == FiveGController.Access.READY && known && !state.busy);
        updatingSwitch = false;
        refreshButton.setEnabled(!state.busy);
        refreshButton.setAlpha(state.busy ? 0.45f : 1f);

        String title;
        if (known) title = getString(state.enabled == 1 ? R.string.ui_on : R.string.ui_off);
        else if (state.access == FiveGController.Access.PERMISSION_REQUIRED) title = getString(R.string.ui_waiting_permission);
        else if (state.access == FiveGController.Access.SERVICE_STOPPED) title = getString(R.string.ui_waiting_connection);
        else if (state.access == FiveGController.Access.UNSUPPORTED) title = getString(R.string.ui_unavailable);
        else title = state.busy ? getString(R.string.ui_reading) : firstNonBlank(state.status, getString(R.string.ui_unknown));
        stateTitle.setText(title);
        toggle.setStateDescription(known ? title : getString(R.string.ui_unknown));

        if (state.access == FiveGController.Access.READY) {
            String detail = state.busy ? getString(R.string.ui_updating) : firstNonBlank(state.detail, state.status);
            stateDetail.setText(detail);
            stateDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
            accessCard.setVisibility(View.GONE);
            return;
        }
        stateDetail.setVisibility(View.GONE);
        accessCard.setVisibility(View.VISIBLE);
        accessButton.setEnabled(!state.busy);
        accessButton.setVisibility(state.access == FiveGController.Access.UNSUPPORTED ? View.GONE : View.VISIBLE);
        if (state.access == FiveGController.Access.PERMISSION_REQUIRED) {
            accessTitle.setText(R.string.ui_permission_required);
            accessDetail.setText(firstNonBlank(state.detail, getString(R.string.ui_permission_detail)));
            accessButton.setText(R.string.ui_grant_permission);
        } else if (state.access == FiveGController.Access.SERVICE_STOPPED) {
            accessTitle.setText(R.string.ui_connect_shizuku);
            accessDetail.setText(firstNonBlank(state.detail, getString(R.string.ui_start_shizuku_detail)));
            accessButton.setText(R.string.ui_open_shizuku);
        } else {
            accessTitle.setText(firstNonBlank(state.status, getString(R.string.ui_unsupported_device)));
            accessDetail.setText(firstNonBlank(state.detail, getString(R.string.ui_read_failed)));
        }
    }

    private void showLabelDialog() {
        EditText input = new EditText(this);
        input.setTag("tile-label-input");
        input.setTextColor(colors.ink);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[] {new CodePointLengthFilter(MAX_LABEL_CODE_POINTS)});
        input.setText(TilePreferences.label(this));
        input.setSelection(input.length());
        LinearLayout box = vertical();
        box.setPadding(dp(24), dp(7), dp(24), dp(8));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(56)));
        TextView limit = text(getString(R.string.ui_label_limit, MAX_LABEL_CODE_POINTS), 13, colors.muted, false);
        addWithTopMargin(box, limit, 8);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.ui_tile_name)
                .setView(box)
                .setNegativeButton(R.string.ui_cancel, null)
                .setPositiveButton(R.string.ui_save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) {
                        input.setError(getString(R.string.ui_enter_name));
                        return;
                    }
                    TilePreferences.setLabel(this, label);
                    tileLabel.setText(TilePreferences.label(this));
                    try {
                        TileService.requestListeningState(this, new ComponentName(getPackageName(), TILE_COMPONENT));
                    } catch (IllegalArgumentException | SecurityException ignoredFailure) {
                        // The saved name is also read when the existing tile next starts listening.
                    }
                    dialog.dismiss();
                }));
        dialog.show();
        input.requestFocus();
        input.post(() -> {
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void openShizuku() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (intent == null) {
            Toast.makeText(this, R.string.ui_shizuku_not_installed, Toast.LENGTH_SHORT).show();
            return;
        }
        startSafely(intent);
    }

    private void openNetworkSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS));
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            startSafely(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.ui_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private View launcherVisibilityRow() {
        LinearLayout row = horizontal();
        row.setTag("hide-launcher-row");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), dp(17), dp(22), dp(17));
        LinearLayout labels = vertical();
        TextView title = text(getString(R.string.ui_hide_launcher), 16, colors.ink, true);
        labels.addView(title);
        TextView detail = text(getString(R.string.ui_hide_launcher_detail), 13, colors.muted, false);
        detail.setLineSpacing(dp(2), 1f);
        addWithTopMargin(labels, detail, 5);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        launcherSwitch = new Switch(this);
        launcherSwitch.setTag("hide-launcher-switch");
        launcherSwitch.setContentDescription(getString(R.string.ui_hide_launcher));
        launcherSwitch.setMinHeight(dp(48));
        launcherSwitch.setShowText(false);
        refreshLauncherSwitch();
        launcherSwitch.setOnCheckedChangeListener((button, hidden) -> {
            if (updatingLauncherSwitch) return;
            // The icon changes only after the recovery instructions are acknowledged.
            refreshLauncherSwitch();
            if (hidden) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_hide_launcher)
                        .setMessage(R.string.ui_hide_launcher_confirmation)
                        .setNegativeButton(R.string.ui_cancel, null)
                        .setPositiveButton(R.string.ui_hide_icon, (dialog, which) -> setLauncherHidden(true))
                        .show();
            } else {
                setLauncherHidden(false);
            }
        });
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-2, -2);
        switchParams.setMarginStart(dp(12));
        row.addView(launcherSwitch, switchParams);
        row.setOnClickListener(view -> launcherSwitch.performClick());
        return row;
    }

    private void refreshLauncherSwitch() {
        if (launcherSwitch == null) return;
        updatingLauncherSwitch = true;
        try {
            launcherSwitch.setChecked(LauncherVisibility.isHidden(this));
        } finally {
            updatingLauncherSwitch = false;
        }
    }

    private void setLauncherHidden(boolean hidden) {
        try {
            LauncherVisibility.setHidden(this, hidden);
            Toast.makeText(this, hidden ? R.string.ui_launcher_hidden
                    : R.string.ui_launcher_restored, Toast.LENGTH_LONG).show();
        } catch (RuntimeException unavailable) {
            Log.w("FiveGUI", "Could not change launcher visibility", unavailable);
            Toast.makeText(this, R.string.ui_launcher_change_failed, Toast.LENGTH_LONG).show();
        }
        refreshLauncherSwitch();
    }

    private void showAbout() {
        LinearLayout content = vertical();
        content.setPadding(dp(24), dp(8), dp(24), dp(8));
        Button github = button(getString(R.string.github_label), false);
        github.setTag("github-link");
        github.setContentDescription(getString(R.string.ui_open_github));
        github.setOnClickListener(view -> startSafely(new Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))));
        content.addView(github, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle(R.string.ui_about)
                .setView(content)
                .setPositiveButton(R.string.ui_done, null)
                .show();
    }

    private View settingsRow(String title, TextView value, String tag, Runnable action) {
        LinearLayout row = horizontal();
        row.setTag(tag);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(value == null ? 66 : 82));
        row.setPadding(dp(22), dp(17), dp(22), dp(17));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple), null,
                round(Color.WHITE, 20)));
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());
        LinearLayout labels = vertical();
        labels.addView(text(title, 16, colors.ink, true));
        if (value != null) addWithTopMargin(labels, value, 5);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 28, colors.muted, false);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(-2, -2);
        arrowParams.setMarginStart(dp(14));
        row.addView(arrow, arrowParams);
        return row;
    }

    private void divider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(colors.line);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMarginStart(dp(22));
        params.setMarginEnd(dp(22));
        parent.addView(divider, params);
    }

    private Button button(String title, boolean filled) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setTextColor(filled ? colors.onAccent : colors.accent);
        button.setTypeface(Typeface.create(SYSTEM_SANS, 500, false));
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(17), dp(11), dp(17), dp(11));
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple),
                round(filled ? colors.accent : Color.TRANSPARENT, 15), round(Color.WHITE, 15)));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create(SYSTEM_SANS, bold ? 500 : 400, false));
        return text;
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private void addWithTopMargin(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return fallback == null ? "" : fallback.trim();
    }

    private final class WidthLimitedColumn extends LinearLayout {
        WidthLimitedColumn() {
            super(MainActivity.this);
            setOrientation(VERTICAL);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int available = MeasureSpec.getSize(widthMeasureSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(available, dp(600)),
                    MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec);
        }
    }

    static final class CodePointLengthFilter implements InputFilter {
        private final int maximum;

        CodePointLengthFilter(int maximum) { this.maximum = maximum; }

        @Override public CharSequence filter(CharSequence source, int start, int end,
                Spanned destination, int replaceStart, int replaceEnd) {
            int retained = Character.codePointCount(destination, 0, replaceStart)
                    + Character.codePointCount(destination, replaceEnd, destination.length());
            int remaining = maximum - retained;
            if (remaining <= 0) return "";
            if (Character.codePointCount(source, start, end) <= remaining) return null;
            int keepEnd = Character.offsetByCodePoints(source, start, remaining);
            CharSequence kept = source.subSequence(start, keepEnd);
            if (!(source instanceof Spanned)) return kept;
            SpannableString result = new SpannableString(kept);
            TextUtils.copySpansFrom((Spanned) source, start, keepEnd, null, result, 0);
            return result;
        }
    }

    private static final class Palette {
        final int page, card, ink, muted, accent, onAccent, accentSurface;
        final int hero, heroInk, heroMuted, line, ripple;
        final int switchDisabled, switchOff, switchTrackOn, switchTrackOff;

        Palette(boolean dark) {
            page = Color.parseColor(dark ? "#111318" : "#F4F6FA");
            card = Color.parseColor(dark ? "#1C1F27" : "#FFFFFF");
            ink = Color.parseColor(dark ? "#F0F1F7" : "#1A1E2C");
            muted = Color.parseColor(dark ? "#A0A7B7" : "#727989");
            accent = Color.parseColor(dark ? "#B7C6FF" : "#3459D8");
            onAccent = Color.parseColor(dark ? "#17254E" : "#FFFFFF");
            accentSurface = Color.parseColor(dark ? "#253153" : "#E8EEFF");
            hero = Color.parseColor(dark ? "#202C49" : "#E8EEFF");
            heroInk = Color.parseColor(dark ? "#E4EAFF" : "#203566");
            heroMuted = Color.parseColor(dark ? "#ABBADD" : "#6477A1");
            line = Color.parseColor(dark ? "#2A2E38" : "#EEF0F4");
            ripple = Color.parseColor(dark ? "#24FFFFFF" : "#153459D8");
            switchDisabled = Color.parseColor(dark ? "#74809D" : "#9BA9CB");
            switchOff = Color.parseColor(dark ? "#B7C0D5" : "#FFFFFF");
            switchTrackOn = Color.parseColor(dark ? "#6579B6" : "#AFBFEE");
            switchTrackOff = Color.parseColor(dark ? "#53617F" : "#B8C4DF");
        }
    }
}
