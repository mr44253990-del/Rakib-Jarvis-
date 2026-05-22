package com.example.data.db

import androidx.room.*
import com.example.data.model.ChatMessage
import com.example.data.model.Memory
import com.example.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    // Chat messages
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChat()

    // Notes
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Int)

    @Query("UPDATE notes SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateNoteSyncStatus(id: Int, isSynced: Boolean)

    // Memories
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<Memory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Int)
}
