package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.update.BackgroundUpdateScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.KeyPair

/** Exercise actual disabled entry points, not only the policy values. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
class Geely0218IntegrationTest {
    private val rejecting = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("unexpected application access")
        override fun getPackageName(): String = error("unexpected package access")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("unexpected preference access")
        override fun getSystemService(name: String): Any = error("unexpected system service access")
    }

    @Test fun wheelStartStopAndIdleNeverBindOrRetainAContext() {
        repeat(3) {
            GeelyWheelKeys.start(rejecting)
            GeelyWheelKeys.stop()
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertFalse(GeelyWheelKeys::class.java.declaredFields.any { it.type == Context::class.java })
    }

    @Test fun vpnFallbackCannotLoadKeysOrUseEvenAnInjectedAdbTransport() {
        assertEquals(LocalAdb.Access.UNSUPPORTED, VpnPreConsent.grant(rejecting))
        LocalAdb(KeyPair(null, null), "invalid.invalid", 1).use { adb ->
            assertEquals(LocalAdb.Access.UNSUPPORTED, VpnPreConsent.grant(rejecting, adb))
        }
    }

    @Test fun restoredServicesAndBackgroundUpdaterStopBeforeContextUse() {
        repeat(3) {
            WheelKeyService.restoreIfNeeded(rejecting)
            assertFalse(WheelKeyService.wanted(rejecting))
            assertFalse(UsbAutoConfirmService.openSettings(rejecting))
            BackgroundUpdateScheduler.schedule(rejecting)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun newInstallAndEverySavedLanguageSurviveTheIntegration() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
        for (language in AppLocale.ALL) {
            AppLocale.save(context, language)
            repeat(2) {
                AppLocale.wrap(context)
                assertEquals(language, AppLocale.preference(context))
            }
        }
    }

    @Test fun navigationUnsetExplicitZeroAndLegacyStreamRemainDistinct() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        for (stream in listOf(3, 14)) {
            AirPlayPersistence.saveNavigationStreamType(context, stream)
            for (channel in listOf(null, 0, 14)) {
                AirPlayPersistence.saveNavigationAudioChannel(context, channel)
                GeelyWheelKeys.start(context)
                GeelyWheelKeys.stop()
                assertEquals(channel, AirPlayPersistence.loadNavigationAudioChannel(context))
                assertEquals(stream, AirPlayPersistence.loadNavigationStreamType(context))
            }
        }
    }
}
