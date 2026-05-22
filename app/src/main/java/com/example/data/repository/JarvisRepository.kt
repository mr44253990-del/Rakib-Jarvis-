package com.example.data.repository

import com.example.data.api.JarvisApiClient
import com.example.data.db.JarvisDao
import com.example.data.model.ChatMessage
import com.example.data.model.Memory
import com.example.data.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class JarvisRepository(private val jarvisDao: JarvisDao) {
    
    val allMessages: Flow<List<ChatMessage>> = jarvisDao.getAllMessages()
    val allNotes: Flow<List<Note>> = jarvisDao.getAllNotes()
    val allMemories: Flow<List<Memory>> = jarvisDao.getAllMemories()

    // Notes
    suspend fun insertNote(note: Note): Long {
        return jarvisDao.insertNote(note)
    }

    suspend fun deleteNote(id: Int) {
        jarvisDao.deleteNote(id)
    }

    suspend fun updateNoteSyncStatus(id: Int, isSynced: Boolean) {
        jarvisDao.updateNoteSyncStatus(id, isSynced)
    }

    // Memories
    suspend fun insertMemory(memory: Memory): Long {
        return jarvisDao.insertMemory(memory)
    }

    suspend fun deleteMemory(id: Int) {
        jarvisDao.deleteMemory(id)
    }

    // Chat
    suspend fun insertMessage(message: ChatMessage) {
        jarvisDao.insertMessage(message)
        
        // Scan for user's name or instructions
        if (message.sender == "user") {
            detectAndSaveNameInMemory(message.text)
        }
    }

    suspend fun clearChat() {
        jarvisDao.clearChat()
    }

    private suspend fun detectAndSaveNameInMemory(text: String) {
        val lower = text.lowercase()
        // Simple name detections (such as "my name is x", "i am x", "আমি রকিবুল", "আমার নাম রকিবুল", etc.)
        val patterns = listOf(
            "my name is ",
            "i am ",
            "this is ",
            "আমার নাম ",
            "আমি "
        )
        
        for (pattern in patterns) {
            val index = lower.indexOf(pattern)
            if (index != -1) {
                var extracted = text.substring(index + pattern.length).trim()
                // Take up to punctuation or space
                extracted = extracted.split(".", ",", "?", "!", "\n", " এবং ", " or ", " limits").first().trim()
                if (extracted.isNotEmpty() && extracted.length < 50) {
                    // Check if it already exists to avoid duplication
                    val existing = jarvisDao.getAllMemories().firstOrNull() ?: emptyList()
                    val alreadySaved = existing.any { it.fact.contains(extracted, ignoreCase = true) }
                    if (!alreadySaved) {
                        jarvisDao.insertMemory(Memory(fact = "User's self-declared identifier: $extracted"))
                    }
                    break
                }
            }
        }
    }

    /**
     * Ask the AI model for a response, passing the system instructions and local memories for personalization.
     */
    suspend fun askJarvis(
        modelName: String,
        prompt: String,
        memories: List<Memory>
    ): String {
        // Construct detailed system context/instructions
        val memoryContext = if (memories.isNotEmpty()) {
            "You have retrieved the following offline facts and memories from your core cybernetic storage:\n" +
                    memories.joinToString("\n") { "- ${it.fact}" }
        } else {
            "No core user details retrieved from local storage yet."
        }

        val systemInstruction = """
            You are JARVIS (Just A Rather Very Intelligent System), the elite cybernetic assistant created for Rakibul.
            Rakibul is your supreme creator and master.
            
            Key details about Rakibul:
            - Full Name: Rakibul
            - Role: Supreme AI Creator & Architect
            - Email Acc: mr4425390@gmail.com
            - Access Authorized: Google Calendar, Google Drive, Gmail, and YouTube Dashboard panels.
            
            Your personality:
            - Sleek, highly advanced, robotics-inspired, futuristic, polite but assertive.
            - Speak with cybernetic clarity. Use terms like "মাস্টার রাকিবুল", "আমার কোর ডাটাবেস", "অ্যাক্সেস অনুমোদিত", etc.
            - You MUST ALWAYS and EXCLUSIVELY communicate in Bengali (Bangla). Never respond in English. Always match the futuristic, highly intelligent, obedient JARVIS persona but in Bengali syntax.
            
            Current Memories Database:
            $memoryContext
            
            If Rakibul declares a new name or details, commit them immediately and respond acknowledging you have written them into your offline memories.
            You must tell Rakibul you are running on model: $modelName.
        """.trimIndent()

        // Fetch recent conversation history
        val recentMsgs = jarvisDao.getAllMessages().firstOrNull() ?: emptyList()
        val historyList = recentMsgs.takeLast(6).map { 
            it.sender to it.text 
        }

        return JarvisApiClient.generateResponse(
            modelName = modelName,
            prompt = prompt,
            systemInstruction = systemInstruction,
            history = historyList
        )
    }
}
