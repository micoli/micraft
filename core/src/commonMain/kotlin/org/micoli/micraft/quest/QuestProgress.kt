package org.micoli.micraft.quest

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

enum class QuestStatus {
    TODO,
    IN_PROGRESS,
    ABANDONED,
    COMPLETED,
    FAILED
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class QuestProgress(
    val status: QuestStatus = QuestStatus.TODO,
    @EncodeDefault val progress: Map<String, Int> = emptyMap(),
    val acceptedAt: Long? = null,
    val completedAt: Long? = null,
    val lastCompletedAt: Long? = null,
)
