/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TileIconTest {
    @Test public void renderedGlyphIsCenteredUnclippedAndMonochromeForSystemTinting() {
        Bitmap bitmap = TileIcon.bitmap();
        int left = bitmap.getWidth(), top = bitmap.getHeight(), right = -1, bottom = -1;
        int opaquePixels = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) == 0) continue;
                assertEquals(255, Color.red(pixel));
                assertEquals(255, Color.green(pixel));
                assertEquals(255, Color.blue(pixel));
                if (Color.alpha(pixel) == 255) opaquePixels++;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        assertTrue("Readable solid lettering", opaquePixels > 200);
        assertTrue("Transparent safety margin", left >= 6 && top >= 12 && right <= 89 && bottom <= 83);
        assertTrue("Horizontally centered", Math.abs(left + right - 95) <= 3);
        assertTrue("Vertically centered", Math.abs(top + bottom - 95) <= 3);
        assertEquals(Bitmap.DENSITY_NONE, bitmap.getDensity());
    }
}
