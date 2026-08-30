package fuck.andes.agent.model

import fuck.andes.agent.runtime.AgentRunController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户附图的文字摘要生成器（图片记忆）。
 *
 * 持久化阶段 sanitizeContentArray 会剥离 image_url 并写入占位文字；本轮 run 结束后
 * 异步调用本生成器，用 LLM 将图片内容压缩为 ~200 字摘要，再通过
 * [AgentConversationCodec.replaceImagePlaceholder] 替换历史中的占位文字。
 * 后续轮次模型从摘要里回忆图片内容，无需重发原图。
 *
 * 失败时静默跳过，保留原占位文字不阻塞主流程。
 */
internal object AgentImageSummarizer {

    private const val MAX_SUMMARY_CHARS = 300

    private val SYSTEM_PROMPT = JSONObject()
        .put("role", "system")
        .put(
            "content",
            "你是一个图片内容摘要助手。用中文简要描述用户发送的图片关键内容（200字以内），" +
                "保留文字、布局、关键视觉信息、人物/对象描述，不要添加分析或建议。" +
                "只输出摘要本身，不要加前缀或标题。",
        )

    /**
     * 从持久化前的用户消息 contentJson 中提取 image_url 的 reference。
     *
     * 这里的 contentJson 尚未经过 sanitizeContentArray（或只有占位文字，
     * 需要从原始 images 参数获取）。实际图片 URL 通过 [AgentModelClient.ModelImage] 传入。
     */
    fun extractImageReferences(images: List<AgentModelClient.ModelImage>): List<String> =
        images.mapNotNull { img ->
            img.reference.takeIf { it.startsWith("data:image/", ignoreCase = true) }
        }

    /**
     * 用 LLM 将图片内容压缩为短文本摘要。
     *
     * @param config 当前模型配置
     * @param imageUrls data:image/... 格式的图片引用
     * @param userText 用户原始问题（给 LLM 一点上下文）
     * @return 摘要文本，失败或为空时返回 null
     */
    suspend fun summarize(
        config: AgentModelClient.ModelConfig,
        imageUrls: List<String>,
        userText: String,
    ): String? = withContext(Dispatchers.IO) {
        if (imageUrls.isEmpty()) return@withContext null

        runCatching {
            val images = imageUrls.map { url ->
                AgentModelClient.ModelImage(
                    reference = url,
                    mimeType = "image/png",
                    bytes = 0,
                )
            }

            val userContent = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", userText.take(500)))
                images.forEach { img ->
                    put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", img.reference))
                    )
                }
            }

            val messages = JSONArray()
                .put(SYSTEM_PROMPT)
                .put(JSONObject().put("role", "user").put("content", userContent))

            val request = ProviderRequest(
                config = config,
                messages = messages,
                tools = JSONArray(),
            )

            val text = StringBuilder()
            val provider = ProviderClientFactory.getClient(config)
            provider.complete(request, AgentRunController()) { event ->
                if (event is ProviderEvent.BlockDelta &&
                    event.kind == AssistantBlockKind.TEXT
                ) {
                    text.append(event.delta)
                }
            }

            text.toString().trim().take(MAX_SUMMARY_CHARS).ifBlank { null }
        }.getOrElse { throwable ->
            null
        }
    }
}
