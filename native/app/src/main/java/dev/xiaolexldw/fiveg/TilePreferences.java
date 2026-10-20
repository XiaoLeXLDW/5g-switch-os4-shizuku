/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import android.content.Context;
import android.content.SharedPreferences;

public final class TilePreferences {
    private static final String DEFAULT_LABEL = "5G·";
    private static final String LABEL_KEY = "edit_label";

    private TilePreferences() { }

    public static String label(Context context) {
        SharedPreferences preferences = preferences(context);
        String stored;
        try { stored = preferences.getString(LABEL_KEY, null); }
        catch (ClassCastException invalidOldValue) { stored = null; }
        String label = normalizedLabel(stored);
        if (!label.equals(stored)) preferences.edit().putString(LABEL_KEY, label).apply();
        return label;
    }

    public static void setLabel(Context context, String label) {
        preferences(context).edit().putString(LABEL_KEY, normalizedLabel(label)).apply();
        FiveGTileService.requestUpdate(context);
    }

    static String normalizedLabel(String value) {
        return value == null || isBlank(value) || value.equals("5G·A17") ? DEFAULT_LABEL : value;
    }

    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)) return false;
        }
        return true;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }
}
