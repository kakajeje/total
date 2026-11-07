package com.kakajeje.meetingnotes

import com.kakajeje.meetingnotes.data.Minutes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinutesTest {
    private val sample = """
```markdown
회의ID: 20261107-143200
# 회의록: 4분기 마케팅 점검
- 일시: 2026-11-07 14:00
- 참석자: 김팀장, 이대리

## 한 줄 요약
- 4분기 캠페인 예산을 10% 늘리기로 했다.

## 할 일
- [ ] 이대리: 견적서 받기 (기한: 2026-11-12)

## 다음 일정
- **일시**: 2026-11-14 (금) 오후 2:30
- 장소: 본사 3층 회의실
- 담당자: 김팀장
- 안건:
  1. 견적 비교
  2. 일정 확정

## 미결 사항
- 없음
```
""".trimIndent()

    @Test fun parsesSample() {
        val m = Minutes.parse(sample)
        assertEquals("20261107-143200", m.meetingId)
        assertEquals("4분기 마케팅 점검", m.title)
        assertEquals("4분기 캠페인 예산을 10% 늘리기로 했다.", m.oneLine)
        assertEquals("회의 정보", m.sections.first().heading)
        val n = m.nextSchedule
        assertEquals("본사 3층 회의실", n.place)
        assertEquals("김팀장", n.owner)
        assertEquals(listOf("견적 비교", "일정 확정"), n.agenda)
        assertArrayEquals(intArrayOf(2026, 11, 14, 14, 30), n.parseDateTime())
        assertTrue(Minutes.looksLikeMinutes(sample))
        assertFalse(m.markdown.startsWith("```"))
    }

    @Test fun dates() {
        assertArrayEquals(intArrayOf(2026, 1, 5, 9, 0), Minutes.parseDateTime("2026-01-05 09:00"))
        assertArrayEquals(intArrayOf(2026, 12, 1, -1, -1), Minutes.parseDateTime("2026.12.1 (추정)"))
        assertArrayEquals(intArrayOf(2026, 3, 2, 15, 0), Minutes.parseDateTime("2026년 3월 2일 오후 3시"))
        assertNull(Minutes.parseDateTime("미정"))
    }

    @Test fun transcriptIsNotMinutes() {
        assertFalse(Minutes.looksLikeMinutes("화자 1: 안녕하세요\n화자 2: 네 오늘 요약부터 하죠"))
    }

    @Test fun plainText() {
        val t = Minutes.toPlainText(sample)
        assertFalse(t.contains("회의ID"))
        assertTrue(t.contains("■ 다음 일정"))
        assertTrue(t.contains("□ 이대리"))
    }
}
