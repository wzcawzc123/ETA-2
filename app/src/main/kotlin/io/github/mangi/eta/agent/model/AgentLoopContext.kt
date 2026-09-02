package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Agent 单次运行内的上下文预算（P2 补丁，纯逻辑可单测）。
 *
 * P1 只裁剪会话历史；单次 run 内工具执行轮次会不断追加到 messages，长自动化任务
 * （几十轮 GUI 操作）会撑爆 context window。这里按"保留最后一条 user 及其后最多
 * N 个完整工具轮"裁剪喂给模型的副本（整轮丢弃保证 assistant(tool_use) 与
 * tool_result 配对不被拆散），完整 transcript 仍保留在调用方的 messages 中。
 *
 * 工具轮数同样不再用固定估估算：工具结果通常很长，固定"每轮 2000 token"会低估真实体积，
 * 导致保留轮数过多而静默超窗。这里按实测 JSON 体积给出预算，重轮次自然少留。
 */
internal object AgentLoopContext {

    const val DEFAULT_MAX_TOOL_ROUNDS = 30
    const val TOKENS_PER_ROUND_ESTIMATE = 2_000

    private const val DEFAULT_MIN_TOOL_ROUNDS = 3

    /** 工具轮最多占据 context window 的比例；其余留给 system prompt、记忆、当前输入。 */
    private const val TOOL_CHAR_FRACTION = 0.50

    private const val JSON_OVERHEAD = 32L

    fun trimInRun(
        messages: JSONArray,
        contextWindow: Int?,
        defaultMaxRounds: Int = DEFAULT_MAX_TOOL_ROUNDS,
        tokensPerRoundEstimate: Int? = null,
    ): JSONArray {
        val maxRounds = if (contextWindow != null && contextWindow > 0) {
            // tokensPerRoundEstimate 为真实观测的每轮 token（由 AgentTokenBudget 反馈），无则用默认估值。
            val est = tokensPerRoundEstimate ?: TOKENS_PER_ROUND_ESTIMATE
            val roundEstimate = (contextWindow / est)
                .coerceIn(DEFAULT_MIN_TOOL_ROUNDS, 40)
            maxToolRoundsByBudget(messages, contextWindow, roundEstimate)
                .coerceAtMost(roundEstimate)
                .coerceAtLeast(DEFAULT_MIN_TOOL_ROUNDS)
        } else {
            defaultMaxRounds
        }
        return trimToolTail(messages, maxRounds)
    }

    /**
     * 按实测体积估算"最后一条 user 之后"能装下的完整工具轮数，封顶为 [cap]。
     *
     * 从末尾向前累计每条消息的 JSON 体积，遇到 assistant 记为一轮；预算按
     * 1 token ≈ 1 字符保守估算（宁可少留也不超窗）。当 [contextWindow] 未知/<=0 时返回 0，
     * 调用方回退到默认轮数。
     */
    fun maxToolRoundsByBudget(
        messages: JSONArray,
        contextWindow: Int,
        cap: Int = 40,
    ): Int {
        if (contextWindow <= 0) return 0
        val budgetChars = (contextWindow * TOOL_CHAR_FRACTION).toInt()
        if (budgetChars <= 0) return 0

        var lastUserIndex = -1
        for (index in messages.length() - 1 downTo 0) {
            if (messages.optJSONObject(index)?.optString("role") == "user") {
                lastUserIndex = index
                break
            }
        }
        if (lastUserIndex < 0) return 0

        var used = 0L
        var rounds = 0
        for (index in messages.length() - 1 downTo lastUserIndex + 1) {
            val obj = messages.optJSONObject(index) ?: continue
            val cost = serializedJsonChars(obj)
            if (used + cost > budgetChars.toLong()) break
            used += cost
            if (obj.optString("role") == "assistant") {
                rounds += 1
                if (rounds >= cap) break
            }
        }
        return rounds
    }

    /** 单条消息的 JSON 序列化字符开销代理（近似 provider 实际发送体积，可 kotlinc 单测）。 */
    fun serializedJsonChars(obj: JSONObject): Long = obj.toString().length.toLong() + JSON_OVERHEAD

    /**
     * 整批消息的序列化字符开销（超窗检测用）。
     *
     * 仅作为相对量级与低成本代理：1 字符 ≈ 1 token 对中文偏保守，对英文则偏大，
     * 适合做"是否明显超窗"的告警判断，不作为精确预算依据。
     */
    fun estimatedChars(messages: JSONArray): Long {
        var total = 0L
        for (index in 0 until messages.length()) {
            messages.optJSONObject(index)?.let { total += serializedJsonChars(it) }
        }
        return total
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
