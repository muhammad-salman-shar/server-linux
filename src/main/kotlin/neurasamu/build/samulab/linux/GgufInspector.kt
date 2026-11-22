package neurasamu.build.samulab.linux

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class GgufMetadataInfo(
    val architecture: String = "",
    val modelName: String = "",
    val nativeContext: Int = 0,
    val templateHintsThinking: Boolean = false
)

object GgufInspector {
    private val cache = ConcurrentHashMap<String, GgufMetadataInfo>()

    fun inspect(file: File): GgufMetadataInfo {
        if (!file.isFile || !file.name.lowercase().endsWith(".gguf")) {
            return GgufMetadataInfo()
        }
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        cache[key]?.let { return it }

        val result = runCatching { parseGgufHeader(file) }.getOrElse { GgufMetadataInfo() }
        cache[key] = result
        return result
    }

    private fun parseGgufHeader(file: File): GgufMetadataInfo {
        var arch = ""
        var modelName = ""
        var nativeCtx = 0
        var templateThinking = false

        BufferedInputStream(FileInputStream(file), 64 * 1024).use { inp ->
            val magic = readExact(inp, 4)
            if (magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte() ||
                magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()
            ) {
                return GgufMetadataInfo()
            }

            val version = readUInt32LE(inp)
            if (version < 2 || version > 5) return GgufMetadataInfo()

            /* tensorCount */ readUInt64LE(inp)
            val kvCount = readUInt64LE(inp).coerceAtMost(4096L)

            for (i in 0 until kvCount) {
                val k = readGgufString(inp, maxLen = 1024) ?: break
                val vType = readUInt32LE(inp)

                val isCtxKey = k.endsWith(".context_length")
                val isArchKey = k == "general.architecture"
                val isNameKey = k == "general.name"
                val isTmplKey = k == "tokenizer.chat_template"

                when {
                    isCtxKey -> {
                        val v = readNumericAsInt(inp, vType)
                        if (v != null && v > 0) nativeCtx = v
                    }
                    isArchKey && vType == 8 -> {
                        arch = readGgufString(inp, maxLen = 256).orEmpty()
                    }
                    isNameKey && vType == 8 -> {
                        modelName = readGgufString(inp, maxLen = 512).orEmpty()
                    }
                    isTmplKey && vType == 8 -> {
                        val tmpl = readGgufString(inp, maxLen = 256 * 1024).orEmpty().lowercase()
                        if (tmpl.contains("<think>") || tmpl.contains("enable_thinking") || tmpl.contains("reasoning")) {
                            templateThinking = true
                        }
                    }
                    else -> {
                        if (!skipGgufValue(inp, vType)) break
                    }
                }

                // Stop early once we have context length, architecture, and chat template
                if (nativeCtx > 0 && arch.isNotEmpty() && isTmplKey) break
            }
        }

        return GgufMetadataInfo(
            architecture = arch,
            modelName = modelName,
            nativeContext = nativeCtx,
            templateHintsThinking = templateThinking
        )
    }

    private fun readExact(inp: InputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = inp.read(buf, off, len - off)
            if (n < 0) throw EOFException()
            off += n
        }
        return buf
    }

    private fun skipFully(inp: InputStream, count: Long): Boolean {
        if (count < 0L || count > 256L * 1024L * 1024L) return false
        var rem = count
        while (rem > 0) {
            val skipped = inp.skip(rem)
            if (skipped > 0) {
                rem -= skipped
            } else {
                if (inp.read() < 0) return false
                rem--
            }
        }
        return true
    }

    private fun readUInt32LE(inp: InputStream): Int {
        val b = readExact(inp, 4)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readUInt64LE(inp: InputStream): Long {
        val b = readExact(inp, 8)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readGgufString(inp: InputStream, maxLen: Int): String? {
        val len = readUInt64LE(inp)
        if (len < 0L || len > 32L * 1024L * 1024L) return null
        if (len > maxLen.toLong()) {
            if (!skipFully(inp, len)) return null
            return ""
        }
        val bytes = readExact(inp, len.toInt())
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readNumericAsInt(inp: InputStream, vType: Int): Int? {
        return when (vType) {
            0, 1, 7 -> readExact(inp, 1)[0].toInt() and 0xFF
            2, 3 -> ByteBuffer.wrap(readExact(inp, 2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            4, 5 -> readUInt32LE(inp)
            10, 11 -> readUInt64LE(inp).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            else -> {
                skipGgufValue(inp, vType)
                null
            }
        }
    }

    private fun fixedElementSize(t: Int): Int = when (t) {
        0, 1, 7 -> 1
        2, 3 -> 2
        4, 5, 6 -> 4
        10, 11, 12 -> 8
        else -> -1
    }

    private fun skipGgufValue(inp: InputStream, vType: Int): Boolean {
        val fixed = fixedElementSize(vType)
        if (fixed > 0) return skipFully(inp, fixed.toLong())
        return when (vType) {
            8 -> {
                val len = readUInt64LE(inp)
                skipFully(inp, len)
            }
            9 -> {
                val elemType = readUInt32LE(inp)
                val count = readUInt64LE(inp)
                if (count < 0L || count > 2_000_000L) return false
                val elemFixed = fixedElementSize(elemType)
                if (elemFixed > 0) {
                    skipFully(inp, count * elemFixed.toLong())
                } else {
                    for (j in 0 until count) {
                        if (!skipGgufValue(inp, elemType)) return false
                    }
                    true
                }
            }
            else -> false
        }
    }
}
