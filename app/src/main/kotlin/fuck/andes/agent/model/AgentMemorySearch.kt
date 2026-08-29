package fuck.andes.agent.model

/** 会话记忆检索（P4）：在会话摘要与 MEMORY.md 标题中做关键词检索（纯逻辑，可单测）。 */
internal object AgentMemorySearch {

    const val MAX_QUERY_CHARS = 120
    const val MAX_HITS = 8
    const val MAX_SNIPPET_CHARS = 160

    data class Hit(
        val source: String,
        val snippet: String,
        val score: Int,
    )

    /** 查询词拆分：按空白/标点切分，忽略 1 字符词。 */
    fun queryTerms(query: String): List<String> =
        query.trim()
            .take(MAX_QUERY_CHARS)
            .split(Regex("[\\s，。！？；：、,.!?;:]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

    /** 在候选文本上打分：命中词数 × 词长。 */
    fun scoreText(text: String, terms: List<String>): Int {
        if (terms.isEmpty()) return 0
        val lowered = text.lowercase()
        return terms.sumOf { term ->
            if (term.lowercase() in lowered) term.length else 0
        }
    }

    /** 在摘要列表与标题列表中检索，返回按分数降序的去重结果。 */
    fun search(
        query: String,
        summaries: List<Pair<String, String>>,
        headings: List<String>,
    ): List<Hit> {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<Hit>()
        summaries.forEach { (conversationId, summary) ->
            val score = scoreText(summary, terms)
            if (score > 0) {
                hits += Hit(
                    source = "会话摘要($conversationId)",
                    snippet = summary.trim().take(MAX_SNIPPET_CHARS),
                    score = score,
                )
            }
        }
        headings.forEach { heading ->
            val score = scoreText(heading, terms)
            if (score > 0) {
                hits += Hit(
                    source = "核心记忆",
                    snippet = heading.trim().take(MAX_SNIPPET_CHARS),
                    score = score,
                )
            }
        }
        return hits
            .distinctBy { it.source + it.snippet }
            .sortedByDescending { it.score }
            .take(MAX_HITS)
    }
}
