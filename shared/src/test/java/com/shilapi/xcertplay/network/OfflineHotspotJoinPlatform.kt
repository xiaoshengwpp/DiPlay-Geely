package com.shilapi.xcertplay.network

/** Synthetic-service adapter for transaction tests; application ports remain inert. */
internal class OfflineHotspotJoinPlatform(
    private val wifi: Any,
    api: Int,
    firmwareKey: String,
    capability: () -> HotspotJoinCapability.Snapshot? = { null },
) : HotspotJoinTransaction.Port<Any> {
    private val models = HotspotJoinPlatform(wifi, api, firmwareKey, capability)
    private val configType = Class.forName("android.net.wifi.SoftApConfiguration")
    val codec = models.codec

    override fun firmware() = models.firmware()
    override fun repaired(config: Any) = models.repaired(config)
    override fun read(): Any = wifi.javaClass.getMethod("getSoftApConfiguration").invoke(wifi)
        ?: throw IllegalStateException()
    override fun hotspotOff(): Boolean? = when (wifi.javaClass.getMethod("getWifiApEnabledState").invoke(wifi) as? Int) {
        11 -> true
        13 -> false
        else -> null
    }
    override fun write(config: Any): Boolean = wifi.javaClass.getMethod(
        "setSoftApConfiguration", configType, String::class.java,
    ).invoke(wifi, config, "com.android.shell") == true
}
