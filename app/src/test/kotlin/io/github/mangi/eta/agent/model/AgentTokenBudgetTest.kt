package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTokenBudgetTest {

    @Test
    fun cacheHitRateComputesBoundedRatio() {
        assertEquals(0.6, AgentTokenBudget.cacheHitRate(100, 60), 1e-6)
        assertEquals(0.0, AgentTokenBudget.cacheHitRate(100, null), 1e-6)
        assertEquals(0.0, AgentTokenBudget.cacheHitRate(null, 60), 1e-6)
        assertEquals(0.0, AgentTokenBudget.cacheHitRate(0, 60), 1e-6)
        // 命中数不得超过输入
        assertEquals(1.0, AgentTokenBudget.cacheHitRate(100, 200), 1e-6)
    }

    @Test
    fun marginalRoundCostUsesPositiveDelta() {
        assertEquals(40, AgentTokenBudget.marginalRoundCost(100, 140)?.let { it })
        assertEquals(null, AgentTokenBudget.marginalRoundCost(null, 140))
        assertEquals(null, AgentTokenBudget.marginalRoundCost(150, 140))
        assertEquals(null, AgentTokenBudget.marginalRoundCost(100, 100))
    }

    @Test
    fun adaptiveTokensPerRoundEmasAndClamps() {
        // 无观测 → 回退默认
        assertEquals(AgentTokenBudget.DEFAULT_TOKENS_PER_ROUND, AgentTokenBudget.adaptiveTokensPerRound(null, null))
        // 首观测直接用观测值
        assertEquals(2000, AgentTokenBudget.adaptiveTokensPerRound(null, 2000))
        // 平滑：0.25*2400 + 0.75*1600 = 1800
        assertEquals(1800, AgentTokenBudget.adaptiveTokensPerRound(1600, 2400))
        // 夹上界
        assertTrue(AgentTokenBudget.adaptiveTokensPerRound(6_000, 9_000) <= 8_000)
    }

    @Test
    fun trimUsesRealPerRoundEstimateToShrink() {
        // 同样的历史，若真实每轮 token 更大（工具密集），则轮数上限更小 → 裁得更狠。
        val history = List(80) { AgentModelClient.ConversationMessage(role = if (it % 2 == 0) "user" else "assistant") }
        val light = AgentHistoryWindow.trim(history, 32_000, tokensPerRoundEstimate = 1_600)
        val heavy = AgentHistoryWindow.trim(history, 32_000, tokensPerRoundEstimate = 4_000)
        assertTrue(heavy.size <= light.size)
    }
}
