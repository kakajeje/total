package com.kakajeje.meetingnotes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.CalendarContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kakajeje.meetingnotes.data.Meeting
import com.kakajeje.meetingnotes.data.Minutes
import java.io.File
import java.util.Calendar

object Actions {
    const val CLAUDE_PACKAGE = "com.anthropic.claude"
    const val GMAIL_PACKAGE = "com.google.android.gm"
    const val EXPORT_FOLDER = "회의노트"

    private fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(40)

    private fun cacheFile(context: Context, name: String, content: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name).apply { writeText(content) }
        return FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    /** 녹취록을 .txt 파일로 만들어 Claude 앱에 넘긴다. 요청 문구는 클립보드에도 복사. */
    fun shareToClaude(context: Context, m: Meeting, asFile: Boolean) {
        val body = ClaudeGuide.transcriptFile(m.id, m.title, Meeting.formatDate(m.createdAt), m.transcript)
        val request = ClaudeGuide.requestMessage(m.id)
        copy(context, "Claude 요청", request)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (asFile) {
                val uri = cacheFile(context, "회의녹취_${m.id}.txt", body)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, request)
                clipData = ClipData.newRawUri("녹취록", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, request + "\n\n" + body)
            }
        }
        launch(context, intent, CLAUDE_PACKAGE, "Claude로 보내기")
        toast(context, "Claude에서 '${ClaudeGuide.PROJECT_NAME}' 프로젝트를 고르세요. 요청 문구는 복사되어 있어요.")
    }

    fun openClaude(context: Context) {
        val i = context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/projects"))
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Gmail 앱에 받는 사람·제목·본문을 채워 연다. 보내기는 사용자가 누른다. */
    fun sendMail(context: Context, m: Meeting, to: List<String>, attach: Boolean) {
        val md = m.minutes ?: return
        val subject = "[회의록] ${m.parsed?.title ?: m.title} (${Meeting.formatDate(m.createdAt).substringBefore(" (")})"
        val body = Minutes.toPlainText(md) + "\n\n— 회의노트 앱에서 보냄"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (attach) {
                val uri = cacheFile(context, "회의록_${m.id}_${safeName(m.title)}.md", md)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("회의록", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        launch(context, intent, GMAIL_PACKAGE, "메일 보내기")
    }

    /** 다음 일정을 캘린더(삼성 캘린더/구글 캘린더) 새 일정 화면으로 넘긴다. */
    fun addToCalendar(context: Context, m: Meeting) {
        val next = m.parsed?.nextSchedule ?: return
        val dt = next.parseDateTime()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            val title = next.agenda.firstOrNull()?.let { "회의: $it" } ?: "다음 회의 (${m.title})"
            putExtra(CalendarContract.Events.TITLE, title)
            if (next.place.isNotBlank() && next.place != "미정") putExtra(CalendarContract.Events.EVENT_LOCATION, next.place)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                buildString {
                    if (next.owner.isNotBlank()) appendLine("담당자: ${next.owner}")
                    if (next.attendees.isNotBlank()) appendLine("참석자: ${next.attendees}")
                    if (next.agenda.isNotEmpty()) {
                        appendLine("안건:")
                        next.agenda.forEachIndexed { i, a -> appendLine("${i + 1}. $a") }
                    }
                    append("\n이전 회의: ${m.title}")
                },
            )
            if (dt != null) {
                val cal = Calendar.getInstance().apply {
                    clear()
                    if (dt[3] >= 0) set(dt[0], dt[1] - 1, dt[2], dt[3], dt[4]) else set(dt[0], dt[1] - 1, dt[2])
                }
                if (dt[3] >= 0) {
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 60 * 60 * 1000)
                } else {
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                }
            }
        }
        runCatching { context.startActivity(intent) }.onFailure { toast(context, "캘린더 앱을 찾지 못했어요") }
    }

    /**
     * 정리본을 '내 파일 > 다운로드 > 회의노트' 폴더에 .md 로 저장한다. 이미 저장한 적이 있으면 덮어쓴다.
     * 저장된 파일의 주소를 돌려준다.
     */
    fun exportMinutes(context: Context, m: Meeting): String? {
        val md = m.minutes ?: return null
        val resolver = context.contentResolver
        m.exportedUri?.let { old ->
            val ok = runCatching {
                resolver.openOutputStream(Uri.parse(old), "wt")?.use { it.write(md.toByteArray()) } != null
            }.getOrDefault(false)
            if (ok) return old
        }
        val name = "회의록_${m.id.substringBefore('-')}_${safeName(m.parsed?.title ?: m.title)}.md"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_FOLDER)
        }
        return runCatching {
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) }
            uri.toString()
        }.getOrNull()
    }

    private fun launch(context: Context, intent: Intent, preferred: String, chooserTitle: String) {
        if (isInstalled(context, preferred)) {
            val direct = Intent(intent).setPackage(preferred)
            if (runCatching { context.startActivity(direct) }.isSuccess) return
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
