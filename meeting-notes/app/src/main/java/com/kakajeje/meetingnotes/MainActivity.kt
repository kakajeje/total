package com.kakajeje.meetingnotes

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kakajeje.meetingnotes.data.Meeting
import com.kakajeje.meetingnotes.data.MeetingStore
import com.kakajeje.meetingnotes.data.Minutes
import com.kakajeje.meetingnotes.data.RecipientStore
import com.kakajeje.meetingnotes.data.TextImport
import com.kakajeje.meetingnotes.ui.AppTheme
import com.kakajeje.meetingnotes.ui.DetailScreen
import com.kakajeje.meetingnotes.ui.GuideScreen
import com.kakajeje.meetingnotes.ui.HomeScreen
import com.kakajeje.meetingnotes.ui.PickMeetingDialog
import com.kakajeje.meetingnotes.ui.RecipientsScreen

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data object Recipients : Screen
    data object Guide : Screen
}

class MainActivity : ComponentActivity() {

    /** 공유·열기로 들어온 내용. 화면이 받아서 처리한 뒤 비운다. */
    private val incoming = mutableStateOf<TextImport.Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppTheme {
                App(incoming.value) { incoming.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data: TextImport.Incoming? = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val fromFile = uri?.let { TextImport.read(this, it) }?.takeIf { it.text.isNotBlank() }
                fromFile ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
                    TextImport.Incoming(it.trim(), intent.getStringExtra(Intent.EXTRA_SUBJECT))
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { TextImport.read(this, it) }
            else -> null
        }
        if (data != null) incoming.value = data
        else if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            Actions.toast(this, "가져올 텍스트를 찾지 못했어요")
        }
    }
}

@Composable
private fun App(incoming: TextImport.Incoming?, consumeIncoming: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { MeetingStore(ctx) }
    val recipientStore = remember { RecipientStore(ctx) }
    var meetings by remember { mutableStateOf(store.all()) }
    var recipients by remember { mutableStateOf(recipientStore.all()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var pendingMinutes by remember { mutableStateOf<String?>(null) }
    // 파일 선택기가 '녹취록'용인지 특정 회의의 '정리본'용인지
    var pickTarget by remember { mutableStateOf<String?>(null) }

    fun refresh() { meetings = store.all() }

    fun saveMinutes(target: Meeting, md: String) {
        val clean = Minutes.clean(md)
        var m = target.copy(minutes = clean, minutesAt = System.currentTimeMillis())
        val uri = Actions.exportMinutes(ctx, m)
        if (uri != null) m = m.copy(exportedUri = uri)
        store.save(m)
        refresh()
        screen = Screen.Detail(m.id)
        Actions.toast(ctx, if (uri != null) "정리본을 저장했어요 (다운로드/${Actions.EXPORT_FOLDER})" else "정리본을 저장했어요")
    }

    fun newMeeting(text: String, fileName: String?): Meeting {
        val now = System.currentTimeMillis()
        var id = Meeting.newId(now)
        while (store.get(id) != null) id += "a"
        val m = Meeting(id, Meeting.guessTitle(text, fileName, now), now, text)
        store.save(m)
        refresh()
        screen = Screen.Detail(id)
        Actions.toast(ctx, "녹취록을 가져왔어요")
        return m
    }

    /** 들어온 텍스트가 정리본이면 회의ID로 해당 회의를 찾아 붙이고, 아니면 새 녹취록으로 만든다. */
    fun route(text: String, fileName: String?, forMeeting: String?) {
        if (forMeeting != null) {
            store.get(forMeeting)?.let { saveMinutes(it, text) }
            return
        }
        if (Minutes.looksLikeMinutes(text)) {
            val target = Minutes.extractId(text)?.let { store.get(it) }
            if (target != null) saveMinutes(target, text) else pendingMinutes = text
        } else {
            newMeeting(text, fileName)
        }
    }

    LaunchedEffect(incoming) {
        incoming?.let { route(it.text, it.fileName, null) }
        if (incoming != null) consumeIncoming()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pickTarget
        pickTarget = null
        if (uri == null) return@rememberLauncherForActivityResult
        val data = TextImport.read(ctx, uri)
        if (data == null || data.text.isBlank()) Actions.toast(ctx, "파일을 읽지 못했어요")
        else route(data.text, data.fileName, target)
    }
    val mimeTypes = arrayOf(
        "text/*", "application/octet-stream",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    fun clipboardText(): String? =
        ctx.getSystemService(ClipboardManager::class.java).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            meetings = meetings,
            onOpen = { screen = Screen.Detail(it.id) },
            onPickFile = { pickTarget = null; picker.launch(mimeTypes) },
            onPaste = {
                val t = clipboardText()
                if (t == null) Actions.toast(ctx, "클립보드가 비어 있어요") else route(t, null, null)
            },
            onRecipients = { screen = Screen.Recipients },
            onGuide = { screen = Screen.Guide },
        )
        is Screen.Detail -> {
            val m = meetings.firstOrNull { it.id == s.id }
            if (m == null) {
                LaunchedEffect(s.id) { screen = Screen.Home }
            } else {
                DetailScreen(
                    m = m,
                    recipients = recipients,
                    attachDefault = recipientStore.attachFile,
                    onBack = { screen = Screen.Home },
                    onSendToClaude = { asFile ->
                        Actions.shareToClaude(ctx, m, asFile)
                        store.save(m.copy(sentToClaudeAt = System.currentTimeMillis())); refresh()
                    },
                    onImportFile = { pickTarget = m.id; picker.launch(mimeTypes) },
                    onPasteMinutes = {
                        val t = clipboardText()
                        if (t == null) Actions.toast(ctx, "클립보드가 비어 있어요") else saveMinutes(m, t)
                    },
                    onRename = { store.save(m.copy(title = it)); refresh() },
                    onDelete = { store.delete(m.id); refresh(); screen = Screen.Home },
                    onSendMail = { to, attach ->
                        recipientStore.attachFile = attach
                        Actions.sendMail(ctx, m, to, attach)
                    },
                    onCalendar = { Actions.addToCalendar(ctx, m) },
                    onManageRecipients = { screen = Screen.Recipients },
                )
            }
        }
        Screen.Recipients -> RecipientsScreen(
            initial = recipients,
            onBack = { screen = Screen.Home },
            onSave = { recipientStore.saveAll(it); recipients = it },
        )
        Screen.Guide -> GuideScreen(onBack = { screen = Screen.Home })
    }

    pendingMinutes?.let { md ->
        PickMeetingDialog(
            meetings = meetings,
            onDismiss = { pendingMinutes = null },
            onPick = { target ->
                pendingMinutes = null
                if (target != null) {
                    saveMinutes(target, md)
                } else {
                    val p = Minutes.parse(md)
                    saveMinutes(newMeeting("(녹취록 없이 정리본만 가져옴)", p.title), md)
                }
            },
        )
    }
}
