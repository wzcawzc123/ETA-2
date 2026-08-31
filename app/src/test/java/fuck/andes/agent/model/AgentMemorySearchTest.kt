package fuck.andes.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemorySearchTest {

    @Test
    fun queryTermsSplitsAndDropsShort() {
        assertEquals(listOf("支付", "密码"), AgentMemorySearch.queryTerms("支付 密码 嗯"))
        assertEquals(emptyList<String>(), AgentMemorySearch.queryTerms("啊"))
        assertEquals(emptyList<String>(), AgentMemorySearch.queryTerms("   "))
    }

    @Test
    fun scoreTextCountsTermLengths() {
        assertEquals(2, AgentMemorySearch.scoreText("提到密码", listOf("密码")))
        assertEquals(0, AgentMemorySearch.scoreText("无关内容", listOf("支付")))
        assertEquals(4, AgentMemorySearch.scoreText("支付密码", listOf("支付", "密码")))
    }

    @Test
    fun searchRanksAndBounds() {
        val summaries = listOf(
            "会话1" to "讨论了支付流程和密码重置",
            "会话2" to "关于周末出行安排",
        )
        val headings = listOf("# 核心记忆", "## 支付相关")
        val hits = AgentMemorySearch.search("支付 密码", summaries, headings)
        assertTrue(hits.isNotEmpty())
        val top = hits.first()
        assertTrue(top.score >= 4)
        assertTrue(hits.size <= AgentMemorySearch.MAX_HITS)
        // 无匹配词
        assertEquals(emptyList<AgentMemorySearch.Hit>(), AgentMemorySearch.search("zzz不存在", summaries, headings))
        // 空查询
        assertEquals(emptyList<AgentMemorySearch.Hit>(), AgentMemorySearch.search(" ", summaries, headings))
    }

    @Test
    fun searchIsCaseInsensitive() {
        val summaries = listOf("c1" to "Payment gateway discussion")
        assertTrue(AgentMemorySearch.search("payment", summaries, emptyList()).isNotEmpty())
    }

    @Test
    fun queryTermsAddsNgramsForChineseLongTokens() {
        val terms = AgentMemorySearch.queryTerms("用户偏好在广州")
        assertTrue(terms.contains("用户偏好在广州"))
        assertTrue(terms.contains("用户"))
        assertTrue(terms.contains("广州"))
    }
}
