package com.evatar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evatar.app.ui.theme.EvatarTypography

/**
 * A lightweight inline markdown renderer that parses a subset of markdown
 * into AnnotatedString spans.
 *
 * Supported syntax:
 *   **bold**
 *   *italic*
 *   `inline code`
 *   # Header / ## Header / ### Header
 *   - item / * item  (bullet lists)
 *   | col | col |    (basic tables)
 *   \n\n              (paragraph breaks)
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseStyle: TextStyle = EvatarTypography.body,
) {
    // Split into logical blocks separated by blank lines
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> EvatarTypography.title1
                        2 -> EvatarTypography.title2
                        else -> EvatarTypography.title3
                    }
                    Text(
                        text = buildAnnotatedString(block.text, baseStyle, color),
                        style = headerStyle,
                        color = color,
                    )
                    if (index < blocks.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
                is MdBlock.BulletList -> {
                    Column {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                                Text("• ", style = baseStyle, color = color)
                                Text(
                                    text = buildAnnotatedString(item, baseStyle, color),
                                    style = baseStyle,
                                    color = color,
                                )
                            }
                        }
                    }
                    if (index < blocks.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                }
                is MdBlock.Table -> {
                    TableBlock(block, baseStyle, color)
                    if (index < blocks.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedString(block.text, baseStyle, color),
                        style = baseStyle,
                        color = color,
                        lineHeight = 22.sp,
                    )
                    if (index < blocks.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// ── Block types ──

private sealed class MdBlock {
    data class Header(val level: Int, val text: String) : MdBlock()
    data class BulletList(val items: List<String>) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

// ── Block-level parsing ──

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraphs = text.split("\n\n")

    for (para in paragraphs) {
        val trimmed = para.trim()
        if (trimmed.isEmpty()) continue

        // Header: lines starting with #
        val headerLines = trimmed.lines().filter { it.trimStart().startsWith("# ") || it.trimStart().startsWith("## ") || it.trimStart().startsWith("### ") }
        if (headerLines.size == trimmed.lines().size && headerLines.isNotEmpty()) {
            for (line in headerLines) {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 3)
                blocks.add(MdBlock.Header(level, line.trimStart('#').trim()))
            }
            continue
        }

        // Table: lines starting with |
        val tableLines = trimmed.lines().filter { it.trim().startsWith("|") }
        if (tableLines.size >= 2 && tableLines.size == trimmed.lines().size) {
            val parsed = parseTable(tableLines)
            if (parsed != null) { blocks.add(parsed); continue }
        }

        // Bullet list: lines starting with - or *
        val bulletLines = trimmed.lines().filter { it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") }
        if (bulletLines.size == trimmed.lines().size && bulletLines.isNotEmpty()) {
            val items = bulletLines.map { it.trimStart().removePrefix("- ").removePrefix("* ").trim() }
            blocks.add(MdBlock.BulletList(items))
            continue
        }

        // Mixed content with headers/lists inside a paragraph -- split further
        val lines = trimmed.lines()
        var i = 0
        val currentPara = StringBuilder()
        while (i < lines.size) {
            val line = lines[i]
            val ls = line.trimStart()
            when {
                ls.startsWith("### ") -> {
                    flushParagraph(currentPara, blocks)
                    blocks.add(MdBlock.Header(3, ls.removePrefix("### ").trim()))
                }
                ls.startsWith("## ") -> {
                    flushParagraph(currentPara, blocks)
                    blocks.add(MdBlock.Header(2, ls.removePrefix("## ").trim()))
                }
                ls.startsWith("# ") -> {
                    flushParagraph(currentPara, blocks)
                    blocks.add(MdBlock.Header(1, ls.removePrefix("# ").trim()))
                }
                ls.startsWith("- ") || ls.startsWith("* ") -> {
                    flushParagraph(currentPara, blocks)
                    val items = mutableListOf(ls.removePrefix("- ").removePrefix("* ").trim())
                    while (i + 1 < lines.size) {
                        val next = lines[i + 1].trimStart()
                        if (next.startsWith("- ") || next.startsWith("* ")) {
                            items.add(next.removePrefix("- ").removePrefix("* ").trim())
                            i++
                        } else break
                    }
                    blocks.add(MdBlock.BulletList(items))
                }
                ls.startsWith("|") -> {
                    flushParagraph(currentPara, blocks)
                    val tableLines2 = mutableListOf(ls)
                    while (i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|")) {
                        i++
                        tableLines2.add(lines[i].trimStart())
                    }
                    val parsed = parseTable(tableLines2)
                    if (parsed != null) blocks.add(parsed)
                    else { currentPara.appendLine(tableLines2.joinToString("\n")) }
                }
                else -> {
                    if (currentPara.isNotEmpty()) currentPara.append("\n")
                    currentPara.append(line)
                }
            }
            i++
        }
        flushParagraph(currentPara, blocks)
    }
    return blocks
}

private fun flushParagraph(sb: StringBuilder, blocks: MutableList<MdBlock>) {
    val text = sb.toString().trim()
    if (text.isNotEmpty()) blocks.add(MdBlock.Paragraph(text))
    sb.clear()
}

private fun parseTable(lines: List<String>): MdBlock.Table? {
    if (lines.size < 2) return null
    fun parseRow(line: String): List<String> =
        line.trim().trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }

    val header = parseRow(lines[0])
    // Second row should be separator (e.g. |---|---|)
    val secondLine = lines[1].trim().replace(" ", "")
    if (!secondLine.matches(Regex("\\|[-:]+(\\|[-:]+)*\\|?"))) return null

    val rows = mutableListOf<List<String>>()
    for (i in 2 until lines.size) {
        rows.add(parseRow(lines[i]))
    }
    return MdBlock.Table(header, rows)
}

// ── Table rendering ──

@Composable
private fun TableBlock(block: MdBlock.Table, baseStyle: TextStyle, color: Color) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        // Header row
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            block.header.forEach { cell ->
                Text(
                    text = buildAnnotatedString(cell, baseStyle, color),
                    style = baseStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = color,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Data rows
        block.rows.forEach { row ->
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                row.forEach { cell ->
                    Text(
                        text = buildAnnotatedString(cell, baseStyle, color),
                        style = baseStyle,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── Inline parsing (bold, italic, inline code) ──

private fun buildAnnotatedString(text: String, baseStyle: TextStyle, color: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // Inline code: `...`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    val code = text.substring(i + 1, end)
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (baseStyle.fontSize.value - 1).sp,
                        background = color.copy(alpha = 0.08f),
                    )) {
                        append(" $code ")
                    }
                    i = end + 1
                    continue
                }
            }
            // Bold: **...**
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            // Italic: *...*  (but not **)
            if (text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1 && (end + 1 >= text.length || text[end + 1] != '*')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
