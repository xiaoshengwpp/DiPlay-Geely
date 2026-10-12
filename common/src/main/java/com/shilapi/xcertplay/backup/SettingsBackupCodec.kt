package com.shilapi.xcertplay.backup

import android.content.Context
import com.shilapi.xcertplay.CarPlayPicture
import com.shilapi.xcertplay.airplay.CarPlayDisplayScale
import com.shilapi.xcertplay.airplay.CarPlayUiScale
import org.json.JSONObject

data class SettingsImportOutcome(val applied: Int, val skipped: Int, val invalid: Boolean)

/** Only reviewed display/audio values travel. Never infer a preference's type from an input file. */
object SettingsBackupCodec {
    const val FORMAT = "diplay-settings"
    const val VERSION = 1

    private sealed interface ValueType {
        fun normalize(value: Any?): Any?
        data object Flag : ValueType {
            override fun normalize(value: Any?): Any? = value as? Boolean
        }
        data class Integer(val values: Set<Int>) : ValueType {
            override fun normalize(value: Any?): Any? {
                val number = (value as? Number)?.toDouble() ?: return null
                if (!number.isFinite() || number % 1.0 != 0.0 || number < Int.MIN_VALUE || number > Int.MAX_VALUE) return null
                return number.toInt().takeIf { it in values }
            }
        }
    }
    private fun integers(range: IntRange) = ValueType.Integer(range.toSet())
    // No wildcard prefixes, whole preference files, consent, identity, network secrets or language.
    // Additional settings require an explicit reviewed schema before they can be backed up.
    private val FILES: Map<String, Map<String, ValueType>> = mapOf(
        "xcertplay_airplay" to mapOf(
            "display_scale_tenths" to integers(CarPlayDisplayScale.MIN_TENTHS..CarPlayDisplayScale.MAX_TENTHS),
            "ui_scale_percent" to ValueType.Integer(CarPlayUiScale.presets.toSet()),
            "display_fps" to ValueType.Integer((30..60 step 5).toSet()),
            "hevc_enabled" to ValueType.Flag,
            "advanced_audio_channel_mapping" to ValueType.Flag,
            "media_audio_channel" to integers(0..20),
            "navigation_audio_channel" to integers(0..20),
            "navigation_stream_type" to integers(0..20),
            "call_echo_cancellation" to ValueType.Flag,
            "call_voice_filter" to ValueType.Flag,
        ),
        "carplay_picture" to CarPlayPicture.keys.associateWith { integers(CarPlayPicture.range(it)) },
    )

    fun export(context: Context, appVersion: String): ByteArray {
        val files = JSONObject()
        for ((name, schema) in FILES) {
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val file = JSONObject()
            for ((key, type) in schema) {
                if (!values.containsKey(key)) continue // Unset navigation override remains unset.
                type.normalize(values[key])?.let { file.put(key, it) }
            }
            if (file.length() > 0) files.put(name, file)
        }
        return JSONObject().put("format", FORMAT).put("version", VERSION).put("app", appVersion)
            .put("exportedAt", System.currentTimeMillis()).put("settings", files)
            .toString(2).toByteArray(Charsets.UTF_8)
    }

    fun import(context: Context, document: ByteArray): SettingsImportOutcome {
        val root = runCatching { JSONObject(document.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return SettingsImportOutcome(0, 0, true)
        if (root.optString("format") != FORMAT || root.optInt("version", Int.MIN_VALUE) != VERSION) {
            return SettingsImportOutcome(0, 0, true)
        }
        val files = root.optJSONObject("settings") ?: return SettingsImportOutcome(0, 0, true)
        var applied = 0
        var skipped = 0
        for (name in files.keys()) {
            val schema = FILES[name]
            val file = files.optJSONObject(name)
            if (schema == null || file == null) {
                skipped += file?.length() ?: 1
                continue
            }
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            for (key in file.keys()) {
                when (val normalized = schema[key]?.normalize(file.opt(key))) {
                    is Boolean -> { editor.putBoolean(key, normalized); applied++ }
                    is Int -> { editor.putInt(key, normalized); applied++ }
                    else -> skipped++
                }
            }
            editor.apply()
        }
        return SettingsImportOutcome(applied, skipped, false)
    }
}
