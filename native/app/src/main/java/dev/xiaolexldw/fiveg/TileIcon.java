/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;

/** Monochrome Quick Settings lettering using the device's current system font. */
final class TileIcon {
    private TileIcon() {}

    static Icon create() {
        return Icon.createWithBitmap(bitmap());
    }

    static Bitmap bitmap() {
        // Render at 4x the 24dp icon grid so System UI can scale and tint cleanly.
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 400, false));
        paint.setTextSize(84f);
        Rect ink = new Rect();
        paint.getTextBounds("5G", 0, 2, ink);
        float scale = Math.min(80f / Math.max(1, ink.width()), 64f / Math.max(1, ink.height()));
        paint.setTextSize(paint.getTextSize() * scale);
        paint.getTextBounds("5G", 0, 2, ink);
        float x = (size - ink.width()) / 2f - ink.left;
        float y = (size - ink.height()) / 2f - ink.top;
        new Canvas(bitmap).drawText("5G", x, y, paint);
        return bitmap;
    }
}
