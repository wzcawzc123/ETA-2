package fuck.andes.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFactRulesTest {

    @Test
    fun buildPromptContainsDialogAndConstraints() {
        val prompt = AgentFactRules.buildPrompt("我叫小明", "好的，小明")
        assertTrue(prompt.contains("用户：我叫小明"))
        assertTrue(prompt.contains("助手：好的，小明"))
        assertTrue(prompt.contains("0-3 条"))
        assertTrue(prompt.contains("不要提取密钥"))
        assertTrue(prompt.contains("每行输出一条事实"))
        // 无助手内容时不输出助手段
        assertFalse(AgentFactRules.buildPrompt("hi", "").contains("助手："))
    }

    @Test
    fun parseFactsAcceptsDashAndBulletPrefixes() {
        val raw = "一些说明\n- 用户偏好中文\n• 用户住在上海\n- 短\n无关行\n- 重复\n- 重复"
        val facts = AgentFactRules.parseFacts(raw)
        assertEquals(listOf("用户偏好中文", "用户住在上海"), facts)
    }

    @Test
    fun dedupeAndClampFiltersExistingAndLimits() {
        val facts = listOf("用户偏好中文", "用户住在上海", "x", "用户偏好中文")
        val merged = AgentFactRules.dedupeAndClamp(facts, existingMemory = "用户住在上海")
        assertEquals(listOf("用户偏好中文"), merged)
        val many = (1..10).map { "事实$it" }
        assertEquals(3, AgentFactRules.dedupeAndClamp(many, "").size)
    }

    @Test
    fun factLengthCapped() {
        val merged = AgentFactRules.dedupeAndClamp(
            listOf("长".repeat(1000)),
            "",
            maxFacts = 1,
        )
        assertEquals(AgentFactRules.MAX_FACT_CHARS, merged.single().length)
    }
}
