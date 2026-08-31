package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopContextTest {

    private fun msg(role: String, content: String = role): JSONObject =
        JSONObject().put("role", role).put("content", content)

    private fun messagesOf(vararg items: JSONObject): JSONArray =
        JSONArray().apply { items.forEach(::put) }

    @Test
    fun underLimit_returnsSame() {
        val messages = messagesOf(
            msg("system", "s"), msg("user", "u"),
            msg("assistant", "a1"), msg("tool", "t1"),
        )
        assertEquals(messages.toString(), AgentLoopContext.trimToolTail(messages, 10).toString())
    }

    @Test
    fun overLimit_dropsOldestCompleteRounds() {
        val messages = JSONArray().apply {
            put(msg("system", "s"))
            put(msg("user", "u"))
            repeat(4) { round ->
                put(msg("assistant", "a$round"))
                put(msg("tool", "r$round"))
            }
        }
        val out = AgentLoopContext.trimToolTail(messages, 2)
        // 保留 system + user + 最近 2 轮（assistant/tool 各 2 条）
        assertEquals(6, out.length())
        assertEquals("user", out.getJSONObject(1).getString("role"))
        assertEquals("a2", out.getJSONObject(2).getString("content"))
        assertEquals("r2", out.getJSONObject(3).getString("content"))
        assertEquals("a3", out.getJSONObject(4).getString("content"))
    }

    @Test
    fun zeroOrNegative_returnsSame() {
        val messages = messagesOf(msg("user", "u"), msg("assistant", "a"), msg("tool", "t"))
        assertEquals(messages.toString(), AgentLoopContext.trimToolTail(messages, 0).toString())
        assertEquals(messages.toString(), AgentLoopContext.trimToolTail(messages, -1).toString())
    }

    @Test
    fun noUserOrOnlyUser_returnsSame() {
        val noUser = messagesOf(msg("assistant", "a"), msg("tool", "t"))
        assertEquals(noUser.toString(), AgentLoopContext.trimToolTail(noUser, 1).toString())
        val onlyUser = messagesOf(msg("user", "u"))
        assertEquals(onlyUser.toString(), AgentLoopContext.trimToolTail(onlyUser, 1).toString())
    }

    @Test
    fun empty_returnsSame() {
        assertEquals("[]", AgentLoopContext.trimToolTail(JSONArray(), 5).toString())
    }

    @Test
    fun trimInRun_usesContextWindowEstimate() {
        val messages = JSONArray().apply {
            put(msg("user", "u"))
            repeat(60) { round ->
                put(msg("assistant", "a$round"))
                put(msg("tool", "r$round"))
            }
        }
        // 32000 token → 16 轮
        val out = AgentLoopContext.trimInRun(messages, 32_000)
        assertTrue(out.length() < messages.length())
        assertEquals("u", out.getJSONObject(0).getString("content"))
        // 默认 30 轮
        val def = AgentLoopContext.trimInRun(messages, null)
        assertTrue(def.length() > out.length())
    }
}
