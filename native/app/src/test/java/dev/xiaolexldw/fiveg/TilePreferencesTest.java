/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TilePreferencesTest {
    @Test public void migratesOnlyMissingBlankAndExactPreviousDefault() {
        assertEquals("5G·", TilePreferences.normalizedLabel(null));
        assertEquals("5G·", TilePreferences.normalizedLabel(""));
        assertEquals("5G·", TilePreferences.normalizedLabel(" \n\t"));
        assertEquals("5G·", TilePreferences.normalizedLabel("\u3000\u00a0"));
        assertEquals("5G·", TilePreferences.normalizedLabel("5G·A17"));
    }

    @Test public void preservesCustomLabelsExactly() {
        assertEquals("我的 5G", TilePreferences.normalizedLabel("我的 5G"));
        assertEquals(" 5G·A17", TilePreferences.normalizedLabel(" 5G·A17"));
        assertEquals("5G·A17 自定义", TilePreferences.normalizedLabel("5G·A17 自定义"));
        assertEquals("  NR 开关  ", TilePreferences.normalizedLabel("  NR 开关  "));
    }
}
