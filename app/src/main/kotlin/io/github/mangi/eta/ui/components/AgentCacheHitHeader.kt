package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.TokenUsageUi
import kotlin.math.roundToInt

/** 取最近一条非空 usage（当前 run 的缓存命中来源）。只有 AgentMessageUi 才有 usage 字段。 */
internal fun latestUsage(messages: List<AgentChatMessageUi>): TokenUsageUi? =
    messages.asReversed().firstNotNullOfOrNull { message ->
        (message as? AgentMessageUi)?.usage?.takeIf { usage -> !usage.isEmpty }
    }

/**
 * 聊天页顶部居中的缓存命中率**悬浮小胶囊**（overlay，不占布局空间、不推挤/遮挡消息）。
 * 无 usage 数据时不显示（只在 run 产生 usage 后出现）。
 * 传入的 [modifier] 由调用方用 `BoxScope.align(TopCenter)` 定位；内部自带半透明底以提升可读性。
 */
@Composable
internal fun CacheHitRateHeader(
    usage: TokenUsageUi?,
    modifier: Modifier = Modifier,
) {
    if (usage == null) return
    val percent = (usage.cacheHitRate * 100).roundToInt()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "缓存命中 $percent%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
