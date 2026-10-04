package com.example.data.repository

import com.example.data.db.NoteDao
import com.example.data.db.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getNoteById(id)

    suspend fun insertNote(title: String, content: String): Long {
        val note = NoteEntity(
            title = title.ifBlank { "Başlıksız Not" },
            content = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNoteById(id: Long) = noteDao.deleteNoteById(id)

    suspend fun restoreNote(note: NoteEntity) = noteDao.insertNote(note)

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)
}
