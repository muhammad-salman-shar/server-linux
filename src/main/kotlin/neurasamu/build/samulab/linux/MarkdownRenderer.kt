package neurasamu.build.samulab.linux

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    data class ListItem(val marker: String, val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Divider : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code blocks (supports ``` and ~~~, plus inline single-line ```code```)
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val fence = if (trimmed.startsWith("```")) "```" else "~~~"
            val afterOpen = trimmed.removePrefix(fence)
            val closingInSameLine = afterOpen.indexOf(fence)
            if (closingInSameLine > 0) {
                blocks.add(MdBlock.CodeBlock("", afterOpen.substring(0, closingInSameLine).trim()))
                i++
                continue
            }
            val lang = afterOpen.trim().substringBefore(" ").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith(fence)) i++
            blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        if (trimmed.startsWith("#### ")) {
            blocks.add(MdBlock.Heading(3, trimmed.removePrefix("#### ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("### ")) {
            blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("## ")) {
            blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            i++
            continue
        }
        if (trimmed.startsWith("# ")) {
            blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                i++
            }
            blocks.add(MdBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        if (trimmed.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim())) {
            val headers = splitTableRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(splitTableRow(lines[i].trim()))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks.add(MdBlock.ListItem("•", trimmed.substring(2).trim()))
            i++
            continue
        }

        val orderedMatch = Regex("""^(\d+\.)\s+(.*)$""").find(trimmed)
        if (orderedMatch != null) {
            blocks.add(MdBlock.ListItem(orderedMatch.groupValues[1], orderedMatch.groupValues[2]))
            i++
            continue
        }

        val paraLines = mutableListOf(line)
        i++
        while (i < lines.size) {
            val next = lines[i].trim()
            if (next.isEmpty() ||
                next.startsWith("```") ||
                next.startsWith("~~~") ||
                next.startsWith("# ") ||
                next.startsWith("## ") ||
                next.startsWith("### ") ||
                next.startsWith("#### ") ||
                next.startsWith(">") ||
                next.startsWith("- ") ||
                next.startsWith("* ") ||
                next == "---" ||
                (next.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim())) ||
                Regex("""^\d+\.\s+""").containsMatchIn(next)
            ) {
                break
            }
            paraLines.add(lines[i])
            i++
        }
        blocks.add(MdBlock.Paragraph(paraLines.joinToString("\n")))
    }

    return blocks
}

private fun isTableSeparator(line: String): Boolean {
    if (!line.startsWith("|")) return false
    val cleaned = line.replace("|", "").replace("-", "").replace(":", "").trim()
    return cleaned.isEmpty() && line.contains("-")
}

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun buildInlineAnnotated(
    text: String,
    palette: SaMuPalette,
    scale: Float
): AnnotatedString = buildAnnotatedString {
    val plainBuf = StringBuilder()

    fun flushPlain() {
        if (plainBuf.isNotEmpty()) {
            with(EmojiSupport) { appendWithEmojis(plainBuf.toString()) }
            plainBuf.setLength(0)
        }
    }

    var i = 0
    val n = text.length
    while (i < n) {
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flushPlain()
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (12f * scale).sp,
                        color = palette.codeText,
                        background = if (palette.isDark) Color(0x66000000) else Color(0xFF1E293B)
                    )
                )
                append(" ")
                append(text.substring(i + 1, end))
                append(" ")
                pop()
                i = end + 1
                continue
            }
        }

        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket > i && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen > closeBracket + 2) {
                    flushPlain()
                    val label = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(
                        SpanStyle(
                            color = palette.linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    with(EmojiSupport) { appendWithEmojis(label) }
                    pop()
                    pop()
                    i = closeParen + 1
                    continue
                }
            }
        }

        if (i + 2 < n && text[i] == '*' && text[i + 1] == '*' && text[i + 2] == '*') {
            val end = text.indexOf("***", i + 3)
            if (end > i + 3) {
                flushPlain()
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                append(buildInlineAnnotated(text.substring(i + 3, end), palette, scale))
                pop()
                i = end + 3
                continue
            }
        }

        if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flushPlain()
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(buildInlineAnnotated(text.substring(i + 2, end), palette, scale))
                pop()
                i = end + 2
                continue
            }
        }

        if (i + 1 < n && text[i] == '_' && text[i + 1] == '_') {
            val end = text.indexOf("__", i + 2)
            if (end > i + 2) {
                flushPlain()
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                append(buildInlineAnnotated(text.substring(i + 2, end), palette, scale))
                pop()
                i = end + 2
                continue
            }
        }

        if (text[i] == '*' && (i + 1 < n && !text[i + 1].isWhitespace())) {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1) {
                flushPlain()
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(buildInlineAnnotated(text.substring(i + 1, end), palette, scale))
                pop()
                i = end + 1
                continue
            }
        }

        plainBuf.append(text[i])
        i++
    }
    flushPlain()
}

@Composable
fun MarkdownText(
    markdown: String,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 17f
                        2 -> 16f
                        else -> 15f
                    } * scale
                    SelectionContainer {
                        Text(
                            text = buildInlineAnnotated(block.text, palette, scale),
                            fontSize = size.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            lineHeight = (size * 1.4f).sp
                        )
                    }
                }

                is MdBlock.CodeBlock -> {
                    FencedCodeCard(
                        language = block.language,
                        code = block.code,
                        onCopy = { onCopyCode(block.code) }
                    )
                }

                is MdBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(palette.primary, RoundedCornerShape(2.dp))
                        )
                        SelectionContainer {
                            Text(
                                text = buildInlineAnnotated(block.text, palette, scale),
                                fontSize = (13.5f * scale).sp,
                                fontStyle = FontStyle.Italic,
                                color = palette.textSecondary,
                                lineHeight = (13.5f * scale * 1.5f).sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                is MdBlock.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.marker,
                            fontSize = (14f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.primary,
                            lineHeight = (14f * scale * 1.55f).sp
                        )
                        InlineClickableText(
                            text = block.text,
                            palette = palette,
                            scale = scale,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(palette.divider)
                    )
                }

                is MdBlock.Table -> {
                    MarkdownTable(block.headers, block.rows, palette, scale)
                }

                is MdBlock.Paragraph -> {
                    InlineClickableText(
                        text = block.text,
                        palette = palette,
                        scale = scale
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InlineClickableText(
    text: String,
    palette: SaMuPalette,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, palette, scale) { buildInlineAnnotated(text, palette, scale) }
    val baseSize = (14f * scale).sp
    val baseLine = (14f * scale * 1.55f).sp

    ClickableText(
        text = annotated,
        style = TextStyle(
            fontSize = baseSize,
            lineHeight = baseLine,
            color = palette.onSurface
        ),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { ann ->
                    runCatching {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(URI(ann.item))
                        }
                    }
                }
        }
    )
}

@Composable
private fun FencedCodeCard(
    language: String,
    code: String,
    onCopy: () -> Unit
) {
    val palette = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val scope = rememberCoroutineScope()
    val hScroll = rememberScrollState()
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.codeBg)
            .border(1.dp, palette.surfaceVariant, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(palette.surfaceVariant)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" },
                fontSize = (11f * scale).sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        onCopy()
                        copied = true
                        scope.launch {
                            delay(1500)
                            copied = false
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) palette.success else palette.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (copied) "Copied" else "Copy",
                    fontSize = (11f * scale).sp,
                    color = if (copied) palette.success else palette.textSecondary
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScroll)
                        .padding(10.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (12f * scale).sp,
                        lineHeight = (12f * scale * 1.45f).sp,
                        color = palette.codeText
                    )
                }
            }
            if (hScroll.maxValue > 0) {
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(hScroll),
                    style = samuScrollbarStyle(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    palette: SaMuPalette,
    scale: Float
) {
    val hScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, palette.divider, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
            Row(modifier = Modifier.background(palette.surfaceVariant)) {
                headers.forEach { cell ->
                    Text(
                        text = EmojiSupport.format(cell),
                        fontSize = (12f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface,
                        modifier = Modifier
                            .width(140.dp)
                            .border(0.5.dp, palette.divider)
                            .padding(8.dp)
                    )
                }
            }
            rows.forEach { row ->
                Row {
                    val cols = maxOf(headers.size, row.size)
                    for (c in 0 until cols) {
                        Text(
                            text = EmojiSupport.format(row.getOrElse(c) { "" }),
                            fontSize = (12f * scale).sp,
                            color = palette.onSurface,
                            modifier = Modifier
                                .width(140.dp)
                                .border(0.5.dp, palette.divider)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
        if (hScroll.maxValue > 0) {
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(hScroll),
                style = samuScrollbarStyle(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}
