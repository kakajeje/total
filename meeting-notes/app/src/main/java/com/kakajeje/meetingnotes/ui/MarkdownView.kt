package com.kakajeje.meetingnotes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 회의록에 쓰는 정도의 간단한 마크다운(제목·목록·체크박스·표)만 그린다. */
@Composable
fun MarkdownView(md: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            val t = line.trim()
            val indent = (line.length - line.trimStart().length).coerceAtMost(8) * 4
            val clean = t.replace("**", "")
            when {
                t.isEmpty() -> Spacer(Modifier.height(6.dp))
                t.startsWith("회의ID") -> Unit
                t.startsWith("# ") -> Text(
                    clean.removePrefix("# "), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp),
                )
                t.startsWith("## ") -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        clean.removePrefix("## "), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                t.startsWith("### ") -> Text(
                    clean.removePrefix("### "), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
                )
                t.matches(Regex("""^\|?\s*:?-{2,}.*""")) -> Unit // 표 구분선
                t.startsWith("|") -> Text(
                    clean.trim('|').split("|").joinToString("  ·  ") { it.trim() },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = indent.dp, top = 2.dp),
                )
                t.startsWith("- [ ] ") || t.startsWith("- [x] ") || t.startsWith("- [X] ") ->
                    Bullet(if (t[3] == ' ') "☐" else "☑", clean.substring(6), indent)
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ") -> Bullet("•", clean.substring(2), indent)
                Regex("""^\d+[.)]\s+.*""").matches(t) -> {
                    val num = t.substringBefore(' ')
                    Bullet(num, clean.substringAfter(' '), indent)
                }
                else -> Text(clean, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = indent.dp))
            }
        }
    }
}

@Composable
private fun Bullet(mark: String, text: String, indent: Int) {
    Row(Modifier.padding(start = indent.dp, top = 2.dp, bottom = 2.dp)) {
        Text(mark, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
