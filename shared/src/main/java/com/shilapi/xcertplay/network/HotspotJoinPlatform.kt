package com.shilapi.xcertplay.network

import android.os.Parcel
import android.os.Parcelable

/** Inert platform port with offline configuration transforms and parcel codecs. */
@Suppress("UNUSED_PARAMETER")
internal class HotspotJoinPlatform(wifi: Any, private val api: Int,
    private val firmwareKey: String,
    private val capability: () -> HotspotJoinCapability.Snapshot? = { null }) : HotspotJoinTransaction.Port<Any> {
    private val configType by lazy { Class.forName("android.net.wifi.SoftApConfiguration") }
    private val builderType by lazy { Class.forName("android.net.wifi.SoftApConfiguration\$Builder") }
    private val elementType by lazy { Class.forName("android.net.wifi.ScanResult\$InformationElement") }
    override fun firmware() = firmwareKey
    override fun read(): Any = throw UnsupportedOperationException("Hotspot repair is disabled")
    override fun hotspotOff(): Boolean? = null
    override fun write(config: Any): Boolean = false

    override fun repaired(config: Any): Any? {
        if (api < 33) return null
        // WifiApConfigStore forces this flag true on every non-null setter call. A false
        // original therefore cannot be restored verbatim, even when every other field is
        // preserved. Refuse it before preparing a target; Apply repeats this same gate.
        if (configType.getMethod("isUserConfiguration").invoke(config) != true) return null
        val bands = configType.getMethod("getBands").invoke(config) as IntArray
        val existing = elements(config)
        val policy = existing.map(::description)
        val merged = HotspotJoinElement.merge(api, bands, policy) ?: return null
        val channel = configType.getMethod("getChannel").invoke(config) as Int
        if (capability()?.allows(channel) != true) return null
        if (merged == policy) return config
        val payload = byteArrayOf(0, 0xa0.toByte(), 0x40, 0, 0, 2, 0, 0x21)
        val added = elementType.getConstructor(Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, ByteArray::class.java).newInstance(221, 0, payload)
        val next = withElements(config, existing + added)
        // Verify copy semantics on this firmware before permitting a transaction.
        if (withElements(next, existing) != config || elements(next).map(::description) != merged) return null
        return next
    }

    private fun elements(config: Any): List<Any> {
        val list = configType.getMethod("getVendorElements").invoke(config) as List<*>
        return list.map { it ?: throw IllegalStateException() }
    }
    private fun description(element: Any): HotspotJoinElement.Element {
        val bytes = elementType.getField("bytes").get(element) as ByteArray
        return HotspotJoinElement.Element(elementType.getField("id").getInt(element),
            elementType.getField("idExt").getInt(element), bytes.joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    private fun withElements(config: Any, elements: List<Any>): Any {
        val builder = builderType.getConstructor(configType).newInstance(config)
        builderType.getMethod("setVendorElements", List::class.java).invoke(builder, elements)
        return builderType.getMethod("build").invoke(builder) ?: throw IllegalStateException()
    }
    val codec = object : HotspotJoinJournal.Codec<Any> {
        override fun encode(config: Any): ByteArray {
            val parcel = Parcel.obtain()
            return try { (config as Parcelable).writeToParcel(parcel, 0); parcel.marshall() }
            finally { parcel.recycle() }
        }
        override fun decode(bytes: ByteArray): Any {
            val parcel = Parcel.obtain()
            return try {
                parcel.unmarshall(bytes, 0, bytes.size); parcel.setDataPosition(0)
                val creator = configType.getField("CREATOR").get(null) as Parcelable.Creator<*>
                val config = creator.createFromParcel(parcel) ?: throw IllegalStateException()
                if (parcel.dataAvail() != 0) throw IllegalStateException()
                config
            } finally { parcel.recycle() }
        }
    }
    companion object {
        fun connect(): HotspotJoinPlatform = throw UnsupportedOperationException("Hotspot repair is disabled")
    }
}
