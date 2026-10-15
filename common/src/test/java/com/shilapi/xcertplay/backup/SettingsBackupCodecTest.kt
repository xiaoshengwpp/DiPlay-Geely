package com.shilapi.xcertplay.backup

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayBlackCornerOverlay
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsBackupCodecTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String = "xcertplay_airplay") = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun document(values: String) = """{"format":"diplay-settings","version":1,"settings":{"xcertplay_airplay":$values}}""".toByteArray()

    @Before fun clearPreferences() {
        for (name in listOf("xcertplay_airplay", "diplay", "carplay_picture", "diplay_car_hotspot",
            "diplay_wheel_map_zoom", "diplay_geely_input", "diplay_foreground_wheel_keys")) {
            prefs(name).edit().clear().commit()
        }
    }

    @Test fun exportOmitsSensitiveAndNavigationKeys() {
        prefs().edit().putInt("display_fps", 60).putString("manual_hotspot_passphrase", "test-placeholder")
            .putString("pairing.test", "placeholder").putBoolean("cluster_map_enabled", true)
            .putBoolean("location_reporting_enabled", true).putBoolean("auto_start_on_boot", true)
            .putBoolean("launcher_map_sharing", true).putBoolean("cluster_future_consent", true)
            .putString("carplay_night_future_secret", "placeholder").putString("identity_private", "placeholder")
            .putString("manual_hotspot_ssid", "placeholder").putString("mfi_target", "REMOTE")
            .putStringSet("pairing_ids", setOf("placeholder")).apply()
        prefs("diplay").edit().putString("app_language", "zh").putBoolean("auto_connect", true).apply()
        prefs("diplay_wheel_map_zoom").edit().putBoolean("media_play_pause", true).apply()
        prefs("diplay_foreground_wheel_keys").edit().putBoolean("siri_double_press", true)
            .putBoolean("media_play_pause", true).apply()
        prefs("diplay_car_hotspot").edit().putBoolean("auto_enable", true).apply()
        val files = JSONObject(String(SettingsBackupCodec.export(context, "0.2.18-geely.1"))).getJSONObject("settings")
        val airplay = files.getJSONObject("xcertplay_airplay")
        assertEquals(1, airplay.length())
        assertEquals(60, airplay.getInt("display_fps"))
        assertFalse(files.has("diplay_car_hotspot"))
        assertFalse(files.has("diplay"))
        assertFalse(files.has("diplay_wheel_map_zoom"))
        assertFalse(files.has("diplay_foreground_wheel_keys"))
    }

    @Test fun importAppliesKnownKeysAndSkipsUnknownOrForbidden() {
        val result = SettingsBackupCodec.import(context, document("""{"display_fps":50,"pairing.attacker":"x","brand_new_key":7,"location_reporting_enabled":true}"""))
        assertFalse(result.invalid)
        assertEquals(1, result.applied)
        assertEquals(3, result.skipped)
        assertEquals(50, prefs().getInt("display_fps", 0))
        assertFalse(prefs().contains("pairing.attacker"))
        assertFalse(prefs().contains("location_reporting_enabled"))
    }

    @Test fun importMatchesTypesAgainstFixedSchemaEvenOnAFreshInstall() {
        for (value in listOf("\"0\"", "true", "0.5", "21", "-1", "3000000000", "null")) {
            prefs().edit().clear().commit()
            val result = SettingsBackupCodec.import(context, document("""{"navigation_audio_channel":$value}"""))
            assertEquals(0, result.applied)
            assertEquals(1, result.skipped)
            assertNull(AirPlayPersistence.loadNavigationAudioChannel(context))
        }
        for (channel in 0..20) {
            prefs().edit().clear().commit()
            assertEquals(1, SettingsBackupCodec.import(context, document("""{"navigation_audio_channel":$channel}""")).applied)
            assertEquals(channel, AirPlayPersistence.loadNavigationAudioChannel(context))
        }
    }

    @Test fun importRejectsMissingOrUnsupportedVersions() {
        for (input in listOf("not json", "{}", """{"format":"diplay-settings","settings":{}}""",
            """{"format":"diplay-settings","version":99,"settings":{}}""")) {
            assertTrue(SettingsBackupCodec.import(context, input.toByteArray()).invalid)
        }
    }

    @Test fun importRejectsOutOfRangeOrFractionalNumbers() {
        val result = SettingsBackupCodec.import(context, document("""{"ui_scale_percent":1e300,"display_fps":30.5,"display_scale_tenths":85,"navigation_audio_channel":[]} """))
        assertEquals(0, result.applied)
        assertEquals(4, result.skipped)
        assertTrue(prefs().all.isEmpty())
    }

    @Test fun exportImportRoundTripRestoresValuesThroughActualGetters() {
        AirPlayPersistence.saveUiScalePercent(context, 85)
        AirPlayPersistence.saveNavigationStreamType(context, 14)
        AirPlayPersistence.saveNavigationAudioChannel(context, 0)
        AirPlayPersistence.saveHevcEnabled(context, true)
        val saved = SettingsBackupCodec.export(context, "0.2.18-geely.1")
        prefs().edit().clear().commit()
        assertEquals(4, SettingsBackupCodec.import(context, saved).applied)
        assertEquals(85, AirPlayPersistence.loadUiScalePercent(context))
        assertEquals(14, AirPlayPersistence.loadNavigationStreamType(context))
        assertEquals(0, AirPlayPersistence.loadNavigationAudioChannel(context))
        assertTrue(AirPlayPersistence.loadHevcEnabled(context))
    }

    @Test fun importCannotTransferConsentOrChangeSavedLanguage() {
        for (choice in listOf(false, true)) {
            prefs().edit().clear().putBoolean("location_reporting_enabled", choice)
                .putBoolean("wireless_enabled", choice).putBoolean("auto_start_on_boot", choice).commit()
            prefs("diplay").edit().putString("app_language", "en").putBoolean("auto_connect", choice).commit()
            val data = """{"format":"diplay-settings","version":1,"settings":{
                "xcertplay_airplay":{"location_reporting_enabled":${!choice},"wireless_enabled":${!choice},"auto_start_on_boot":${!choice},"cluster_map_enabled":true},
                "diplay":{"app_language":"zh","auto_connect":${!choice}},"diplay_car_hotspot":{"auto_enable":true}}}"""
            assertEquals(0, SettingsBackupCodec.import(context, data.toByteArray()).applied)
            for (key in listOf("location_reporting_enabled", "wireless_enabled", "auto_start_on_boot")) assertEquals(choice, prefs().getBoolean(key, !choice))
            assertEquals(choice, prefs("diplay").getBoolean("auto_connect", !choice))
            assertEquals("en", prefs("diplay").getString("app_language", null))
            assertFalse(prefs("diplay_car_hotspot").contains("auto_enable"))
        }
    }

    @Test fun pictureSchemaRejectsArbitraryKeysAndOutOfRangeValues() {
        val data = """{"format":"diplay-settings","version":1,"settings":{"carplay_picture":{"brightness":50,"contrast":200,"saturation":-1,"warmth":101,"unknown":"x"}}}"""
        val result = SettingsBackupCodec.import(context, data.toByteArray())
        assertEquals(2, result.applied)
        assertEquals(3, result.skipped)
        assertEquals(50, prefs("carplay_picture").getInt("brightness", 0))
    }

    @Test fun displayOptionsRoundTripThroughTypedGettersForEveryReviewedChoice() {
        for (radius in CarPlayBlackCornerOverlay.RADII_DP) for (temporaryBars in listOf(false, true)) {
            prefs().edit().clear().commit()
            AirPlayPersistence.saveBlackCornerRadiusDp(context, radius)
            AirPlayPersistence.saveTemporarySystemBars(context, temporaryBars)
            val saved = SettingsBackupCodec.export(context, "test-geely")
            val exported = JSONObject(String(saved)).getJSONObject("settings").getJSONObject("xcertplay_airplay")
            assertEquals(2, exported.length())
            assertEquals(radius, exported.getInt("black_corner_radius_dp"))
            assertEquals(temporaryBars, exported.getBoolean("temporary_system_bars"))

            prefs().edit().clear().commit()
            val result = SettingsBackupCodec.import(context, saved)
            assertFalse(result.invalid)
            assertEquals(2, result.applied)
            assertEquals(0, result.skipped)
            assertEquals(radius, AirPlayPersistence.loadBlackCornerRadiusDp(context))
            assertEquals(temporaryBars, AirPlayPersistence.loadTemporarySystemBars(context))
        }
    }

    @Test fun displayOptionImportsRejectWrongTypesAndUnsupportedRadiiWithoutOverwritingChoices() {
        AirPlayPersistence.saveBlackCornerRadiusDp(context, 24)
        AirPlayPersistence.saveTemporarySystemBars(context, true)
        for (value in listOf("\"24\"", "true", "false", "null", "[]", "{}", "24.5", "-2", "1", "49", "1e300")) {
            val result = SettingsBackupCodec.import(context, document("""{"black_corner_radius_dp":$value}"""))
            assertEquals("Rejected radius $value", SettingsImportOutcome(0, 1, false), result)
            assertEquals(24, AirPlayPersistence.loadBlackCornerRadiusDp(context))
        }
        for (value in listOf("\"true\"", "\"false\"", "0", "1", "null", "[]", "{}")) {
            val result = SettingsBackupCodec.import(context, document("""{"temporary_system_bars":$value}"""))
            assertEquals("Rejected flag $value", SettingsImportOutcome(0, 1, false), result)
            assertTrue(AirPlayPersistence.loadTemporarySystemBars(context))
        }
    }

    @Test fun invalidStoredDisplayValuesAreNotExported() {
        prefs().edit().putString("temporary_system_bars", "true").putInt("black_corner_radius_dp", 1).commit()
        val exported = JSONObject(String(SettingsBackupCodec.export(context, "test-geely"))).getJSONObject("settings")
        assertFalse(exported.has("xcertplay_airplay"))
    }

    @Test fun missingDisplayOptionsStayUnsetOnRestore() {
        assertEquals(SettingsImportOutcome(1, 0, false),
            SettingsBackupCodec.import(context, document("""{"display_fps":60}""")))
        assertFalse(prefs().contains("temporary_system_bars"))
        assertFalse(prefs().contains("black_corner_radius_dp"))
        assertEquals(0, AirPlayPersistence.loadBlackCornerRadiusDp(context))
        assertFalse(AirPlayPersistence.loadTemporarySystemBars(context))
    }

    @Test fun prefixesAndEntirePreferenceFilesCannotTransferNewConsentsOrWheelRemaps() {
        val saved = """{"format":"diplay-settings","version":1,"settings":{
            "xcertplay_airplay":{"cluster_future_consent":true,"carplay_night_future_secret":"x",
                "launcher_map_sharing":true,"center_map_overlay":true,"adb_cluster_activity_enabled":true},
            "diplay_wheel_map_zoom":{"siri_key":true,"siri_double_press":true,"media_play_pause":true},
            "diplay_geely_input":{"manual_enabled":true},"diplay_car_hotspot":{"auto_enable":true},
            "diplay_foreground_wheel_keys":{"siri_double_press":true,"media_play_pause":true}}}"""
        val result = SettingsBackupCodec.import(context, saved.toByteArray())
        assertEquals(SettingsImportOutcome(0, 12, false), result)
        for (name in listOf("xcertplay_airplay", "diplay_wheel_map_zoom", "diplay_geely_input", "diplay_foreground_wheel_keys", "diplay_car_hotspot")) {
            assertTrue("No imported opt-in in $name", prefs(name).all.isEmpty())
        }
    }

}
