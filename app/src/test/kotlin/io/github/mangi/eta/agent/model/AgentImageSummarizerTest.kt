package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentImageSummarizerTest {

    @Test
    fun extractImageReferencesReturnsDataImageUrls() {
        val images = listOf(
            AgentModelClient.ModelImage(
                reference = "data:image/png;base64,AAAA",
                mimeType = "image/png",
                bytes = 4,
            ),
            AgentModelClient.ModelImage(
                reference = "data:image/jpeg;base64,BBBB",
                mimeType = "image/jpeg",
                bytes = 4,
            ),
        )

        val refs = AgentImageSummarizer.extractImageReferences(images)

        assertEquals(2, refs.size)
        assertTrue(refs[0].startsWith("data:image/"))
        assertTrue(refs[1].startsWith("data:image/"))
    }

    @Test
    fun extractImageReferencesSkipsNonDataUrls() {
        val images = listOf(
            AgentModelClient.ModelImage(
                reference = "data:image/png;base64,AAAA",
                mimeType = "image/png",
                bytes = 4,
            ),
            AgentModelClient.ModelImage(
                reference = "https://example.com/photo.jpg",
                mimeType = "image/jpeg",
                bytes = 0,
            ),
            AgentModelClient.ModelImage(
                reference = "content://media/external/images/123",
                mimeType = "image/png",
                bytes = 0,
            ),
        )

        val refs = AgentImageSummarizer.extractImageReferences(images)

        assertEquals(1, refs.size)
        assertTrue(refs[0].startsWith("data:image/"))
    }

    @Test
    fun extractImageReferencesEmptyListReturnsEmpty() {
        val refs = AgentImageSummarizer.extractImageReferences(emptyList())
        assertTrue(refs.isEmpty())
    }
}
