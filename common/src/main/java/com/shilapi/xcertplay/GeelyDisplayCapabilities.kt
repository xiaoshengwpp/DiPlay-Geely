package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Read-only Android enumeration. Does not probe hidden services or change vehicle/display state. */
internal object GeelyDisplayCapabilities {
    fun read(context: Context): GeelyDisplayInventory {
        val manager = context.getSystemService(DisplayManager::class.java)
            ?: return GeelyDisplayInventory(emptyList(), listOf("display_manager_unavailable"))
        val displays = runCatching { manager.displays }.getOrElse {
            return GeelyDisplayInventory(emptyList(), listOf("display_enumeration_failed:${it.javaClass.simpleName}"))
        }
        val issues = mutableListOf<String>()
        val presentationIds = runCatching {
            manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).map { it.displayId }.toSet()
        }.getOrElse {
            issues += "presentation_enumeration_failed:${it.javaClass.simpleName}"
            emptySet()
        }
        val observed = displays.mapNotNull { display ->
            runCatching {
                @Suppress("DEPRECATION")
                val metrics = DisplayMetrics().also(display::getRealMetrics)
                GeelyDisplayInfo(
                    displayId = display.displayId,
                    name = display.name,
                    width = metrics.widthPixels,
                    height = metrics.heightPixels,
                    densityDpi = metrics.densityDpi,
                    rotation = display.rotation,
                    state = when (display.state) {
                        Display.STATE_ON -> "ON"
                        Display.STATE_OFF -> "OFF"
                        Display.STATE_DOZE -> "DOZE"
                        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
                        else -> "UNKNOWN"
                    },
                    flags = display.flags,
                    isDefault = display.displayId == Display.DEFAULT_DISPLAY,
                    valid = display.isValid,
                    publicPresentation = display.displayId in presentationIds &&
                        display.flags and Display.FLAG_PRESENTATION != 0 &&
                        display.flags and Display.FLAG_PRIVATE == 0,
                )
            }.getOrElse {
                issues += "display_snapshot_failed:${it.javaClass.simpleName}"
                null
            }
        }.sortedBy { it.displayId }
        return GeelyDisplayInventory(observed, issues)
    }

    fun report(context: Context): String {
        val inventory = read(context)
        return buildString {
            appendLine("source=Android_DisplayManager readOnly=true screenRoles=unconfirmed")
            appendLine("displayCount=${inventory.displays.size} manuallyEligible=${inventory.displays.count { it.eligibleForManualHud }}")
            inventory.displays.forEach { display ->
                val safeName = display.name.replace(Regex("[\\r\\n\\t]"), " ").take(80)
                val line = "id=${display.displayId} name=$safeName size=${display.width}x${display.height} " +
                    "densityDpi=${display.densityDpi} rotation=${display.rotation} state=${display.state} " +
                    "flags=${display.flags} role=${display.role} manualPresentation=${display.eligibleForManualHud} " +
                    "reason=${display.unavailableReason ?: "explicit_selection_required"}"
                appendLine(DiagnosticRedactor.redact(line) ?: "display_metadata_redacted")
            }
            inventory.issues.forEach { appendLine("issue=$it") }
            appendLine(GeelyWindowDiagnostics.report())
            appendLine("navigationCue=${GeelyHudSelection.status} selectedDisplay=${GeelyHudSelection.selectedDisplayId() ?: "none"}")
            appendLine("OEM HUD/cluster output unavailable: no verified Geely IPC/service contract, permission model, or screen-role mapping.")
            appendLine("Passenger display unavailable unless Android exposes an eligible public presentation display; physical visibility is unverified.")
            append("Presentation navigation cues do not establish OEM HUD support, a cluster map stream, or multi-screen CarPlay.")
        }
    }
}

/** Last foreground window only. Never substitutes a total physical screen width for touch/video. */
internal class GeelyWindowDiagnostics(private val activity: Activity) {
    private val decor get() = activity.window.decorView
    private val listener = ViewTreeObserver.OnGlobalLayoutListener { capture() }
    private var attached = false

    fun start() {
        if (attached) return
        attached = true
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        capture()
    }

    fun stop() {
        if (!attached) return
        attached = false
        if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        last = last.substringBefore(" foreground=") + " foreground=false"
    }

    private fun capture() {
        val insets = ViewCompat.getRootWindowInsets(decor)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val geometry = GeelyWindowGeometry(decor.width, decor.height,
            insets?.left ?: 0, insets?.top ?: 0, insets?.right ?: 0, insets?.bottom ?: 0)
        last = "hostWindow=${geometry.width}x${geometry.height} " +
            "insets=${geometry.insetLeft},${geometry.insetTop},${geometry.insetRight},${geometry.insetBottom} " +
            "insetsKnown=${insets != null} safeArea=${geometry.contentWidth}x${geometry.contentHeight} " +
            "densityDpi=${activity.resources.displayMetrics.densityDpi} " +
            "multiWindow=${activity.isInMultiWindowMode} foreground=true"
    }

    companion object {
        @Volatile private var last = "hostWindow=not_observed"
        fun report(): String = last
    }
}
