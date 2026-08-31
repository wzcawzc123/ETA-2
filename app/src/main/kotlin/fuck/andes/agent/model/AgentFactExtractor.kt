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

    /** 明显是"未提取到任何事实"的负向结论模板，不应沉淀进记忆库。 */
    private val NEGATIVE_FACTS = listOf(
        "暂无长期稳定事实",
        "无长期稳定",
        "无可用事实",
        "无事实",
        "没有提取到明确陈述的用户事实",
        "没有提取到明确",
        "未提取到",
        "没有明确陈述",
        "没有明确",
        "不存在",
        "无法提取",
        "没有发现",
        "无信息",
        "无长期",
        "无可用",
    )

    fun parseFacts(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("• ") }
            .map { it.removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.length >= 3 }
            .filter { fact -> NEGATIVE_FACTS.none { fact.contains(it) } }
            .distinct()
            .take(MAX_FACTS_PER_RUN)
            .toList()

    /** 与 MEMORY.md 已有内容做归一化 + 语义近似去重 + 长度/数量预算。 */
    fun dedupeAndClamp(
        facts: List<String>,
        existingMemory: String,
        maxFacts: Int = MAX_FACTS_PER_RUN,
    ): List<String> {
        val memoryNorms = existingMemory.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toList()
        val result = mutableListOf<String>()
        for (fact in facts) {
            val trimmed = fact.trim().take(MAX_FACT_CHARS)
            if (trimmed.length < 3) continue
            if (NEGATIVE_FACTS.any { trimmed.contains(it) }) continue
            val norm = normalize(trimmed)
            if (memoryNorms.any { similar(norm, it) }) continue
            if (result.any { similar(norm, normalize(it)) }) continue
            result += trimmed
            if (result.size >= maxFacts) break
        }
        return result
    }

    /** 归一化用于比较：去空白与常见中英文标点、统一小写。 */
    private fun normalize(text: String): String {
        val punct = " \t，。！？；：、,.!?;:()【】[]\"'“”"
        return text.lowercase().filter { it !in punct }
    }

    private fun charBigrams(s: String): Set<String> =
        if (s.length < 2) setOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    /** 语义近似：完全/包含相等，或字符二元组 Jaccard 高且长度接近。 */
    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a))) return true
        val ba = charBigrams(a)
        val bb = charBigrams(b)
        if (ba.isEmpty() || bb.isEmpty()) return false
        val jaccard = ba.intersect(bb).size.toDouble() / ba.union(bb).size
        val lenRatio = minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        return jaccard >= 0.6 && lenRatio >= 0.5
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
