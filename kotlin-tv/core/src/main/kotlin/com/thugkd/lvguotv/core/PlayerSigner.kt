// Adapted from Xanerix/guoapp ad8dc125; see kotlin-tv/NOTICE.md.
// SPDX-License-Identifier: GPL-3.0-only
package com.thugkd.lvguotv.core

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Playback uses a different signature contract from the catalog/search endpoints. */
internal object PlayerSigner {
    private val random = SecureRandom()

    fun headers(query: String, body: ByteArray, now: Long, deviceId: String, aid: String): Map<String, String> {
        val timestamp = (now / 1000).toInt()
        val nonce = ByteArray(11).also(random::nextBytes)
        val bodyHash = digest("MD5", body)
        val payload = ByteArray(20)
        digest("MD5", query.toByteArray()).copyInto(payload, endIndex = 4)
        bodyHash.copyInto(payload, destinationOffset = 4, endIndex = 4)
        byteArrayOf(0, 6, 11, 28).copyInto(payload, 12)
        be32(timestamp).copyInto(payload, 16)
        val key = hex("44b9b9d9a4aef9fca493aa757ca3c2c4a496938f")
        for (index in payload.indices) payload[index] = (payload[index].u() xor key[index].u()).toByte()
        for (index in payload.indices) {
            val value = payload[index].u()
            val rotated = ((value shl 4) or (value ushr 4)) and 255
            val mixed = rotated xor payload[(index + 1) % payload.size].u()
            payload[index] = ((Integer.reverse(mixed) ushr 24) xor 255 xor payload.size).toByte()
        }
        return linkedMapOf(
            "X-SS-STUB" to bodyHash.hex().uppercase(),
            "X-Khronos" to (timestamp.toLong() and 0xffffffffL).toString(),
            "X-Gorgon" to (hex("8404401c0000") + payload).hex(),
            "X-SS-Req-Ticket" to now.toString(),
            "X-Argus" to argus(query, bodyHash, timestamp, deviceId, nonce),
            "X-Ladon" to ladon(aid, timestamp, nonce.copyOfRange(2, 6)),
        )
    }

    private class Message {
        private val output = ByteArrayOutputStream()
        private fun varint(value: Long) {
            var remaining = value
            while (remaining and -128L != 0L) {
                output.write((remaining.toInt() and 127) or 128)
                remaining = remaining ushr 7
            }
            output.write(remaining.toInt())
        }
        fun number(field: Int, value: Long) {
            varint(field.toLong() * 8)
            varint(value)
        }
        fun bytes(field: Int, value: ByteArray) {
            varint(field.toLong() * 8 + 2)
            varint(value.size.toLong())
            output.write(value)
        }
        fun text(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
        fun result(): ByteArray = output.toByteArray()
    }

    private fun argus(query: String, bodyHash: ByteArray, timestamp: Int, deviceId: String, nonce: ByteArray): String {
        val key = hex("ac1adaae95a7af94a5114ab3b3a97dd80050aa0a39314c40528caec95256c28c")
        val counters = Message().apply {
            number(1, 1); number(2, 1); number(3, 1); number(7, 3348294860L)
        }.result()
        val environment = Message().apply {
            text(1, "25053RT47C"); text(2, "16"); text(3, "update_64"); number(4, 2147483648L)
        }.result()
        val message = Message().apply {
            number(1, 0x20200929L * 2); number(2, 2)
            number(3, nonce.i32(6, true).toLong() and 0x7fffffffL)
            text(4, "8662"); text(5, deviceId); text(6, "1611921764")
            text(7, "7.3.5.32"); text(8, "v04.07.01-ml-android"); number(9, 135135744)
            bytes(10, ByteArray(8)); number(11, 0); number(12, (timestamp.toLong() and 0xffffffffL) * 2)
            bytes(13, sm3(bodyHash).copyOf(6)); bytes(14, sm3(query.toByteArray()).copyOf(6))
            bytes(15, counters); bytes(16, byteArrayOf()); text(20, "none"); number(21, 738)
            bytes(23, environment); number(25, 2)
        }.result()
        val data = pad(message)
        val derived = sm3(key + hex("3ccc567b") + key)
        val rounds = LongArray(72)
        for (index in 0 until 4) rounds[index] = derived.i64(index * 8, true)
        for (index in 4 until rounds.size) {
            var mixed = java.lang.Long.rotateRight(rounds[index - 1], 3) xor rounds[index - 3]
            mixed = mixed xor java.lang.Long.rotateRight(mixed, 1)
            val bit = (0x3dc94c3a046d678bL ushr ((index - 4) % 62)) and 1L
            rounds[index] = rounds[index - 4].inv() xor mixed xor bit xor 3L
        }
        for (offset in data.indices step 16) {
            var left = data.i64(offset, true)
            var right = data.i64(offset + 8, true)
            for (round in rounds) {
                val next = left xor (java.lang.Long.rotateLeft(right, 1) and java.lang.Long.rotateLeft(right, 8)) xor
                    java.lang.Long.rotateLeft(right, 2) xor round
                left = right; right = next
            }
            le64(left).copyInto(data, offset)
            le64(right).copyInto(data, offset + 8)
        }
        val mask = hex("d04ffdffd04ffdff")
        val reversed = (mask + xor(data, mask)).reversedArray()
        val container = pad(hex("a6e783ee7001100918") + reversed + hex("567b"))
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(digest("MD5", key.copyOfRange(0, 16)), "AES"),
            IvParameterSpec(digest("MD5", key.copyOfRange(16, 32))))
        return (hex("3ccc") + cipher.doFinal(container)).base64()
    }

    private fun ladon(aid: String, timestamp: Int, prefix: ByteArray): String {
        val material = digest("MD5", prefix + aid.toByteArray()).hex().toByteArray()
        var left = material.i64(0, true)
        var right = material.i64(8, true)
        val queue = java.util.ArrayDeque<Long>().apply {
            add(material.i64(16, true)); add(material.i64(24, true))
        }
        val keys = LongArray(34)
        for (index in keys.indices) {
            keys[index] = left
            val mixed = (java.lang.Long.rotateRight(right, 8) + left) xor index.toLong()
            queue.add(mixed)
            left = mixed xor java.lang.Long.rotateLeft(left, 3)
            right = queue.removeFirst()
        }
        val data = pad("${timestamp.toLong() and 0xffffffffL}-1611921764-$aid".toByteArray())
        for (offset in data.indices step 16) {
            left = data.i64(offset, true); right = data.i64(offset + 8, true)
            for (key in keys) {
                right = key xor (left + java.lang.Long.rotateRight(right, 8))
                left = right xor java.lang.Long.rotateLeft(left, 3)
            }
            le64(left).copyInto(data, offset)
            le64(right).copyInto(data, offset + 8)
        }
        return (prefix + data).base64()
    }

    private fun pad(data: ByteArray): ByteArray {
        val count = 16 - data.size % 16
        return data + ByteArray(count) { count.toByte() }
    }
}
