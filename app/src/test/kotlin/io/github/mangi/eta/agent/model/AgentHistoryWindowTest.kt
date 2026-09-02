package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHistoryWindowTest {

    private fun msg(role: String) = AgentModelClient.ConversationMessage(role = role)

    @Test
    fun underLimit_returnsSame() {
        val h = listOf(msg("user"), msg("assistant"), msg("user"), msg("assistant"))
        assertEquals(h, AgentHistoryWindow.trimByUserRounds(h, 10))
    }

    @Test
    fun overLimit_keepsRecentUserRounds_startsWithUser() {
        val h = listOf(
            msg("user"), msg("assistant"),
            msg("user"), msg("assistant"),
            msg("user"), msg("assistant"),
        )
        val out = AgentHistoryWindow.trimByUserRounds(h, 2)
        assertEquals(4, out.size)
        assertEquals("user", out[0].role)
        assertEquals("assistant", out[1].role)
        assertEquals("user", out[2].role)
        assertEquals("assistant", out[3].role)
    }

    @Test
    fun overLimit_keepsOnlyTail() {
        val h = List(20) { msg(if (it % 2 == 0) "user" else "assistant") }
        val out = AgentHistoryWindow.trimByUserRounds(h, 3)
        // 最近 3 个用户轮 = 从第 17 个元素（index 16 是 user）开始
        assertTrue(out.first().role == "user")
        assertTrue(out.size < h.size)
    }

    @Test
    fun zeroOrNegative_returnsSame() {
        val h = listOf(msg("user"), msg("assistant"))
        assertEquals(h, AgentHistoryWindow.trimByUserRounds(h, 0))
        assertEquals(h, AgentHistoryWindow.trimByUserRounds(h, -3))
    }

    @Test
    fun noUser_returnsSame() {
        val h = listOf(msg("assistant"), msg("tool"))
        assertEquals(h, AgentHistoryWindow.trimByUserRounds(h, 5))
    }

    @Test
    fun empty_returnsSame() {
        assertEquals(emptyList<AgentModelClient.ConversationMessage>(), AgentHistoryWindow.trimByUserRounds(emptyList(), 5))
    }

    @Test
    fun trim_usesContextWindowEstimate() {
        val h = List(80) { msg(if (it % 2 == 0) "user" else "assistant") }
        val out = AgentHistoryWindow.trim(h, 32000) // 32000/1600 = 20 轮
        assertTrue(out.size <= 40)
        assertTrue(out.first().role == "user")
    }

    @Test
    fun trim_nullContext_usesDefault() {
        val h = List(200) { msg(if (it % 2 == 0) "user" else "assistant") }
        val out = AgentHistoryWindow.trim(h, null) // 默认 24 轮
        assertTrue(out.first().role == "user")
        assertTrue(out.size < h.size)
    }

    @Test
    fun measuredBudget_trimsHeavyRoundsMoreThanEstimate() {
        // 每轮内容 3000 字符：重内容按体积预算只留极少量轮，远少于轮数粗估的 20 轮
        val heavy = (0 until 38).flatMap { i ->
            listOf(
                AgentModelClient.ConversationMessage(role = "user", content = "u".repeat(3000)),
                AgentModelClient.ConversationMessage(role = "assistant", content = "a".repeat(3000)),
            )
        }
        val light = List(80) { msg(if (it % 2 == 0) "user" else "assistant") }
        val heavyOut = AgentHistoryWindow.trim(heavy, 32_000)
        val lightOut = AgentHistoryWindow.trim(light, 32_000)
        assertTrue("heavy=$heavyOut should be trimmed to fewer messages than light=$lightOut", heavyOut.size < lightOut.size)
        assertTrue(heavyOut.first().role == "user")
    }

    @Test
    fun serializedChars_countsContentAndOverhead() {
        val empty = AgentModelClient.ConversationMessage(role = "user")
        val filled = AgentModelClient.ConversationMessage(role = "user", content = "你好")
        assertTrue(AgentHistoryWindow.serializedChars(filled) > AgentHistoryWindow.serializedChars(empty))
    }

    @Test
    fun maxUserRoundsByBudget_lightContentFitsMoreThanHeavy() {
        val light = AgentHistoryWindow.maxUserRoundsByBudget(
            List(100) { AgentModelClient.ConversationMessage(role = "user") },
            32_000,
        )
        val heavy = AgentHistoryWindow.maxUserRoundsByBudget(
            List(100) { AgentModelClient.ConversationMessage(role = "user", content = "x".repeat(5000)) },
            32_000,
        )
        assertTrue(light > heavy)
    }
}
