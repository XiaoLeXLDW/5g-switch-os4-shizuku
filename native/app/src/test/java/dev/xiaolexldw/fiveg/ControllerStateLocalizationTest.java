/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class ControllerStateLocalizationTest {
    @Test public void cachedStateCanFollowTheCurrentSurfaceLocaleWithoutChangingState() {
        Context english = context("en");
        Context chinese = context("zh-CN");
        FiveGController.State original = FiveGController.State.fromResources(english, 1, false,
                R.string.controller_enabled, R.string.controller_default_data_sim,
                FiveGController.Access.READY);

        FiveGController.State translated = original.localized(chinese);
        assertEquals("5G 已开启", translated.status);
        assertEquals("默认数据 SIM", translated.detail);
        assertEquals(1, translated.enabled);
        assertFalse(translated.busy);
        assertEquals(FiveGController.Access.READY, translated.access);
        assertEquals("5G is on", original.status);
        assertEquals("5G is on", translated.localized(english).status);
    }

    @Test public void unsupportedLanguagesFallBackToEnglishForCachedPermissionState() {
        FiveGController.State original = FiveGController.State.fromResources(context("zh-CN"),
                -1, false, R.string.controller_permission_required,
                R.string.controller_allow_to_switch, FiveGController.Access.PERMISSION_REQUIRED);
        FiveGController.State fallback = original.localized(context("de-DE"));

        assertEquals("Permission required", fallback.status);
        assertEquals("Allow Shizuku access to switch 5G", fallback.detail);
        assertEquals(-1, fallback.enabled);
        assertEquals(FiveGController.Access.PERMISSION_REQUIRED, fallback.access);
    }

    @Test public void callerProvidedMessagesArePreserved() {
        FiveGController.State custom = new FiveGController.State(-1, true, "Custom status",
                "Diagnostic detail", FiveGController.Access.READY);
        assertSame(custom, custom.localized(context("zh-CN")));
    }

    private static Context context(String languageTag) {
        Context application = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(application.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(Locale.forLanguageTag(languageTag)));
        return application.createConfigurationContext(configuration);
    }
}
