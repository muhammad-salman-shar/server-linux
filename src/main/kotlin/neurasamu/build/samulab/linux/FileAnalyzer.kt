package neurasamu.build.samulab.linux

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.ZipInputStream

enum class AttachmentKind { IMAGE, AUDIO, VIDEO, PDF, DOCUMENT, CODE_TEXT, BINARY }

data class AnalyzedFile(
    val kind: AttachmentKind,
    val mimeType: String,
    val extractedText: String? = null,
    val base64Data: String? = null,
    val audioFormat: String? = null
)

object FileAnalyzer {
    private val imageExts = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val audioExts = setOf("wav", "mp3", "ogg", "flac", "m4a", "aac")
    private val videoExts = setOf("mp4", "webm", "mkv", "mov", "avi", "gif")
    private val officeZipExts = setOf("docx", "xlsx", "pptx", "odt", "odp", "ods", "epub")
    private val textCodeExts = setOf(
        "txt", "md", "markdown", "json", "jsonl", "yaml", "yml", "xml", "csv", "tsv",
        "html", "htm", "css", "scss", "js", "jsx", "ts", "tsx", "kt", "kts", "java",
        "py", "c", "h", "cpp", "hpp", "cc", "cs", "go", "rs", "rb", "php", "swift",
        "sh", "bash", "zsh", "bat", "ps1", "sql", "r", "lua", "dart", "gradle",
        "toml", "ini", "cfg", "conf", "properties", "env", "log", "tex", "rtf", "svg",
        "dockerfile", "makefile", "gitignore"
    )

    fun classify(file: File): AttachmentKind {
        val ext = file.extension.lowercase()
        val nameLower = file.name.lowercase()
        return when {
            ext in imageExts -> AttachmentKind.IMAGE
            ext in audioExts -> AttachmentKind.AUDIO
            ext == "pdf" -> AttachmentKind.PDF
            ext in officeZipExts -> AttachmentKind.DOCUMENT
            ext in textCodeExts || nameLower in setOf("dockerfile", "makefile", "readme", "license") -> AttachmentKind.CODE_TEXT
            ext in videoExts -> AttachmentKind.VIDEO
            else -> if (looksLikeUtf8Text(file)) AttachmentKind.CODE_TEXT else AttachmentKind.BINARY
        }
    }

    fun analyze(file: File, maxTextChars: Int = 60_000): AnalyzedFile {
        if (!file.exists() || !file.isFile) {
            return AnalyzedFile(AttachmentKind.BINARY, "application/octet-stream", "[File not found: ${file.name}]")
        }
        val kind = classify(file)
        val ext = file.extension.lowercase()
        return when (kind) {
            AttachmentKind.IMAGE -> {
                val mime = when (ext) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"
                }
                val b64 = runCatching { Base64.getEncoder().encodeToString(file.readBytes()) }.getOrNull()
                AnalyzedFile(kind, mime, base64Data = b64)
            }

            AttachmentKind.AUDIO -> {
                val mime = when (ext) {
                    "wav" -> "audio/wav"
                    "mp3" -> "audio/mpeg"
                    "ogg" -> "audio/ogg"
                    "flac" -> "audio/flac"
                    else -> "audio/$ext"
                }
                val b64 = if (file.length() <= 20L * 1024L * 1024L) {
                    runCatching { Base64.getEncoder().encodeToString(file.readBytes()) }.getOrNull()
                } else null
                AnalyzedFile(
                    kind = kind,
                    mimeType = mime,
                    extractedText = "[Attached audio file: ${file.name} (${file.length() / 1024} KB)]",
                    base64Data = b64,
                    audioFormat = ext.ifBlank { "wav" }
                )
            }

            AttachmentKind.PDF -> {
                val text = extractPdfText(file, maxTextChars)
                AnalyzedFile(kind, "application/pdf", extractedText = text)
            }

            AttachmentKind.DOCUMENT -> {
                val text = extractZipDocumentText(file, ext, maxTextChars)
                AnalyzedFile(kind, "application/$ext", extractedText = text)
            }

            AttachmentKind.CODE_TEXT -> {
                val raw = runCatching {
                    val bytes = file.readBytes()
                    val limit = bytes.size.coerceAtMost(maxTextChars * 2)
                    String(bytes, 0, limit, StandardCharsets.UTF_8)
                }.getOrElse { "[Could not read ${file.name}: ${it.message}]" }
                val clipped = if (raw.length > maxTextChars) {
                    raw.take(maxTextChars) + "\n...[truncated at $maxTextChars chars]"
                } else raw
                AnalyzedFile(kind, "text/plain", extractedText = clipped)
            }

            AttachmentKind.VIDEO -> {
                AnalyzedFile(
                    kind = kind,
                    mimeType = "video/$ext",
                    extractedText = "[Attached video file: ${file.name} (${file.length() / (1024 * 1024)} MB)]"
                )
            }

            AttachmentKind.BINARY -> {
                AnalyzedFile(
                    kind = kind,
                    mimeType = "application/octet-stream",
                    extractedText = "[Attached binary file: ${file.name} (${file.length() / 1024} KB)]"
                )
            }
        }
    }

    private fun extractPdfText(file: File, maxChars: Int): String {
        return runCatching {
            Loader.loadPDF(file).use { doc ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(doc).trim()
                val pageCount = doc.numberOfPages
                if (text.isBlank()) {
                    "[PDF '${file.name}' ($pageCount pages) contains no selectable text — it may be scanned images.]"
                } else if (text.length > maxChars) {
                    "PDF (${file.name}, $pageCount pages):\n" + text.take(maxChars) + "\n...[truncated at $maxChars chars]"
                } else {
                    "PDF (${file.name}, $pageCount pages):\n$text"
                }
            }
        }.getOrElse { e ->
            "[Failed to read PDF '${file.name}': ${e.message}]"
        }
    }

    private fun extractZipDocumentText(file: File, ext: String, maxChars: Int): String {
        return runCatching {
            val sb = StringBuilder()
            ZipInputStream(file.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null && sb.length < maxChars) {
                    val n = entry.name.lowercase()
                    val interesting = when (ext) {
                        "docx" -> n == "word/document.xml" || n.startsWith("word/header") || n.startsWith("word/footer")
                        "pptx" -> n.startsWith("ppt/slides/slide") && n.endsWith(".xml")
                        "xlsx" -> n == "xl/sharedstrings.xml" || (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"))
                        "odt", "odp", "ods" -> n == "content.xml"
                        "epub" -> n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")
                        else -> n.endsWith(".xml") || n.endsWith(".txt")
                    }
                    if (!entry.isDirectory && interesting) {
                        val xml = String(zis.readBytes(), StandardCharsets.UTF_8)
                        val plain = stripXmlTags(xml)
                        if (plain.isNotBlank()) {
                            sb.append(plain).append("\n\n")
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            val result = sb.toString().trim()
            if (result.isBlank()) {
                "[Document '${file.name}' had no extractable text]"
            } else if (result.length > maxChars) {
                result.take(maxChars) + "\n...[truncated at $maxChars chars]"
            } else result
        }.getOrElse { e ->
            "[Failed to read ${ext.uppercase()} '${file.name}': ${e.message}]"
        }
    }

    private fun stripXmlTags(xml: String): String {
        return xml
            .replace(Regex("</(w:p|p|div|tr|li|text:p|a:p|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<(w:tab|tab)[^>]*/?>", RegexOption.IGNORE_CASE), "\t")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#10;", "\n")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun looksLikeUtf8Text(file: File): Boolean {
        if (file.length() == 0L || file.length() > 10L * 1024L * 1024L) return false
        return runCatching {
            val sample = ByteArray(file.length().coerceAtMost(2048).toInt())
            file.inputStream().use { it.read(sample) }
            val nulCount = sample.count { it == 0.toByte() }
            nulCount == 0
        }.getOrDefault(false)
    }
}
