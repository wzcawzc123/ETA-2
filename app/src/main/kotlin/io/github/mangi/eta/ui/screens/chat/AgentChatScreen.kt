package io.github.mangi.eta.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.CacheHitRateHeader
import io.github.mangi.eta.ui.components.chatConversationCompositionKey
import io.github.mangi.eta.ui.components.latestUsage
import io.github.mangi.eta.ui.model.AgentChatAction
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.AgentModelPickerUiState

/**
 * 独立对话页：与首页聊天主舞台共用同一套消息/输入组件，
 * 区别仅在于顶部返回由 Shell 统一提供。顶部居中显示当前 run 的缓存命中率。
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
        Box(modifier = modifier.fillMaxSize()) {
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
                modifier = Modifier.fillMaxSize(),
            )
            // 顶部居中悬浮胶囊：不占布局空间、不推挤消息列表。
            CacheHitRateHeader(
                usage = latestUsage(state.messages),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }
    }
}
