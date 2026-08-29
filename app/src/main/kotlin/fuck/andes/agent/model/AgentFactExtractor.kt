package fuck.andes.agent.model

/** 自动事实沉淀（P3）：从一次对话中提取长期稳定事实。默认关闭，测试可注入假实现。 */
internal fun interface AgentFactExtractor {
    suspend fun extractFacts(
        userText: String,
        assistantText: String,
    ): List<String>
}

/** 事实提取的后处理纯逻辑（可 kotlinc 单测）。 */
internal object AgentFactRules {

    const val MAX_FACTS_PER_RUN = 3
    const val MAX_FACT_CHARS = 400
    const val MAX_INPUT_CHARS = 4_000

    fun buildPrompt(userText: String, assistantText: String): String = buildString {
        appendLine("从以下对话中提取 0-3 条长期稳定的用户事实（名字、身份、偏好、关系、重要背景）。")
        appendLine("只提取明确陈述的稳定信息，不要推断；不要提取密钥、验证码或一次性信息。")
        appendLine("每行输出一条事实，以 \"- \" 开头。")
        appendLine()
        appendLine("用户：${userText.take(MAX_INPUT_CHARS)}")
        if (assistantText.isNotBlank()) {
            appendLine("助手：${assistantText.take(MAX_INPUT_CHARS)}")
        }
    }.trim()

    fun parseFacts(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("• ") }
            .map { it.removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.length >= 3 }
            .distinct()
            .take(MAX_FACTS_PER_RUN)
            .toList()

    /** 与 MEMORY.md 已有内容做行级双向包含去重 + 长度/数量预算。 */
    fun dedupeAndClamp(
        facts: List<String>,
        existingMemory: String,
        maxFacts: Int = MAX_FACTS_PER_RUN,
    ): List<String> {
        val memoryLines = existingMemory.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return facts
            .map { it.trim().take(MAX_FACT_CHARS) }
            .filter { it.length >= 3 }
            .filter { fact -> memoryLines.none { line -> fact in line || line in fact } }
            .distinct()
            .take(maxFacts)
    }
}

internal class LlmAgentFactExtractor(
    private val config: AgentModelClient.ModelConfig,
    private val provider: AgentProviderClient,
) : AgentFactExtractor {

    override suspend fun extractFacts(
        userText: String,
        assistantText: String,
    ): List<String> {
        val result = LlmTextCompletion.complete(
            config = config,
            provider = provider,
            systemPrompt = "你是记忆提取助手。",
            userText = AgentFactRules.buildPrompt(userText, assistantText),
            maxResultChars = 2_000,
        )
        return AgentFactRules.parseFacts(result)
    }
}
