package fuck.andes.agent.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConversationCodecTest {
    @Test
    fun toolRoundTripPreservesReasoningContentForCompatibleProviders() {
        val assistant = JSONObject()
            .put("role", "assistant")
            .put("content", JSONObject.NULL)
            .put("reasoning_content", "先分析工具参数")
            .put(
                "tool_calls",
                JSONArray().put(
                    JSONObject()
                        .put("id", "call-1")
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", "device_info")
                                .put("arguments", "{}")
                        )
                )
            )

        val durable = AgentConversationCodec.durableMessage(assistant)
        val replayed = AgentConversationCodec.toJsonObject(durable)

        assertEquals("先分析工具参数", replayed.getString("reasoning_content"))
        assertEquals("call-1", replayed.getJSONArray("tool_calls").getJSONObject(0).getString("id"))
    }

    @Test
    fun durableImageObservationNeverPersistsBase64Payload() {
        val message = AgentConversationCodec.durableMessage(
            AgentConversationCodec.userMessage(
                text = "屏幕观察",
                images = listOf(
                    AgentModelClient.ModelImage(
                        reference = "data:image/png;base64,${"A".repeat(20_000)}",
                        mimeType = "image/png",
                        bytes = 15_000,
                    )
                ),
            )
        )

        assertFalse(message.contentJson.contains("base64"))
        assertTrue(message.contentJson.contains(AgentConversationCodec.IMAGE_OMITTED_PREFIX))
    }

    @Test
    fun replaceImagePlaceholderReplacesOmittedText() {
        val message = AgentConversationCodec.durableMessage(
            AgentConversationCodec.userMessage(
                text = "看看这张图",
                images = listOf(
                    AgentModelClient.ModelImage(
                        reference = "data:image/png;base64,AAAA",
                        mimeType = "image/png",
                        bytes = 4,
                    )
                ),
            )
        )

        assertTrue(message.contentJson.contains(AgentConversationCodec.IMAGE_OMITTED_PREFIX))
        assertFalse(message.contentJson.contains("微信"))

        val updated = AgentConversationCodec.replaceImagePlaceholder(
            message,
            "一张微信聊天截图，显示对方发送了三条消息",
        )

        assertFalse(updated.contentJson.contains(AgentConversationCodec.IMAGE_OMITTED_PREFIX))
        assertTrue(updated.contentJson.contains("图片摘要"))
        assertTrue(updated.contentJson.contains("微信"))
        assertTrue(updated.contentJson.contains("三条消息"))
    }

    @Test
    fun replaceImagePlaceholderReturnsSameIfNoPlaceholder() {
        val message = AgentModelClient.ConversationMessage(
            role = "user",
            content = "纯文本消息",
        )

        val updated = AgentConversationCodec.replaceImagePlaceholder(message, "摘要")
        assertTrue(updated === message)
    }

    @Test
    fun ipcTranscriptHasHardBudgetAndNeverStartsWithOrphanToolResult() {
        val messages = buildList {
            repeat(20) { index ->
                add(
                    AgentModelClient.ConversationMessage(
                        role = "assistant",
                        content = "回答-$index-${"x".repeat(20_000)}",
                    )
                )
                add(
                    AgentModelClient.ConversationMessage(
                        role = "tool",
                        toolCallId = "call-$index",
                        content = "结果-${"y".repeat(20_000)}",
                    )
                )
            }
            add(AgentModelClient.ConversationMessage(role = "assistant", content = "最终答案"))
        }

        val encoded = AgentConversationCodec.encodeTranscriptForIpc(messages)
        val decoded = AgentConversationCodec.decodeTranscript(encoded)

        assertTrue(encoded.length <= AgentConversationCodec.MAX_IPC_TRANSCRIPT_CHARS)
        assertTrue(decoded.isNotEmpty())
        assertFalse(decoded.first().role == "tool")
        assertTrue(decoded.first().content.contains("容量上限已压缩"))
        assertTrue(decoded.last().content.contains("最终答案"))
    }

    @Test
    fun conversationCheckpointHasHardBudgetAndKeepsNewestContext() {
        val messages = buildList {
            repeat(20) { index ->
                add(
                    AgentModelClient.ConversationMessage(
                        role = "assistant",
                        content = "回答-$index-${"x".repeat(20_000)}",
                    )
                )
            }
            add(AgentModelClient.ConversationMessage(role = "user", content = "继续处理最新任务"))
        }

        val encoded = AgentConversationCodec.encodeConversationCheckpoint(messages)
        val decoded = AgentConversationCodec.decodeTranscript(encoded)

        assertTrue(encoded.length <= AgentConversationCodec.MAX_CONVERSATION_CHECKPOINT_CHARS)
        assertTrue(decoded.first().content.contains("容量上限已压缩"))
        assertEquals("继续处理最新任务", decoded.last().content)
    }

    @Test
    fun responsesOutputItemsStayInMemoryAndNeverEnterStableTranscript() {
        val source = JSONObject().put("role", "assistant").put("content", "完成")
        ResponsesEphemeralState.attachOutputItems(
            source,
            JSONArray().put(
                JSONObject()
                    .put("type", "reasoning")
                    .put("encrypted_content", "opaque-secret"),
            ),
        )
        val history = AgentConversationCodec.assistantHistoryMessage(source, emptyList())
        assertTrue(ResponsesEphemeralState.outputItems(history) != null)

        val stable = AgentConversationCodec.durableMessage(history)
        val encoded = AgentConversationCodec.encodeTranscriptForStorage(listOf(stable))
        assertFalse(encoded.contains("opaque-secret"))
        assertFalse(encoded.contains("_eta_responses_output_items"))
    }
}
