package com.shilapi.xcertplay

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.update.UpdateRelease
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-w900dp-h600dp-land")
class UpdateHomeAvailabilityTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    @After fun clear() = UpdateAvailability.clearAllForTest(application)

    @Test fun cachedOfficialUpdateCannotAppearOrOpenAnUpdatePage() = assertCachedUpdateBlocked()

    @Config(sdk = [29], qualifiers = "en-w400dp-h900dp-port")
    @Test fun compactHomeAlsoRejectsCachedUpdateAcrossResume() = assertCachedUpdateBlocked()

    private fun assertCachedUpdateBlocked() {
        val release = release()
        UpdateAvailability.save(application, release)
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        fun assertNoUpdateControls() {
            val texts = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertFalse(texts.contains(activity.getString(R.string.update_available_home)))
            assertFalse(texts.contains(activity.getString(R.string.update_download)))
            assertNull(ReflectionHelpers.getField<UpdateRelease?>(activity, "availableUpdate"))
            assertNull(ReflectionHelpers.getField<UpdateRelease?>(activity, "updateRelease"))
        }
        try {
            val page = ReflectionHelpers.getField<String>(activity, "page")
            assertNoUpdateControls()
            repeat(2) {
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "openAvailableUpdate",
                    ReflectionHelpers.ClassParameter.from(UpdateRelease::class.java, release))
                assertEquals(page, ReflectionHelpers.getField<String>(activity, "page"))
                assertNoUpdateControls()
                assertNull("No installer intent may be launched", shadowOf(activity).nextStartedActivity)
            }
            controller.pause().stop().start().resume()
            assertNoUpdateControls()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun release() = UpdateRelease(
        tagName = "v99.0.0", apkName = "DiPlay.apk",
        apkUrl = "https://example.com/DiPlay.apk",
        checksumsUrl = "https://example.com/SHA256SUMS.txt",
    )
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
