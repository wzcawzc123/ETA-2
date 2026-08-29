package fuck.andes.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val memoryEnabled: Boolean = true,
    /** 长对话滚动摘要（P2）：被裁剪轮次压缩为摘要注入，默认开启。 */
    val conversationSummaryEnabled: Boolean = true,
    /** 自动事实沉淀（P3）：从对话提取稳定事实写入 MEMORY.md，成本敏感，默认关闭。 */
    val factDistillEnabled: Boolean = false,
    /** 向 Anthropic 系端点注入 cache_control 断点（需端点支持），默认关闭。 */
    val promptCacheEnabled: Boolean = false,
    val appearance: AppearanceSettings = AppearanceSettings(),
)
