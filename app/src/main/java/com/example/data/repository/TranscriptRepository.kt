package com.example.data.repository

import com.example.data.db.TranscriptDao
import com.example.data.db.TranscriptEntity
import com.example.data.db.TranscriptLineEntity
import kotlinx.coroutines.flow.Flow

class TranscriptRepository(private val transcriptDao: TranscriptDao) {

    val allTranscripts: Flow<List<TranscriptEntity>> = transcriptDao.getAllTranscripts()

    suspend fun getTranscriptById(id: Long): TranscriptEntity? = transcriptDao.getTranscriptById(id)

    fun getLinesForTranscript(transcriptId: Long): Flow<List<TranscriptLineEntity>> =
        transcriptDao.getLinesForTranscript(transcriptId)

    suspend fun getLinesForTranscriptSync(transcriptId: Long): List<TranscriptLineEntity> =
        transcriptDao.getLinesForTranscriptSync(transcriptId)

    suspend fun saveSession(
        title: String,
        languageCode: String,
        languageName: String,
        lines: List<Pair<Long, Pair<String, String>>>
    ): Long {
        if (lines.isEmpty()) return 0L
        val preview = lines.take(3).joinToString(" ") { it.second.first }
        val transcript = TranscriptEntity(
            title = title,
            languageCode = languageCode,
            languageName = languageName,
            timestamp = System.currentTimeMillis(),
            totalLines = lines.size,
            previewText = preview
        )
        val transcriptId = transcriptDao.insertTranscript(transcript)
        val lineEntities = lines.map { (timestampMs, pair) ->
            val seconds = (timestampMs / 1000) % 60
            val minutes = (timestampMs / (1000 * 60)) % 60
            val formatted = String.format("%02d:%02d", minutes, seconds)
            TranscriptLineEntity(
                transcriptId = transcriptId,
                timestampMs = timestampMs,
                timeFormatted = formatted,
                originalText = pair.first,
                turkishText = pair.second
            )
        }
        transcriptDao.insertLines(lineEntities)
        return transcriptId
    }

    suspend fun deleteTranscriptById(id: Long) = transcriptDao.deleteTranscriptById(id)

    fun searchTranscripts(query: String): Flow<List<TranscriptEntity>> =
        transcriptDao.searchTranscripts(query)
}
