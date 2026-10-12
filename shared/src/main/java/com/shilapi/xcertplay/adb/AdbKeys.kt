package com.shilapi.xcertplay.adb

import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import android.content.Context
import com.shilapi.xcertplay.compat.Base64Compat
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey

/** ADB protocol helpers; credential loading is unavailable in this Geely build. */
object AdbKeys {
    private const val KEY_BITS = 2048
    private const val NAME = " diplay@headunit"

    // ADB signs the token as if it were a SHA-1 digest: the host prepends SHA-1's DigestInfo header.
    private val SHA1_DIGEST_INFO = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    /** Retains the call contract without accessing storage or creating credentials. */
    @Suppress("UNUSED_PARAMETER")
    @Synchronized
    fun load(context: Context): KeyPair {
        check(GeelyVendorPolicy.bydAdb) { "Local ADB is disabled for the Geely profile" }
        error("ADB credential loading is unavailable in the Geely build")
    }

    fun sign(token: ByteArray, key: PrivateKey): ByteArray =
        Signature.getInstance("NONEwithRSA").run {
            initSign(key)
            update(SHA1_DIGEST_INFO + token)
            sign()
        }

    /**
     * The public key as adbd stores it (AOSP android_pubkey): modulus size in words, n0inv = -1/n mod
     * 2^32, the modulus and R^2 mod n (R = 2^2048) as little-endian numbers, then the exponent;
     * Base64, a name and a NUL.
     */
    fun publicKeyMessage(key: PublicKey): ByteArray {
        val rsa = key as RSAPublicKey
        val n = rsa.modulus
        val words = KEY_BITS / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
        val rr = BigInteger.ONE.shiftLeft(2 * KEY_BITS).mod(n)
        val blob = ByteBuffer.allocate(8 + 2 * KEY_BITS / 8 + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(words)
            putInt(n0inv.toInt())
            put(littleEndian(n, KEY_BITS / 8))
            put(littleEndian(rr, KEY_BITS / 8))
            putInt(rsa.publicExponent.toInt())
        }.array()
        return (Base64Compat.encode(blob) + NAME + "\u0000").toByteArray(Charsets.UTF_8)
    }

    private fun littleEndian(value: BigInteger, size: Int): ByteArray {
        val bigEndian = value.toByteArray().takeLast(size) // drops BigInteger's sign byte
        return ByteArray(size) { index -> bigEndian.getOrElse(bigEndian.size - 1 - index) { 0 } }
    }
}
