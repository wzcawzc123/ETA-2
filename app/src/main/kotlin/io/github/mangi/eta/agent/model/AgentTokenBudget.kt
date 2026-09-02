package io.github.mangi.eta.agent.model

/**
 * 基于真实 token 用量的预算自适应与缓存命中率度量（纯逻辑，可 kotlinc 单测）。
 *
 * 运行时已通过 ProviderEvent.Usage → AgentEvent.UsageReceived 拿到真实 input/output/cached token
 * （见 AgentTokenUsage：inputTokens、cachedTokens）。这里用它们做两类事：
 *  - **自适应修正"每轮 token"估值**：裁剪预算不再固定猜 1600/2000，而是按相邻两轮真实 prompt token
 *    的增量（每轮边际成本）做指数平滑，从而更贴合"工具密集轮实际很大"的现实——既是防超窗，也避免误裁。
 *  - **缓存命中率量测**：cachedTokens/inputTokens，让"命中率"可观测、可对比（用户目标：提高命中）。
 *
 * 全部为纯函数、无内部状态：调用方（AgentLoop / AgentAppState）持有状态并逐轮喂入，便于单测。
 */
internal object AgentTokenBudget {

    /** 指数平滑的新观测权重（0,1）。 */
    private const val ALPHA = 0.25

    private const val MIN_TOKENS_PER_ROUND = 200
    private const val MAX_TOKENS_PER_ROUND = 8_000

    /** 无观测值/回退时的默认每轮 token 估值。 */
    const val DEFAULT_TOKENS_PER_ROUND = 1_600

    /** 有效缓存命中率：input 为空或 <=0 时返回 0.0，结果夹在 [0,1]。 */
    fun cacheHitRate(inputTokens: Int?, cachedTokens: Int?): Double {
        val input = inputTokens ?: return 0.0
        if (input <= 0) return 0.0
        val cached = (cachedTokens ?: 0).coerceAtLeast(0)
        return (cached.toDouble() / input.toDouble()).coerceIn(0.0, 1.0)
    }

    /** 相邻两轮的真实"每轮成本"（prompt token 增量）；信号无效（null/非正）时返回 null。 */
    fun marginalRoundCost(prevPromptTokens: Int?, curPromptTokens: Int?): Int? {
        if (prevPromptTokens == null || curPromptTokens == null) return null
        val delta = curPromptTokens - prevPromptTokens
        // 只丢弃非正噪声，不再用下限 clamp——否则把"轻轮"的真实小成本抬成 200+，扭曲自适应结果。
        return if (delta <= 0) null else delta.coerceIn(1, MAX_TOKENS_PER_ROUND)
    }

    /** 用真实每轮成本平滑更新"每轮 token"估值；无观测时回退 [previousEstimate] 或默认值。 */
    fun adaptiveTokensPerRound(previousEstimate: Int?, observedMarginal: Int?): Int {
        val observed = observedMarginal ?: return previousEstimate ?: DEFAULT_TOKENS_PER_ROUND
        val base = previousEstimate ?: observed
        val ema = ALPHA * observed + (1.0 - ALPHA) * base
        return ema.coerceIn(MIN_TOKENS_PER_ROUND.toDouble(), MAX_TOKENS_PER_ROUND.toDouble()).toInt()
    }
}
