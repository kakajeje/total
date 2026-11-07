@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kakajeje.meetingnotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kakajeje.meetingnotes.Actions
import com.kakajeje.meetingnotes.ClaudeGuide
import com.kakajeje.meetingnotes.data.Meeting
import com.kakajeje.meetingnotes.data.Recipient

/** 폴드를 펼쳤을 때 글줄이 너무 길어지지 않게 가운데 정렬하고 폭을 제한한다. */
@Composable
fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) { content() }
    }
}

// ───────────────────────── 홈 ─────────────────────────

@Composable
fun HomeScreen(
    meetings: List<Meeting>,
    onOpen: (Meeting) -> Unit,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
    onRecipients: () -> Unit,
    onGuide: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("회의노트") },
                actions = {
                    IconButton(onClick = onRecipients) { Icon(Icons.Default.People, "받는 사람") }
                    IconButton(onClick = onGuide) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Claude 설정 안내") }
                },
            )
        },
    ) { pad ->
        Centered(pad) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.FileOpen, null); Spacer(Modifier.padding(4.dp)); Text("파일 가져오기")
                        }
                        FilledTonalButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.padding(4.dp)); Text("붙여넣기")
                        }
                    }
                }
                if (meetings.isEmpty()) {
                    item { EmptyGuide(onGuide) }
                }
                items(meetings, key = { it.id }) { m -> MeetingCard(m) { onOpen(m) } }
            }
        }
    }
}

@Composable
private fun EmptyGuide(onGuide: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("이렇게 사용하세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("1. 삼성 '음성 녹음' 앱으로 회의를 녹음합니다.")
            Text("2. 녹음 파일 → 텍스트 변환(Galaxy AI)을 실행합니다.")
            Text("3. 변환된 텍스트를 '공유' → '회의노트'로 보냅니다.")
            Text("4. 회의노트에서 'Claude로 보내기' → 프로젝트에서 정리합니다.")
            Text("5. Claude 답변(또는 .md 파일)을 '공유' → '회의노트'로 가져옵니다.")
            Text("6. Gmail로 보내고, 다음 일정은 캘린더에 추가합니다.")
            TextButton(onClick = onGuide) { Text("처음 한 번: Claude 프로젝트 만들기 안내 보기") }
        }
    }
}

@Composable
private fun MeetingCard(m: Meeting, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.parsed?.title ?: m.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                StatusChip(m.status)
            }
            Text(Meeting.formatDate(m.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            val sub = m.parsed?.oneLine ?: m.transcript.take(120).replace('\n', ' ')
            Spacer(Modifier.height(4.dp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status) {
        "정리 완료" -> MaterialTheme.colorScheme.primary
        "Claude 정리 중" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Text(status, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

// ───────────────────────── 회의 상세 ─────────────────────────

@Composable
fun DetailScreen(
    m: Meeting,
    recipients: List<Recipient>,
    attachDefault: Boolean,
    onBack: () -> Unit,
    onSendToClaude: (asFile: Boolean) -> Unit,
    onImportFile: () -> Unit,
    onPasteMinutes: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSendMail: (List<String>, Boolean) -> Unit,
    onCalendar: () -> Unit,
    onManageRecipients: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable(m.id) { mutableIntStateOf(if (m.minutes != null) 0 else 1) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMail by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m.parsed?.title ?: m.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = { showRename = true }) { Icon(Icons.Default.Edit, "제목 수정") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, "삭제") }
                },
            )
        },
    ) { pad ->
        Centered(pad) {
            Column(Modifier.fillMaxSize()) {
                ActionPanel(
                    m, onSendToClaude, onImportFile, onPasteMinutes,
                    onMail = { showMail = true }, onCalendar = onCalendar,
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("정리본") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("녹취록") })
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    if (tab == 0) {
                        val md = m.minutes
                        if (md == null) {
                            Text(
                                "아직 정리본이 없습니다.\n'Claude로 보내기'로 녹취록을 프로젝트에 보내고, " +
                                    "정리된 답변이나 .md 파일을 '공유 → 회의노트'로 가져오세요.",
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else {
                            SelectionContainer { MarkdownView(md) }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "폰에 저장됨: 내 파일 › 다운로드 › ${Actions.EXPORT_FOLDER}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    } else {
                        TextButton(onClick = { Actions.copy(ctx, "녹취록", m.transcript); Actions.toast(ctx, "녹취록을 복사했어요") }) {
                            Text("녹취록 전체 복사")
                        }
                        SelectionContainer { Text(m.transcript, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }

    if (showRename) RenameDialog(m.title, onDismiss = { showRename = false }) { onRename(it); showRename = false }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("회의를 삭제할까요?") },
        text = { Text("앱 안의 녹취록과 정리본이 지워집니다. 다운로드 폴더에 저장된 파일은 남습니다.") },
        confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("취소") } },
    )
    if (showMail) MailDialog(
        recipients, attachDefault,
        onDismiss = { showMail = false },
        onManage = { showMail = false; onManageRecipients() },
    ) { to, attach -> showMail = false; onSendMail(to, attach) }
}

@Composable
private fun ActionPanel(
    m: Meeting,
    onSendToClaude: (Boolean) -> Unit,
    onImportFile: () -> Unit,
    onPasteMinutes: () -> Unit,
    onMail: () -> Unit,
    onCalendar: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "${Meeting.formatDate(m.createdAt)} · ${m.status} · 녹취 ${m.transcript.length}자",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (m.minutes == null) {
                Button(onClick = { onSendToClaude(true) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.padding(4.dp)); Text("Claude로 보내기")
                }
                OutlinedButton(onClick = { onSendToClaude(false) }) { Text("텍스트로 보내기") }
                FilledTonalButton(onClick = onImportFile) { Text("정리 파일 가져오기") }
                FilledTonalButton(onClick = onPasteMinutes) { Text("정리본 붙여넣기") }
            } else {
                Button(onClick = onMail) {
                    Icon(Icons.Default.Email, null); Spacer(Modifier.padding(4.dp)); Text("Gmail로 보내기")
                }
                if (m.parsed?.nextSchedule?.isEmpty == false) {
                    FilledTonalButton(onClick = onCalendar) {
                        Icon(Icons.Default.Event, null); Spacer(Modifier.padding(4.dp)); Text("다음 일정 캘린더에")
                    }
                }
                OutlinedButton(onClick = onImportFile) { Text("다시 가져오기") }
                OutlinedButton(onClick = onPasteMinutes) { Text("다시 붙여넣기") }
            }
        }
        m.parsed?.nextSchedule?.takeIf { !it.isEmpty }?.let { n ->
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("다음 일정", fontWeight = FontWeight.Bold)
                    if (n.dateTimeText.isNotBlank()) Text("일시: ${n.dateTimeText}")
                    if (n.place.isNotBlank()) Text("장소: ${n.place}")
                    if (n.owner.isNotBlank()) Text("담당자: ${n.owner}")
                    if (n.agenda.isNotEmpty()) Text("안건: " + n.agenda.joinToString(" / "))
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("회의 제목") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

@Composable
private fun MailDialog(
    recipients: List<Recipient>,
    attachDefault: Boolean,
    onDismiss: () -> Unit,
    onManage: () -> Unit,
    onSend: (List<String>, Boolean) -> Unit,
) {
    val checked = remember { mutableStateListOf<String>().apply { addAll(recipients.filter { it.byDefault }.map { it.email }) } }
    var extra by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(attachDefault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("받는 사람") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (recipients.isEmpty()) Text("저장된 받는 사람이 없습니다.", color = MaterialTheme.colorScheme.outline)
                recipients.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (r.email in checked) checked.remove(r.email) else checked.add(r.email)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(r.email in checked, onCheckedChange = {
                            if (it) checked.add(r.email) else checked.remove(r.email)
                        })
                        Column {
                            Text(r.name.ifBlank { r.email })
                            if (r.name.isNotBlank()) Text(r.email, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = onManage) { Text("받는 사람 관리") }
                OutlinedTextField(
                    extra, { extra = it }, label = { Text("추가 주소 (쉼표로 구분)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(attach, { attach = it })
                    Spacer(Modifier.padding(4.dp))
                    Text("정리본 .md 파일 첨부")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val more = extra.split(',', ';', ' ').map { it.trim() }.filter { EMAIL_REGEX.matches(it) }
                onSend((checked + more).distinct(), attach)
            }) { Text("Gmail 열기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ───────────────────────── 받는 사람 관리 ─────────────────────────

@Composable
fun RecipientsScreen(initial: List<Recipient>, onBack: () -> Unit, onSave: (List<Recipient>) -> Unit) {
    val list = remember { mutableStateListOf<Recipient>().apply { addAll(initial) } }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("받는 사람") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { pad ->
        Centered(pad) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("새로 추가", fontWeight = FontWeight.Bold)
                            OutlinedTextField(name, { name = it }, label = { Text("이름 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(
                                email, { email = it }, label = { Text("이메일") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = {
                                val e = email.trim()
                                if (!EMAIL_REGEX.matches(e)) { Actions.toast(ctx, "이메일 주소를 확인해 주세요"); return@Button }
                                list.removeAll { it.email.equals(e, true) }
                                list.add(Recipient(name.trim(), e, true))
                                onSave(list.toList()); name = ""; email = ""
                            }) { Text("추가") }
                        }
                    }
                }
                item {
                    Text(
                        "체크한 사람은 메일 보낼 때 기본으로 선택됩니다.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
                items(list.toList(), key = { it.email }) { r ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(r.byDefault, onCheckedChange = { v ->
                                val i = list.indexOfFirst { it.email == r.email }
                                if (i >= 0) { list[i] = r.copy(byDefault = v); onSave(list.toList()) }
                            })
                            Column(Modifier.weight(1f)) {
                                Text(r.name.ifBlank { r.email })
                                if (r.name.isNotBlank()) Text(r.email, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { list.removeAll { it.email == r.email }; onSave(list.toList()) }) {
                                Icon(Icons.Default.Delete, "삭제")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Claude 프로젝트 안내 ─────────────────────────

@Composable
fun GuideScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude 프로젝트 설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { pad ->
        Centered(pad) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("처음 한 번만 하면 됩니다", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("1. Claude(앱 또는 claude.ai)에서 프로젝트 → 새 프로젝트를 만들고 이름을 '${ClaudeGuide.PROJECT_NAME}'로 합니다.")
                Text("2. 프로젝트 '지침(Instructions)'에 아래 내용을 그대로 붙여 넣고 저장합니다.")
                Text("3. 설정 › 기능에서 '파일 생성' 기능이 켜져 있는지 확인합니다. (.md 파일로 받기 위해)")
                Text("4. 이후로는 회의마다 'Claude로 보내기' → 프로젝트 선택 → 붙여넣기(요청 문구가 복사되어 있음) → 보내기.")
                Text("5. 정리가 끝나면 답변의 공유 버튼이나 받은 .md 파일을 '회의노트'로 공유하면 자동으로 해당 회의에 붙습니다.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        Actions.copy(ctx, "프로젝트 지침", ClaudeGuide.PROJECT_INSTRUCTIONS)
                        Actions.toast(ctx, "지침을 복사했어요")
                    }) { Text("지침 복사") }
                    OutlinedButton(onClick = { Actions.openClaude(ctx) }) { Text("Claude 열기") }
                }
                AssistChip(onClick = {}, label = { Text("API 요금 없이 Claude 구독 사용량만 씁니다") })
                Card {
                    SelectionContainer {
                        Text(
                            ClaudeGuide.PROJECT_INSTRUCTIONS,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── 가져온 정리본을 어느 회의에 붙일지 ─────────────────────────

@Composable
fun PickMeetingDialog(
    meetings: List<Meeting>,
    onDismiss: () -> Unit,
    onPick: (Meeting?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어느 회의의 정리본인가요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "정리본에서 회의ID를 찾지 못했습니다. 연결할 회의를 고르세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                meetings.sortedBy { if (it.minutes == null) 0 else 1 }.take(15).forEach { m ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 8.dp)) {
                        Text(m.parsed?.title ?: m.title, fontWeight = FontWeight.SemiBold)
                        Text("${Meeting.formatDate(m.createdAt)} · ${m.status}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("새 회의로 저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
