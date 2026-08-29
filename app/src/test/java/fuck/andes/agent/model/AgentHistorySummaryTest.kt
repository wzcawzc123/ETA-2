package fuck.andes.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHistorySummaryTest {

    private fun msg(role: String, content: String = role) =
        AgentModelClient.ConversationMessage(role = role, content = content)

    @Test
    fun needsRegenerationUsesThreshold() {
        assertFalse(AgentHistorySummary.needsRegeneration(7))
        assertTrue(AgentHistorySummary.needsRegeneration(8))
        assertTrue(AgentHistorySummary.needsRegeneration(20))
        assertTrue(AgentHistorySummary.needsRegeneration(5, thresholdTurns = 5))
    }

    @Test
    fun trimmedUserTurnCountCountsUsersBeforeWindowStart() {
        val full = listOf(
            msg("user", "u1"), msg("assistant", "a1"),
            msg("user", "u2"), msg("assistant", "a2"),
            msg("user", "u3"), msg("assistant", "a3"),
        )
        val windowed = full.subList(4, full.size).toList() // 从 u3 开始
        assertEquals(2, AgentHistorySummary.trimmedUserTurnCount(full, windowed))
        assertEquals(0, AgentHistorySummary.trimmedUserTurnCount(full, full))
        assertEquals(0, AgentHistorySummary.trimmedUserTurnCount(emptyList(), windowed))
    }

    @Test
    fun serializeTurnsFormatsRolesAndBounds() {
        val turns = listOf(
            msg("user", "请检查系统设置"),
            msg("assistant", "已检查"),
            AgentModelClient.ConversationMessage(role = "tool", content = "结果"),
        )
        val text = AgentHistorySummary.serializeTurns(turns)
        assertTrue(text.contains("用户: 请检查系统设置"))
        assertTrue(text.contains("助手: 已检查"))
        assertTrue(text.contains("tool: 结果"))
        // 空内容轮次跳过
        val withBlank = listOf(msg("user", "有内容"), AgentModelClient.ConversationMessage(role = "user"))
        val text2 = AgentHistorySummary.serializeTurns(withBlank)
        assertTrue(text2.contains("用户: 有内容"))
        assertFalse(text2.contains("助手"))
        assertEquals(1, text2.lines().size)
    }

    @Test
    fun buildPromptIsDeterministicAndContainsExistingSummary() {
        val turns = listOf(msg("user", "u"), msg("assistant", "a"))
        val p1 = AgentHistorySummary.buildPrompt("已有摘要", turns)
        val p2 = AgentHistorySummary.buildPrompt("已有摘要", turns)
        assertEquals(p1, p2)
        assertTrue(p1.contains("已有摘要"))
        assertTrue(p1.contains("用户: u"))
        assertTrue(p1.contains("只输出摘要本身"))
        // 无既有摘要时不含"已有摘要"段
        assertFalse(AgentHistorySummary.buildPrompt(null, turns).contains("已有摘要"))
    }

    @Test
    fun clampSummaryBounds() {
        assertEquals("abc", AgentHistorySummary.clampSummary("  abc  "))
        assertEquals("a".repeat(100), AgentHistorySummary.clampSummary("a".repeat(500), maxChars = 100))
        assertEquals("", AgentHistorySummary.clampSummary("  "))
    }
}
