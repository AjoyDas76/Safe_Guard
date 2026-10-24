package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiUiState
import com.example.ai.AskEngine
import com.example.ai.ChatAction
import com.example.ai.ChatMsg
import com.example.ai.ChatReply
import com.example.ai.FirebaseAiDataSource
import com.example.ui.components.webGlass
import com.example.ui.components.webTextMain
import com.example.ui.components.webTextMuted
import com.example.ui.theme.CommandNavyDark
import com.example.ui.theme.CyanNeon
import kotlinx.coroutines.launch

private val CHIPS_BN = listOf(
    "আজকে কয়টা অ্যালার্ট হয়েছে?", "ওয়ার্কারের অবস্থা এখন কেমন?",
    "অস্বাভাবিক কিছু আছে?", "তাপমাত্রা কত?", "আজকের সামারি"
)

/** Turns **bold** markup into an AnnotatedString. */
private fun markup(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p)
    }
}

private fun hasBangla(s: String) = s.any { it in '\u0980'..'\u09FF' }

/**
 * "Ask SafeGuard" - same questions and answers as the website's chat widget.
 * Runs fully on the phone (no API key, no cost); numbers come from real telemetry/logs.
 */
@Composable
fun AiAssistantScreen(
    onOpenAnalysis: (hours: Int) -> Unit,
    /** Non-null when shown inside the floating panel: adds a close (X) button to the header. */
    onClose: (() -> Unit)? = null,
    /** The full screen handles the keyboard itself; the floating panel's host does it instead. */
    handleIme: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    var input by remember { mutableStateOf("") }
    val msgs = AiUiState.chat

    LaunchedEffect(Unit) {
        if (msgs.isEmpty()) msgs.add(ChatMsg(fromUser = false, reply = AskEngine(FirebaseAiDataSource, true).welcome()))
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    fun send(raw: String) {
        val q = raw.trim()
        if (q.isEmpty() || AiUiState.chatBusy) return
        input = ""
        msgs.add(ChatMsg(fromUser = true, text = q))
        val typing = ChatMsg(fromUser = false, typing = true)
        msgs.add(typing)
        AiUiState.chatBusy = true
        scope.launch {
            val bn = hasBangla(q)
            val reply: ChatReply = try {
                AskEngine(FirebaseAiDataSource, bn).answer(q)
            } catch (e: Exception) {
                val m = e.message ?: ""
                val txt = when {
                    m.contains("PERMISSION_DENIED", true) || m.startsWith("-3") ->
                        if (bn) "ডাটাবেজ পড়ার অনুমতি পাওয়া যায়নি — লগইন/Firebase Rules দেখুন।" else "Database read permission denied — check login / Firebase rules."
                    else -> (if (bn) "ডেটা আনতে সমস্যা হয়েছে: " else "Could not load data: ") + m
                }
                ChatReply(listOf(com.example.ai.ChatLine("⚠️ $txt")))
            }
            msgs.remove(typing)
            msgs.add(ChatMsg(fromUser = false, reply = reply))
            AiUiState.chatBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (handleIme) Modifier.imePadding() else Modifier)
            .padding(horizontal = 10.dp)
    ) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(CyanNeon.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(22.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Ask SafeGuard", fontWeight = FontWeight.Black, fontSize = 16.sp, color = webTextMain(isDark))
                Text("Local AI engine · live data", fontSize = 11.sp, color = webTextMuted(isDark))
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close chat", tint = webTextMuted(isDark))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(msgs) { m ->
                if (m.fromUser) UserBubble(m.text, isDark)
                else BotBubble(m, isDark,
                    onAction = { a ->
                        when (a.kind) {
                            ChatAction.OPEN_ANALYZE -> onOpenAnalysis(a.hours)
                            ChatAction.OPEN_MAPS -> runCatching { uriHandler.openUri(a.url) }
                        }
                    })
            }
        }

        // suggestion chips
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(CHIPS_BN) { c ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable(enabled = !AiUiState.chatBusy) { send(c) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text(c, fontSize = 12.sp, color = webTextMain(isDark)) }
            }
        }

        // input
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 300) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("প্রশ্ন লিখুন… / Ask a question…", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            FilledIconButton(
                onClick = { send(input) },
                enabled = input.isNotBlank() && !AiUiState.chatBusy
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun UserBubble(text: String, isDark: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(CyanNeon.copy(alpha = if (isDark) 0.22f else 0.18f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Text(text, fontSize = 14.sp, color = webTextMain(isDark)) }
    }
}

@Composable
private fun BotBubble(m: ChatMsg, isDark: Boolean, onAction: (ChatAction) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .webGlass(isDark, shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), topLine = false)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (m.typing) {
                Text("…", fontSize = 18.sp, color = webTextMuted(isDark))
            } else {
                m.reply?.lines?.forEach { l ->
                    if (l.text.isEmpty()) Spacer(Modifier.height(4.dp))
                    else Text(
                        markup(l.text),
                        fontSize = if (l.small) 11.sp else 14.sp,
                        color = if (l.small) webTextMuted(isDark) else webTextMain(isDark)
                    )
                }
                m.reply?.actions?.forEach { a ->
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onAction(a) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text(a.label, fontSize = 12.sp) }
                }
            }
        }
    }
}
