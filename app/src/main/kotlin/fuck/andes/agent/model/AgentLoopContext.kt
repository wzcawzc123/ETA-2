package fuck.andes.agent.model

import org.json.JSONArray

/**
 * Agent 单次运行内的上下文预算（P2 补丁，纯逻辑可单测）。
 *
 * P1 只裁剪会话历史；单次 run 内工具执行轮次会不断追加到 messages，长自动化任务
 * （几十轮 GUI 操作）会撑爆 context window。这里按"保留最后一条 user 及其后最多
 * N 个完整工具轮"裁剪喂给模型的副本（整轮丢弃保证 assistant(tool_use) 与
 * tool_result 配对不被拆散），完整 transcript 仍保留在调用方的 messages 中。
 */
internal object AgentLoopContext {

    const val DEFAULT_MAX_TOOL_ROUNDS = 30
    const val TOKENS_PER_ROUND_ESTIMATE = 2_000

    fun trimInRun(
        messages: JSONArray,
        contextWindow: Int?,
        defaultMaxRounds: Int = DEFAULT_MAX_TOOL_ROUNDS,
    ): JSONArray {
        val maxRounds = if (contextWindow != null && contextWindow > 0) {
            (contextWindow / TOKENS_PER_ROUND_ESTIMATE).coerceIn(8, 40)
        } else {
            defaultMaxRounds
        }
        return trimToolTail(messages, maxRounds)
    }

    /**
     * 保留最后一条 user 之前的全部消息，以及其后最多 [maxRounds] 个完整工具轮。
     * 工具轮起点为 assistant 消息，其后直到下一个 assistant/末尾的 tool 消息属于该轮。
     */
    fun trimToolTail(messages: JSONArray, maxRounds: Int): JSONArray {
        val length = messages.length()
        if (maxRounds <= 0 || length == 0) return messages

        var lastUserIndex = -1
        for (index in length - 1 downTo 0) {
            if (messages.optJSONObject(index)?.optString("role") == "user") {
                lastUserIndex = index
                break
            }
        }
        if (lastUserIndex < 0 || lastUserIndex == length - 1) return messages

        var roundCount = 0
        for (index in lastUserIndex + 1 until length) {
            if (messages.optJSONObject(index)?.optString("role") == "assistant") {
                roundCount += 1
            }
        }
        if (roundCount <= maxRounds) return messages

        val roundsToDrop = roundCount - maxRounds
        var seen = 0
        var newStart = length
        for (index in lastUserIndex + 1 until length) {
            if (messages.optJSONObject(index)?.optString("role") == "assistant") {
                seen += 1
                if (seen == roundsToDrop + 1) {
                    newStart = index
                    break
                }
            }
        }
        if (newStart <= lastUserIndex + 1) return messages

        val result = JSONArray()
        for (index in 0 until lastUserIndex + 1) {
            result.put(messages.opt(index))
        }
        for (index in newStart until length) {
            result.put(messages.opt(index))
        }
        return result
    }
}
