package com.shilapi.xcertplay

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R

/** In-memory consent for one observed public secondary display; intentionally not a saved setting. */
internal object GeelyHudSelection {
    private val policy = GeelyHudSelectionPolicy()
    @Volatile var status = "off"
        internal set
    @Volatile internal var onChanged: (() -> Unit)? = null

    @Synchronized fun selectedDisplayId(): Int? = policy.selectedDisplayId()

    fun select(context: Context, displayId: Int): Boolean {
        val displays = GeelyDisplayCapabilities.read(context).displays
        val accepted = synchronized(this) {
            policy.select(displays, displayId).also { selected ->
                if (selected) policy.observeConnection(CarPlayGlance.snapshot().connected)
            }
        }
        if (accepted) {
            status = "selected_waiting_for_foreground_host"
            onChanged?.invoke()
        }
        return accepted
    }

    fun clear() {
        synchronized(this) { policy.clear() }
        status = "off"
        onChanged?.invoke()
    }

    @Synchronized internal fun resolve(displays: List<GeelyDisplayInfo>): GeelyDisplayInfo? = policy.resolve(displays)
    @Synchronized internal fun observeConnection(connected: Boolean) = policy.observeConnection(connected)
    @Synchronized internal fun removed(displayId: Int) = policy.removed(displayId)
}

/**
 * Foreground, manually selected Android Presentation carrying next-turn cues only.
 * No OEM protocol, background overlay, display launch, permission request, or automatic selection.
 * CarPlayGlance already expires silent routes at 30 seconds; polling also observes that expiry when
 * no new frame arrives. It does not replace the widget's existing single listener.
 */
internal class GeelyHudProjection(private val activity: Activity) : DisplayManager.DisplayListener {
    private val main = Handler(Looper.getMainLooper())
    private val manager = activity.getSystemService(DisplayManager::class.java)
    private var running = false
    private var presentation: NavigationPresentation? = null
    private val refreshTask = Runnable { refresh() }
    private val selectionChanged: () -> Unit = { main.post(refreshTask); Unit }

    fun start() {
        if (running) return
        running = true
        GeelyHudSelection.onChanged = selectionChanged
        runCatching { manager?.registerDisplayListener(this, main) }.onFailure {
            GeelyHudSelection.clear()
            GeelyHudSelection.status = "display_listener_unavailable:${it.javaClass.simpleName}"
        }
        refresh()
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(refreshTask)
        if (GeelyHudSelection.onChanged === selectionChanged) {
            GeelyHudSelection.onChanged = null
            GeelyHudSelection.clear()
        }
        runCatching { manager?.unregisterDisplayListener(this) }
        dismiss()
    }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) {
        val wasSelected = GeelyHudSelection.selectedDisplayId() == displayId
        GeelyHudSelection.removed(displayId)
        if (wasSelected) GeelyHudSelection.status = "display_removed_select_again"
        refresh()
    }

    private fun refresh() {
        main.removeCallbacks(refreshTask)
        if (!running || activity.isFinishing || activity.isDestroyed) {
            dismiss()
            return
        }
        if (GeelyHudSelection.selectedDisplayId() == null) {
            dismiss()
            return
        }
        val selected = GeelyHudSelection.resolve(GeelyDisplayCapabilities.read(activity).displays)
        if (selected == null) {
            GeelyHudSelection.status = "display_changed_or_unavailable_select_again"
            dismiss()
            return
        }
        val glance = CarPlayGlance.snapshot()
        GeelyHudSelection.observeConnection(glance.connected)
        if (!glance.connected || glance.maneuverType == null) {
            GeelyHudSelection.status = if (glance.connected) "no_fresh_navigation" else "not_connected"
            dismiss()
        } else {
            val display = manager?.getDisplay(selected.displayId)
            if (display == null) {
                GeelyHudSelection.clear()
                GeelyHudSelection.status = "display_removed_select_again"
                dismiss()
                return
            }
            try {
                if (presentation?.display?.displayId != selected.displayId) {
                    dismiss()
                    val next = NavigationPresentation(activity, display)
                    presentation = next
                    next.setOnDismissListener {
                        if (presentation === next) {
                            presentation = null
                            GeelyHudSelection.clear()
                            GeelyHudSelection.status = "presentation_dismissed_select_again"
                        }
                    }
                    next.show()
                }
                presentation?.render(glance)
                GeelyHudSelection.status = "navigation_cue_on_public_display_role_unconfirmed"
            } catch (error: RuntimeException) {
                dismiss()
                GeelyHudSelection.clear()
                GeelyHudSelection.status = "presentation_rejected:${error.javaClass.simpleName}"
                return
            }
        }
        // Expiry is calculated at the source, not extended every time this snapshot is polled.
        if (GeelyHudSelection.selectedDisplayId() != null) main.postDelayed(refreshTask, 250L)
    }

    private fun dismiss() {
        val previous = presentation
        presentation = null
        previous?.setOnDismissListener(null)
        previous?.render(null)
        runCatching { previous?.dismiss() }
    }
}

private class NavigationPresentation(context: Context, display: Display) : Presentation(context, display) {
    private var guidanceView: NavigationCueView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
        guidanceView = NavigationCueView(context).also { setContentView(it) }
    }

    fun render(snapshot: CarPlayGlance.Snapshot?) {
        guidanceView?.guidance = snapshot?.takeIf { it.connected && it.maneuverType != null }
    }
}

/** Bounds-relative rendering: no physical-screen constants, no map/video, song, or vehicle values. */
private class NavigationCueView(context: Context) : View(context) {
    var guidance: CarPlayGlance.Snapshot? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    private val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val roadPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = guidance ?: return
        val maneuver = value.maneuverType ?: return
        if (width <= 0 || height <= 0) return
        val unit = minOf(height.toFloat(), width * 0.5f)
        val margin = unit * 0.10f
        val iconSize = unit * 0.52f
        val top = (height - iconSize) / 2f
        ContextCompat.getDrawable(context, NavigationWidgetUpdater.arrow(maneuver, value.drivingSide))?.apply {
            setTint(Color.WHITE)
            setBounds(margin.toInt(), top.toInt(), (margin + iconSize).toInt(), (top + iconSize).toInt())
            draw(canvas)
        }
        val textLeft = margin * 2 + iconSize
        val textWidth = (width - textLeft - margin).coerceAtLeast(0f)
        val meters = value.distanceMeters.coerceAtLeast(0)
        val distance = if (meters < 1_000) context.getString(R.string.widget_distance_meters, meters)
            else context.getString(R.string.widget_distance_km_decimal, meters / 1_000.0)
        distancePaint.textSize = unit * 0.23f
        // Bound text to this display, including narrow windows and large accessibility fonts.
        val measured = distancePaint.measureText(distance)
        if (measured > textWidth && measured > 0f) distancePaint.textSize *= textWidth / measured
        canvas.drawText(distance, textLeft, height / 2f, distancePaint)
        roadPaint.textSize = unit * 0.12f
        val road = TextUtils.ellipsize(value.road, roadPaint, textWidth, TextUtils.TruncateAt.END)
        canvas.drawText(road.toString(), textLeft, height / 2f + unit * 0.22f, roadPaint)
    }
}
