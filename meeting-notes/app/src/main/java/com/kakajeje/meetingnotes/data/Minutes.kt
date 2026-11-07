package com.kakajeje.meetingnotes.data

/**
 * Claude 프로젝트가 돌려준 정리본(마크다운)을 읽어 화면·메일·캘린더에 쓸 수 있게 나눈다.
 * 형식은 ClaudeGuide.PROJECT_INSTRUCTIONS 에 정의되어 있다. Claude가 형식을 조금 어겨도
 * 최대한 읽어 내고, 못 읽은 부분은 원문 그대로 보여 준다.
 */
data class NextSchedule(
    val dateTimeText: String = "",
    val place: String = "",
    val owner: String = "",
    val attendees: String = "",
    val agenda: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = dateTimeText.isBlank() && place.isBlank() && owner.isBlank() && agenda.isEmpty()

    /** "2026-11-14 14:00" 같은 표기를 (년,월,일,시,분)으로. 시각이 없으면 시·분은 null. */
    fun parseDateTime(): IntArray? = Minutes.parseDateTime(dateTimeText)
}

data class Section(val heading: String, val lines: List<String>)

data class Minutes(
    val meetingId: String?,
    val title: String?,
    val sections: List<Section>,
    val nextSchedule: NextSchedule,
    val markdown: String,
) {
    fun section(vararg names: String): Section? =
        sections.firstOrNull { s -> names.any { n -> s.heading.replace(" ", "").contains(n.replace(" ", "")) } }

    val oneLine: String?
        get() = section("한 줄 요약", "한줄요약")?.lines?.firstOrNull { it.isNotBlank() }?.let { stripBullet(it) }

    companion object {
        private val ID_REGEX = Regex("""회의\s*ID\s*[:：]\s*\[?([A-Za-z0-9_-]+)\]?""")
        private val DATE_REGEX =
            Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})\s*[-./월]\s*(\d{1,2})\s*일?(?:\s*\([^)]*\))?(?:\s*(오전|오후)?\s*(\d{1,2})\s*[:시]\s*(\d{1,2})?)?""")

        fun looksLikeMinutes(text: String): Boolean {
            val t = text.trim()
            return ID_REGEX.containsMatchIn(t) ||
                (t.contains("## ") && (t.contains("요약") || t.contains("다음 일정") || t.contains("할 일")))
        }

        fun extractId(text: String): String? = ID_REGEX.find(text)?.groupValues?.get(1)

        fun parseDateTime(text: String): IntArray? {
            val m = DATE_REGEX.find(text) ?: return null
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            if (mo !in 1..12 || d !in 1..31) return null
            val ampm = m.groupValues[4]
            val hRaw = m.groupValues[5]
            if (hRaw.isEmpty()) return intArrayOf(y, mo, d, -1, -1)
            var h = hRaw.toInt()
            if (ampm == "오후" && h < 12) h += 12
            if (ampm == "오전" && h == 12) h = 0
            val min = m.groupValues[6].ifEmpty { "0" }.toInt()
            if (h !in 0..23 || min !in 0..59) return intArrayOf(y, mo, d, -1, -1)
            return intArrayOf(y, mo, d, h, min)
        }

        fun stripBullet(line: String): String =
            line.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim()

        /** Claude가 ```markdown 코드 블록으로 감싸 보내는 경우가 있어서 벗겨 낸다. */
        fun clean(raw: String): String {
            var t = raw.replace("\r\n", "\n").trim()
            val fence = Regex("""^```[a-zA-Z]*\n([\s\S]*?)\n```\s*$""")
            fence.find(t)?.let { t = it.groupValues[1].trim() }
            return t
        }

        fun parse(raw: String): Minutes {
            val md = clean(raw)
            var title: String? = null
            val sections = mutableListOf<Section>()
            var curHeading: String? = null
            var cur = mutableListOf<String>()
            val preamble = mutableListOf<String>()

            for (line in md.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("## ") || (trimmed.startsWith("### ") && curHeading == null) -> {
                        if (curHeading != null) sections += Section(curHeading, cur.trimBlank())
                        curHeading = trimmed.trimStart('#').trim()
                        cur = mutableListOf()
                    }
                    trimmed.startsWith("# ") && title == null -> title = trimmed.removePrefix("# ").trim()
                    curHeading == null -> preamble += line
                    else -> cur += line
                }
            }
            if (curHeading != null) sections += Section(curHeading, cur.trimBlank())

            val info = preamble.filterNot { ID_REGEX.containsMatchIn(it) }.trimBlank()
            val all = if (info.any { it.isNotBlank() }) listOf(Section("회의 정보", info)) + sections else sections

            val next = all.firstOrNull { it.heading.replace(" ", "").contains("다음일정") }
            return Minutes(
                meetingId = extractId(md),
                title = title?.replace(Regex("""^회의록\s*[:：]\s*"""), ""),
                sections = all,
                nextSchedule = next?.let { parseSchedule(it.lines) } ?: NextSchedule(),
                markdown = md,
            )
        }

        private fun parseSchedule(lines: List<String>): NextSchedule {
            var date = ""; var place = ""; var owner = ""; var who = ""
            val agenda = mutableListOf<String>()
            var inAgenda = false
            for (l in lines) {
                val s = stripBullet(l).replace("**", "")
                if (s.isBlank()) continue
                val kv = Regex("""^([^:：]{1,12})\s*[:：]\s*(.*)$""").find(s)
                val key = kv?.groupValues?.get(1)?.replace(" ", "") ?: ""
                val value = kv?.groupValues?.get(2)?.trim() ?: ""
                when {
                    kv != null && (key.contains("일시") || key == "날짜" || key == "시간") -> { date = value; inAgenda = false }
                    kv != null && key.contains("장소") -> { place = value; inAgenda = false }
                    kv != null && key.contains("담당") -> { owner = value; inAgenda = false }
                    kv != null && key.contains("참석") -> { who = value; inAgenda = false }
                    kv != null && key.contains("안건") -> {
                        inAgenda = true
                        if (value.isNotBlank()) agenda += value
                    }
                    inAgenda -> agenda += s.replace(Regex("""^\d+[.)]\s*"""), "")
                }
            }
            return NextSchedule(date, place, owner, who, agenda)
        }

        private fun List<String>.trimBlank(): List<String> =
            dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

        /** 메일 본문용: 마크다운 기호를 걷어 낸 읽기 쉬운 텍스트. */
        fun toPlainText(md: String): String = clean(md).lines()
            .filterNot { ID_REGEX.containsMatchIn(it) && it.trim().length < 40 }
            .joinToString("\n") { line ->
                val t = line.trimEnd()
                when {
                    t.trim().startsWith("## ") -> "\n■ " + t.trim().removePrefix("## ").trim()
                    t.trim().startsWith("### ") -> "▶ " + t.trim().removePrefix("### ").trim()
                    t.trim().startsWith("# ") -> "[" + t.trim().removePrefix("# ").trim() + "]"
                    t.trim().startsWith("- [ ] ") -> t.replaceFirst("- [ ] ", "□ ")
                    t.trimStart().startsWith("- ") -> t.replaceFirst("- ", "· ")
                    else -> t
                }.replace("**", "")
            }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
