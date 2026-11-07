package com.kakajeje.meetingnotes.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Meeting(
    val id: String,
    val title: String,
    val createdAt: Long,
    val transcript: String,
    val minutes: String? = null,
    val minutesAt: Long? = null,
    val sentToClaudeAt: Long? = null,
    val exportedUri: String? = null,
) {
    val parsed: Minutes? by lazy { minutes?.let { Minutes.parse(it) } }

    val status: String
        get() = when {
            minutes != null -> "정리 완료"
            sentToClaudeAt != null -> "Claude 정리 중"
            else -> "정리 대기"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("createdAt", createdAt)
        put("transcript", transcript)
        minutes?.let { put("minutes", it) }
        minutesAt?.let { put("minutesAt", it) }
        sentToClaudeAt?.let { put("sentToClaudeAt", it) }
        exportedUri?.let { put("exportedUri", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = Meeting(
            id = o.getString("id"),
            title = o.optString("title", "회의"),
            createdAt = o.optLong("createdAt"),
            transcript = o.optString("transcript", ""),
            minutes = o.optString("minutes").ifEmpty { null },
            minutesAt = if (o.has("minutesAt")) o.getLong("minutesAt") else null,
            sentToClaudeAt = if (o.has("sentToClaudeAt")) o.getLong("sentToClaudeAt") else null,
            exportedUri = o.optString("exportedUri").ifEmpty { null },
        )

        fun newId(now: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.KOREA).format(Date(now))

        fun formatDate(ms: Long): String =
            SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.KOREA).format(Date(ms))

        /** 녹취 텍스트의 첫 줄이나 파일 이름에서 제목을 짐작한다. */
        fun guessTitle(text: String, fileName: String?, now: Long): String {
            fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() && it.length <= 40 }?.let { return it }
            val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: ""
            return if (first.length in 2..30 && !first.contains(":")) first
            else "회의 " + SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(now))
        }
    }
}
