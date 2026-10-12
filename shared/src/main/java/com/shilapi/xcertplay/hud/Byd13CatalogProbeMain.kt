package com.shilapi.xcertplay.hud


/** No-op compatibility interface. This product has no live vehicle access implementation. */
object Byd13CatalogProbeMain {
    const val HEADER = "DIPLAY_BYD13_CATALOG_V1"
    @JvmStatic fun main(args: Array<String>) = Unit
}

internal data class BydFirmwareCatalog(
    val symbols: Map<String, Int>,
    val devices: Map<String, Int>,
) {
    fun fid(field: BydVehicleField): Int? =
        (listOf(field.symbol) + field.symbolAliases).firstNotNullOfOrNull(symbols::get)

    fun device(field: BydVehicleField): Int = devices[field.deviceName] ?: field.defaultDevice

    companion object {
        private const val DEVICE_PREFIX = "BYDAutoConstants.BYDAUTO_DEVICE_"

        fun parse(output: String?): BydFirmwareCatalog? {
            val text = output ?: return null
            if (Byd13CatalogProbeMain.HEADER !in text.lineSequence().map(String::trim)) return null
            val symbols = LinkedHashMap<String, Int>()
            val devices = LinkedHashMap<String, Int>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                val equals = line.lastIndexOf('=')
                if (equals <= 0 || equals == line.lastIndex) continue
                val key = line.substring(0, equals)
                val value = line.substring(equals + 1).toLongOrNull()
                    ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: continue
                if (key.startsWith(DEVICE_PREFIX)) {
                    devices[key.removePrefix(DEVICE_PREFIX)] = value
                } else if ('.' in key && !key.startsWith("BYDAutoFeatureIds.")) {
                    symbols[key] = value
                }
            }
            return BydFirmwareCatalog(symbols, devices).takeIf { it.symbols.isNotEmpty() }
        }
    }
}
