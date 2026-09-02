package io.github.mangi.eta.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.chatConversationCompositionKey
import io.github.mangi.eta.ui.model.AgentChatAction
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.TokenUsageUi
import kotlin.math.roundToInt

/**
 * 独立对话页：与首页聊天主舞台共用同一套消息/输入组件，
 * 区别仅在于顶部返回由 Shell 统一提供。
 */
@Composable
internal fun AgentChatScreen(
    state: AgentChatUiState,
    modelPickerState: AgentModelPickerUiState,
    conversationKey: String?,
    onAction: (AgentChatAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    key(chatConversationCompositionKey(conversationKey)) {
        Column(modifier = modifier.fillMaxSize()) {
            CacheHitRateHeader(latestUsage(state.messages))
            AgentChatBody(
                messages = state.messages,
                modelPickerState = modelPickerState,
                input = state.input,
                isStreaming = state.isStreaming,
                reasoningEffort = state.reasoningEffort,
                availableReasoningEfforts = state.availableReasoningEfforts,
                pendingImages = state.pendingImages,
                pendingFileReferences = state.pendingFileReferences,
                messageEdit = state.messageEdit,
                onReasoningEffortChange = { onAction(AgentChatAction.ReasoningEffortChanged(it)) },
                onModelSelected = { onAction(AgentChatAction.ModelSelected(it)) },
                onVisionToggled = { providerId, modelId, vision ->
                    onAction(AgentChatAction.VisionToggled(providerId, modelId, vision))
                },
                onSubmit = { text -> onAction(AgentChatAction.SubmitMessage(text)) },
                onStop = { onAction(AgentChatAction.StopRun) },
                onAttachImage = { uri -> onAction(AgentChatAction.ImageAttached(uri)) },
                onRemoveImage = { id -> onAction(AgentChatAction.RemoveImage(id)) },
                onAttachFiles = { uris -> onAction(AgentChatAction.FilesAttached(uris)) },
                onAttachFolder = { uri -> onAction(AgentChatAction.FolderAttached(uri)) },
                onAttachFilePath = { path -> onAction(AgentChatAction.FilePathAttached(path)) },
                onRemoveFileReference = { id -> onAction(AgentChatAction.RemoveFileReference(id)) },
                onEditMessage = { id -> onAction(AgentChatAction.EditMessage(id)) },
                onCancelMessageEdit = { onAction(AgentChatAction.CancelMessageEdit) },
                onDeleteMessage = { id -> onAction(AgentChatAction.DeleteMessage(id)) },
                onRegenerateMessage = { id -> onAction(AgentChatAction.RegenerateMessage(id)) },
                onSuggestionClick = { prompt ->
                    onAction(AgentChatAction.SubmitMessage(prompt))
                },
                onRunTraceClick = { /* 对话页暂不做 Run trace 展开 */ },
                onOpenBrowser = { onAction(AgentChatAction.OpenBrowser) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 取最近一条非空 usage（当前 run 的缓存命中来源）。 */
private fun latestUsage(messages: List<AgentChatMessageUi>): TokenUsageUi? =
    messages.asReversed().firstNotNullOfOrNull { message ->
        message.usage?.takeIf { usage -> !usage.isEmpty }
    }

/** 聊天页顶部居中的缓存命中率指示。无 usage 时不占位。 */
@Composable
private fun CacheHitRateHeader(usage: TokenUsageUi?) {
    if (usage == null) return
    val percent = (usage.cacheHitRate * 100).roundToInt()
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "缓存命中 $percent%",
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}
