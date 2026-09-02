package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 聊天区顶部居中的缓存命中率小胶囊（**流式布局**：占一行、水平居中，消息列表从它下方开始，
 * 不叠加/遮挡正文，也不随消息滚动穿行）。
 *
 * 无 usage 数据时不占位、不显示（只在 run 产生 usage 后出现）。
 */
@Composable
internal fun CacheHitRateHeader(usage: TokenUsageUi?) {
    if (usage == null) return
    val percent = (usage.cacheHitRate * 100).roundToInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
}
