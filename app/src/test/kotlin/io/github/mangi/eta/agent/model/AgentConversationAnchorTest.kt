package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentConversationAnchorTest {

    @Test
    fun firstUserMessage_picksFirstUser() {
        val h = listOf(
            AgentModelClient.ConversationMessage(role = "system", content = "s"),
            AgentModelClient.ConversationMessage(role = "user", content = "帮我看看 obra/superpowers"),
            AgentModelClient.ConversationMessage(role = "assistant", content = "已完成"),
        )
        assertEquals("帮我看看 obra/superpowers", AgentConversationAnchor.firstUserMessage(h))
    }

    @Test
    fun firstUserMessage_boundsLength() {
        val h = listOf(AgentModelClient.ConversationMessage(role = "user", content = "x".repeat(2000)))
        assertEquals(AgentConversationAnchor.MAX_ANCHOR_CHARS, AgentConversationAnchor.firstUserMessage(h)!!.length)
    }

    @Test
    fun firstUserMessage_nullWhenNoUserOrBlank() {
        assertNull(AgentConversationAnchor.firstUserMessage(listOf(AgentModelClient.ConversationMessage(role = "system"))))
        assertNull(AgentConversationAnchor.firstUserMessage(listOf(AgentModelClient.ConversationMessage(role = "user", content = "   "))))
    }
}
