package io.github.mangi.eta.ui.screens.home

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
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.AgentModelPickerUiState

/**
 * AgentChatHome：首屏为聊天主舞台。
 *
 * 顶部入口统一由 [io.github.mangi.eta.ui.app.AgentAppShell] 提供，
 * 本 Screen 只负责消息流、Run trace、工具摘要和底部输入框。
 * 顶部居中显示当前 run 的缓存命中率。
 */
@Composable
internal fun AgentHomeScreen(
    state: AgentChatHomeUiState,
    modelPickerState: AgentModelPickerUiState,
    conversationKey: String?,
    onAction: (AgentHomeAction) -> Unit,
    isDrawerOpen: Boolean = false,
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
                onReasoningEffortChange = { onAction(AgentHomeAction.ReasoningEffortChanged(it)) },
                onModelSelected = { onAction(AgentHomeAction.ModelSelected(it)) },
                onVisionToggled = { providerId, modelId, vision ->
                    onAction(AgentHomeAction.VisionToggled(providerId, modelId, vision))
                },
                onSubmit = { text -> onAction(AgentHomeAction.SubmitMessage(text)) },
                onStop = { onAction(AgentHomeAction.StopRun) },
                onAttachImage = { uri -> onAction(AgentHomeAction.ImageAttached(uri)) },
                onRemoveImage = { id -> onAction(AgentHomeAction.RemoveImage(id)) },
                onAttachFiles = { uris -> onAction(AgentHomeAction.FilesAttached(uris)) },
                onAttachFolder = { uri -> onAction(AgentHomeAction.FolderAttached(uri)) },
                onAttachFilePath = { path -> onAction(AgentHomeAction.FilePathAttached(path)) },
                onRemoveFileReference = { id -> onAction(AgentHomeAction.RemoveFileReference(id)) },
                onEditMessage = { id -> onAction(AgentHomeAction.EditMessage(id)) },
                onCancelMessageEdit = { onAction(AgentHomeAction.CancelMessageEdit) },
                onDeleteMessage = { id -> onAction(AgentHomeAction.DeleteMessage(id)) },
                onRegenerateMessage = { id -> onAction(AgentHomeAction.RegenerateMessage(id)) },
                onSuggestionClick = { prompt ->
                    onAction(AgentHomeAction.SubmitMessage(prompt))
                },
                onRunTraceClick = { onAction(AgentHomeAction.ExpandRunTrace) },
                onOpenBrowser = { onAction(AgentHomeAction.OpenBrowser) },
                isDrawerOpen = isDrawerOpen,
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
