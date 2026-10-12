package com.shilapi.xcertplay.network


/** Wi-Fi Mainline modules and vendor images can change independently of Build.FINGERPRINT. */
internal object HotspotJoinFirmware {
    fun identity(properties: Map<String, String>, modules: Map<String, String>): String? {
        if (modules.isEmpty() || modules.values.any { it.isBlank() }) return null
        val parts = properties.toSortedMap().entries + modules.toSortedMap().entries
        // Length-prefix every component; do not depend on delimiters occurring in OEM build names.
        val bytes = parts.joinToString("") { "${it.key.length}:${it.key}${it.value.length}:${it.value}" }.toByteArray()
        return hex(HotspotJoinJournal.digest(bytes))
    }

    fun current(): String? = null

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
