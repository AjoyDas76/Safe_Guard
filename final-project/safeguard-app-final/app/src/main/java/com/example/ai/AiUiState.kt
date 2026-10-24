package com.example.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One bubble in the Ask SafeGuard chat. */
class ChatMsg(val fromUser: Boolean, val reply: ChatReply? = null, val text: String = "", val typing: Boolean = false)

/**
 * Process-lifetime UI state, so the chat history and the last analysis survive
 * switching between drawer screens (the Scaffold recreates the screen composables).
 */
object AiUiState {
    val chat = mutableStateListOf<ChatMsg>()
    var chatBusy by mutableStateOf(false)

    /** Set by the chat's "open full analysis" button; consumed once by the Analyze screen. */
    var pendingAnalyzeHours by mutableStateOf<Int?>(null)

    var lastAnalysis by mutableStateOf<AnalysisResult?>(null)
    var lastWindowKey by mutableStateOf("6h")
    var lastRunMs by mutableStateOf(0L)

    /**
     * Floating "Ask SafeGuard" button: how far (px) the user has dragged it from its default
     * bottom-right spot. Kept here so the position stays the same on every screen.
     */
    var fabX by mutableStateOf(0f)
    var fabY by mutableStateOf(0f)
}
