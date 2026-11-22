package neurasamu.build.samulab.linux

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

object EmojiSupport {
    val fontFamily: FontFamily? by lazy { loadColorEmojiFont() }

    private fun loadColorEmojiFont(): FontFamily? {
        val fromRes = runCatching {
            Thread.currentThread().contextClassLoader
                .getResourceAsStream("fonts/emoji.ttf")
                ?.use { it.readBytes() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { bytes -> FontFamily(Font(identity = "SaMuColorEmoji", data = bytes)) }
        }.getOrNull()
        if (fromRes != null) return fromRes

        val sysCandidates = listOf(
            "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf",
            "/usr/share/fonts/noto/NotoColorEmoji.ttf",
            "/usr/share/fonts/google-noto-emoji/NotoColorEmoji.ttf",
            "/usr/share/fonts/truetype/twemoji/TwemojiMozilla.ttf"
        )
        for (path in sysCandidates) {
            val f = File(path)
            if (f.isFile && f.canRead()) {
                val loaded = runCatching {
                    FontFamily(Font(identity = "SysColorEmoji", data = f.readBytes()))
                }.getOrNull()
                if (loaded != null) return loaded
            }
        }
        return null
    }

    private fun isEmojiCodePoint(cp: Int): Boolean =
        (cp in 0x1F300..0x1FAFF) ||
            (cp in 0x1F1E6..0x1F1FF) ||
            (cp in 0x1F000..0x1F02F) ||
            (cp in 0x1F0A0..0x1F0FF) ||
            (cp in 0x2600..0x27BF) ||
            (cp in 0x2300..0x23FF) ||
            cp == 0x200D ||
            cp == 0xFE0F ||
            cp == 0x20E3 ||
            (cp in 0x1F3FB..0x1F3FF)

    fun AnnotatedString.Builder.appendWithEmojis(text: String) {
        val ef = fontFamily
        if (ef == null || text.isEmpty()) {
            append(text)
            return
        }
        var i = 0
        val len = text.length
        while (i < len) {
            val cp = Character.codePointAt(text, i)
            if (isEmojiCodePoint(cp)) {
                val start = i
                i += Character.charCount(cp)
                while (i < len && isEmojiCodePoint(Character.codePointAt(text, i))) {
                    i += Character.charCount(Character.codePointAt(text, i))
                }
                pushStyle(SpanStyle(fontFamily = ef))
                append(text.substring(start, i))
                pop()
            } else {
                val start = i
                i += Character.charCount(cp)
                while (i < len && !isEmojiCodePoint(Character.codePointAt(text, i))) {
                    i += Character.charCount(Character.codePointAt(text, i))
                }
                append(text.substring(start, i))
            }
        }
    }

    fun format(text: String): AnnotatedString = buildAnnotatedString {
        appendWithEmojis(text)
    }
}

fun sanitizeApiUrl(raw: String): String {
    var s = raw.trim()
    // Heal accidental markdown link syntax like [http://127.0.0.1](http://127.0.0.1):8080/v1
    val mdLink = Regex("""\[([^\]]+)\]\((https?://[^)]+)\)""")
    s = mdLink.replace(s) { m -> m.groupValues[2] }
    s = s.trim()
    if (s.isNotEmpty() && !s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        s = "http://$s"
    }
    return s
}
