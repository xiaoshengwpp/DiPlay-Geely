package com.shilapi.xcertplay

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppLocaleTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(LocaleManager::class.java)

    @Test @Config(qualifiers = "en-rUS")
    fun freshInstallDefaultsToSimplifiedChineseOnAndroid13() {
        assertEquals("en", context.resources.configuration.locales[0].language)
        assertTrue(manager.applicationLocales.isEmpty)
        assertEquals(AppLocale.SIMPLIFIED_CHINESE, AppLocale.preference(context))
        assertSame(context, AppLocale.wrap(context))
        assertEquals("zh-CN", manager.applicationLocales.toLanguageTags())
        assertTrue(context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
            .getBoolean("app_language_platform_migrated", false))
    }

    @Test @Config(sdk = [28, 32], qualifiers = "en-rUS")
    fun freshInstallWrapsSimplifiedChineseWithoutChangingGlobalResources() {
        assertEquals("en", context.resources.configuration.locales[0].language)
        val original = context.resources.configuration.locales.toLanguageTags()
        assertEquals(AppLocale.SIMPLIFIED_CHINESE, AppLocale.preference(context))
        val wrapped = AppLocale.wrap(context)
        assertEquals("zh-CN", wrapped.resources.configuration.locales[0].toLanguageTag())
        assertEquals(View.LAYOUT_DIRECTION_LTR, wrapped.resources.configuration.layoutDirection)
        assertEquals(original, context.resources.configuration.locales.toLanguageTags())
    }

    @Test @Config(sdk = [28, 32, 33])
    fun savedChoicesIncludingSystemSurviveRepeatedInitialization() {
        for (language in AppLocale.ALL) {
            AppLocale.save(context, language)
            repeat(2) {
                AppLocale.wrap(context)
                assertEquals(language, AppLocale.preference(context))
            }
        }
    }

    @Test fun explicitLegacySystemPreferenceIsPreserved() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putString("app_language", AppLocale.SYSTEM).commit()
        AppLocale.wrap(context)
        assertTrue(manager.applicationLocales.isEmpty)
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
    }

    @Test fun previouslyMigratedSystemPreferenceIsPreserved() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putBoolean("app_language_platform_migrated", true).commit()
        AppLocale.wrap(context)
        assertTrue(manager.applicationLocales.isEmpty)
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
    }

    @Test fun androidSettingsChoiceBeforeFirstLaunchIsPreserved() {
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLocale.wrap(context)
        assertEquals(AppLocale.ENGLISH, AppLocale.preference(context))
        assertEquals("en", manager.applicationLocales.toLanguageTags())
    }

    @Test fun returningToSystemAfterChineseDefaultIsNotOverridden() {
        AppLocale.wrap(context)
        assertEquals("zh-CN", manager.applicationLocales.toLanguageTags())
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        repeat(2) { AppLocale.wrap(context) }
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
        assertTrue(manager.applicationLocales.isEmpty)
    }

    @Test fun pickerAndSystemSettingsShareTheSamePreference() {
        AppLocale.save(context, AppLocale.ARABIC)
        assertEquals("ar", manager.applicationLocales.toLanguageTags())
        manager.applicationLocales = LocaleList.forLanguageTags("es")
        assertEquals(AppLocale.SPANISH, AppLocale.preference(context))
        assertSame(context, AppLocale.wrap(context))
        AppLocale.save(context, AppLocale.SYSTEM)
        assertTrue(manager.applicationLocales.isEmpty)
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
    }

    @Test fun oldPreferenceMigratesOnceAndCannotOverrideLaterSystemChanges() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putString("app_language", "ar").commit()
        AppLocale.wrap(context)
        assertEquals("ar", manager.applicationLocales.toLanguageTags())
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLocale.wrap(context)
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
    }

    @Test fun existingSystemChoiceWinsOverLegacyPreference() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putString("app_language", "ar").commit()
        manager.applicationLocales = LocaleList.forLanguageTags("zh-CN")
        AppLocale.wrap(context)
        assertEquals(AppLocale.SIMPLIFIED_CHINESE, AppLocale.preference(context))
        assertEquals("zh-CN", manager.applicationLocales.toLanguageTags())
    }

    @Test @Config(sdk = [28, 32])
    fun olderAndroidWrapsArabicAndReturnsToSystemWithoutChangingGlobalResources() {
        val original = context.resources.configuration.locales.toLanguageTags()
        AppLocale.save(context, AppLocale.ARABIC)
        val wrapped = AppLocale.wrap(context)
        assertEquals(Locale("ar"), wrapped.resources.configuration.locales[0])
        assertEquals(View.LAYOUT_DIRECTION_RTL, wrapped.resources.configuration.layoutDirection)
        assertEquals(original, context.resources.configuration.locales.toLanguageTags())
        AppLocale.save(context, AppLocale.SYSTEM)
        assertSame(context, AppLocale.wrap(context))
    }
}
