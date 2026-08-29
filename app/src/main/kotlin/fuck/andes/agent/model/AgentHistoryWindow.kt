package fuck.andes.agent.model

/**
 * 长对话历史上下文预算（纯逻辑、可 kotlinc 单测）。
 *
 * 此前 history 纯追加、注入时全量塞给模型，长对话会无限增长并撑爆
 * 模型 context window。这里按"最近 N 个用户轮"做滑动窗口裁剪，防止超窗，
 * 同时保证裁剪后的窗口以 user 消息开头、不产生孤儿 tool/assistant 消息。
 * 只影响喂给模型的注入副本，UI 与持久化保持全量。
 */
internal object AgentHistoryWindow {

    private const val DEFAULT_MAX_USER_ROUNDS = 24

    fun trim(
        history: List<AgentModelClient.ConversationMessage>,
        contextWindow: Int?,
        defaultMaxUserRounds: Int = DEFAULT_MAX_USER_ROUNDS,
    ): List<AgentModelClient.ConversationMessage> {
        val turns = if (contextWindow != null && contextWindow > 0) {
            // 每条历史约 1600 token（含 assistant/工具调用），按 token 预算粗估可保留轮数。
            (contextWindow / 1600).coerceIn(6, 60)
        } else defaultMaxUserRounds
        return trimByUserRounds(history, turns)
    }

    fun trimByUserRounds(
        history: List<AgentModelClient.ConversationMessage>,
        maxUserRounds: Int,
    ): List<AgentModelClient.ConversationMessage> {
        if (maxUserRounds <= 0 || history.isEmpty()) return history
        val userIndices = history.indices.filter { history[it].role == "user" }
        if (userIndices.size <= maxUserRounds || userIndices.isEmpty()) return history
        val cutIndex = userIndices[userIndices.size - maxUserRounds]
        return history.subList(cutIndex, history.size).toList()
    }
}
