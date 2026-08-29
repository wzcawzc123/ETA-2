package fuck.andes.data.repository

import android.content.Context
import fuck.andes.data.db.ConversationSummaryEntity
import fuck.andes.data.db.FuckAndesDatabase

/** 长对话滚动摘要持久化（P2）：按会话存取被裁剪轮次的压缩摘要。 */
internal object ConversationSummaryStore {

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    private fun context(): Context = checkNotNull(applicationContext) {
        "ConversationSummaryStore.init(context) must be called in Application.onCreate()"
    }

    private fun dao() = FuckAndesDatabase.get(context()).conversationSummaryDao()

    suspend fun summary(conversationId: String): String? =
        dao().summary(conversationId)?.summary

    suspend fun summarizedTurns(conversationId: String): Int =
        dao().summary(conversationId)?.summarizedTurns ?: 0

    suspend fun upsert(
        conversationId: String,
        summary: String,
        summarizedTurns: Int,
    ) {
        dao().upsert(
            ConversationSummaryEntity(
                conversationId = conversationId,
                summary = summary,
                summarizedTurns = summarizedTurns,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun all(): List<Pair<String, String>> =
        dao().all().map { it.conversationId to it.summary }
}
