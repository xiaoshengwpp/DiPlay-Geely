package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.IntentFilter
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Geely must retain standard settings intents even if a BYD decoy package is present on Android 7.1.
 * Firmware detection cannot authorize a product-specific explicit component or background home launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en", manifest = Config.NONE)
class BydSettingsDecoyTest {
    private lateinit var activity: DiPlayActivity
    private lateinit var content: LinearLayout

    private val decoy = ComponentName(BYD_PACKAGE, "$BYD_PACKAGE.main.unsupport.UnsupportActivity")
    private val bydBluetooth =
        ComponentName("com.byd.btsetting", "com.byd.btsetting.BluetoothManagerDialog")
    private val bydHotspot =
        ComponentName(BYD_PACKAGE, "$BYD_PACKAGE.system.mvp.view.link.hotspot.PerHospotActivity")
    private val stockSettings =
        ComponentName("com.android.settings", "com.android.settings.Settings\$BluetoothSettingsActivity")

    @Before fun setUp() {
        assertFalse(GeelyVendorPolicy.bydAdb)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        content = LinearLayout(activity)
        // Render the one card that holds the button under test rather than the whole settings page.
        ReflectionHelpers.setField(activity, "settingsSectionFilter",
            setOf(SettingsSection.PERMISSIONS_AND_HELP))
    }

    /** Installs a component that can be opened by name, without claiming any action. */
    private fun install(component: ComponentName) {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addActivityIfNotPresent(component)
    }

    /** Makes [action] resolve to [component], the way a head unit's settings app would. */
    private fun resolveTo(component: ComponentName, action: String) {
        val packages = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        packages.addActivityIfNotPresent(component)
        packages.addIntentFilterForActivity(component, IntentFilter(action))
    }

    private fun bluetoothButton(): Button {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
        return descendants(content).filterIsInstance<Button>()
            .single { it.text == activity.getString(R.string.bluetooth_settings) }
    }

    /** Each read pops the queue; read the expected intent, then check no extra launch remains. */
    private fun started() = shadowOf(activity).nextStartedActivity

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    @Test @Config(sdk = [25])
    fun theDecoyOnAndroid71CannotActivateBydsBluetoothReplacementInGeely() {
        resolveTo(decoy, Settings.ACTION_BLUETOOTH_SETTINGS)
        install(bydBluetooth)

        bluetoothButton().performClick()

        val intent = started()
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, intent.action)
        assertNull(intent.component)
        assertNull(started())
    }

    @Test @Config(sdk = [25])
    fun aStockSettingsScreenKeepsTheStandardAction() {
        resolveTo(stockSettings, Settings.ACTION_BLUETOOTH_SETTINGS)
        install(bydBluetooth)

        bluetoothButton().performClick()

        val intent = started()
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, intent.action)
        assertNull(intent.component)
    }

    /**
     * The same firmware one Android version later is left alone: the replacement components are only
     * audited on 7.1, so the decoy is not even looked for and the driver still gets the decoy's answer.
     */
    @Test @Config(sdk = [26])
    fun theDecoyIsIgnoredAboveAndroid71() {
        resolveTo(decoy, Settings.ACTION_BLUETOOTH_SETTINGS)
        install(bydBluetooth)

        bluetoothButton().performClick()

        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, started().action)
    }

    @Test @Config(sdk = [25])
    fun detectionStaysDisabledEvenWhenEveryActionResolvesToBydsDecoy() {
        for (action in listOf(Settings.ACTION_BLUETOOTH_SETTINGS, TETHER_ACTION, Settings.ACTION_WIRELESS_SETTINGS)) {
            resolveTo(decoy, action)
            assertFalse(ReflectionHelpers.callInstanceMethod<Boolean>(activity, "answeredByBydDecoy",
                ReflectionHelpers.ClassParameter.from(String::class.java, action)))
        }
        assertNull(started())
    }

    @Test @Config(sdk = [25])
    fun aDirectBydScreenCallCannotBypassTheProductGate() {
        for (component in listOf(bydBluetooth, bydHotspot)) {
            install(component)
            assertFalse(ReflectionHelpers.callInstanceMethod<Boolean>(activity, "openBydScreen",
                ReflectionHelpers.ClassParameter.from(ComponentName::class.java, component)))
            assertNull(started())
        }
    }

    @Test @Config(sdk = [25])
    fun missingTetherActionUsesStandardWirelessFallbackInsteadOfInstalledBydHotspot() {
        install(bydHotspot)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "openCarWifiSettings")

        val intent = started()
        assertEquals(Settings.ACTION_WIRELESS_SETTINGS, intent.action)
        assertNull(intent.component)
        assertNull(started())
    }

    @Test @Config(sdk = [25])
    fun aHotspotDecoyCannotActivateTheExplicitBydReplacement() {
        resolveTo(decoy, TETHER_ACTION)
        resolveTo(decoy, Settings.ACTION_WIRELESS_SETTINGS)
        install(bydHotspot)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "openCarWifiSettings")

        val intent = started()
        assertEquals(TETHER_ACTION, intent.action)
        assertNull(intent.component)
        assertNull(started())
    }

    @Test @Config(sdk = [25])
    fun aLegacyBydSettingsPackageCannotLaunchHomeBeforeStandardTetherSettings() {
        resolveTo(ComponentName("com.byd.carsettings", "com.byd.carsettings.HotspotSettings"), TETHER_ACTION)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "openCarWifiSettings")

        val intent = started()
        assertEquals(TETHER_ACTION, intent.action)
        assertNull(intent.component)
        assertNull(started())
    }

    @Test @Config(sdk = [25])
    fun aLegacyBydSettingsPackageCannotLaunchHomeBeforeStandardClientWifiSettings() {
        resolveTo(ComponentName("com.byd.carsettings", "com.byd.carsettings.WifiSettings"), Settings.ACTION_WIFI_SETTINGS)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "openCarClientWifiSettings")

        val intent = started()
        assertEquals(Settings.ACTION_WIFI_SETTINGS, intent.action)
        assertNull(intent.component)
        assertNull(started())
    }

    private companion object {
        const val BYD_PACKAGE = "com.byd.vehiclesettings"
        const val TETHER_ACTION = "com.android.settings.WIFI_TETHER_SETTINGS"
    }
}
