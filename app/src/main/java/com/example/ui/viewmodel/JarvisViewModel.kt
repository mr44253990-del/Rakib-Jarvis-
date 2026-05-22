package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.JarvisApiClient
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.Memory
import com.example.data.model.Note
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JarvisRepository
    private val prefs = application.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = JarvisRepository(database.jarvisDao())
        
        // Seed default database memories about Rakibul if they are empty
        viewModelScope.launch {
            repository.allMemories.first().let { current ->
                if (current.isEmpty()) {
                    repository.insertMemory(Memory(fact = "User Name: Rakibul"))
                    repository.insertMemory(Memory(fact = "Email ID: mr4425390@gmail.com"))
                    repository.insertMemory(Memory(fact = "User Identity: Supreme Creator and Architect of JARVIS assistant."))
                    repository.insertMemory(Memory(fact = "Local Dhaka coordinate reference set: Bangladesh Standard Time."))
                    repository.insertMemory(Memory(fact = "Privilege Status: Developer access fully authorized."))
                }
            }
        }
    }

    // UI States
    val chatMessages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<Memory>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Input states
    private val _currentPrompt = MutableStateFlow("")
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _selectedModel = MutableStateFlow(JarvisApiClient.MODEL_GEMINI)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _ttsSpeakTrigger = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val ttsSpeakTrigger: SharedFlow<String> = _ttsSpeakTrigger.asSharedFlow()

    // Google Auth States
    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _scopeGmailGranted = MutableStateFlow(true)
    val scopeGmailGranted: StateFlow<Boolean> = _scopeGmailGranted.asStateFlow()

    private val _scopeCalendarGranted = MutableStateFlow(true)
    val scopeCalendarGranted: StateFlow<Boolean> = _scopeCalendarGranted.asStateFlow()

    private val _scopeDriveGranted = MutableStateFlow(true)
    val scopeDriveGranted: StateFlow<Boolean> = _scopeDriveGranted.asStateFlow()

    private val _scopeYoutubeGranted = MutableStateFlow(true)
    val scopeYoutubeGranted: StateFlow<Boolean> = _scopeYoutubeGranted.asStateFlow()

    // Backup state
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    fun updatePrompt(text: String) {
        _currentPrompt.value = text
    }

    fun selectModel(model: String) {
        _selectedModel.value = model
    }

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
    }

    fun toggleGmailScope() { _scopeGmailGranted.value = !_scopeGmailGranted.value }
    fun toggleCalendarScope() { _scopeCalendarGranted.value = !_scopeCalendarGranted.value }
    fun toggleDriveScope() { _scopeDriveGranted.value = !_scopeDriveGranted.value }
    fun toggleYoutubeScope() { _scopeYoutubeGranted.value = !_scopeYoutubeGranted.value }

    fun login(email: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            kotlinx.coroutines.delay(1800) // Cyber auth logic
            
            // Save login state permanently
            prefs.edit().putBoolean("is_logged_in", true).apply()
            
            _isLoggedIn.value = true
            _isAuthenticating.value = false

            // Welcome from Jarvis
            val greetingText = "অ্যাক্সেস অনুমোদিত। স্বাগতম মাস্টার রাকিবুল। আমার সমস্ত সিস্টেম এবং ইন্টারফেস এখন সম্পূর্ণ কার্যকর। আপনার কোর ডেটাবেস লোড করা হয়েছে। আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
            repository.insertMessage(ChatMessage(sender = "jarvis", text = greetingText))
            if (_ttsEnabled.value) {
                _ttsSpeakTrigger.tryEmit(greetingText)
            }
        }
    }

    fun logout() {
        prefs.edit().putBoolean("is_logged_in", false).apply()
        _isLoggedIn.value = false
    }

    fun sendMessage() {
        val userPrompt = _currentPrompt.value.trim()
        if (userPrompt.isEmpty()) return

        _currentPrompt.value = ""
        
        viewModelScope.launch {
            // 1. Insert user message to Room
            repository.insertMessage(ChatMessage(sender = "user", text = userPrompt))
            _isGenerating.value = true

            // 2. Fetch current memories for context
            val currentMemories = memories.value

            // 3. Request AI response
            val finalResponse = repository.askJarvis(
                modelName = _selectedModel.value,
                prompt = userPrompt,
                memories = currentMemories
            )

            // 4. Insert Assistant response
            repository.insertMessage(ChatMessage(sender = "jarvis", text = finalResponse))
            _isGenerating.value = false

            // 5. Speak response if TTS is activated
            if (_ttsEnabled.value) {
                _ttsSpeakTrigger.tryEmit(finalResponse)
            }
        }
    }

    // Notes adding
    fun addNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            val noteId = repository.insertNote(
                Note(title = title, content = content)
            )
            // Auto back up notes to Google Drive simulation!
            if (_scopeDriveGranted.value) {
                backupNoteToDrive(noteId.toInt(), title)
            }
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun triggerManualBackup() {
        viewModelScope.launch {
            _isBackingUp.value = true
            kotlinx.coroutines.delay(2000) // Beautiful sync animation
            notes.value.forEach { note ->
                if (!note.isSynced) {
                    repository.updateNoteSyncStatus(note.id, isSynced = true)
                }
            }
            _isBackingUp.value = false
        }
    }

    private suspend fun backupNoteToDrive(noteId: Int, title: String) {
        // Simulate real drive upload network call
        kotlinx.coroutines.delay(1000)
        repository.updateNoteSyncStatus(noteId, isSynced = true)
    }

    // Memories management
    fun addMemoryRaw(fact: String) {
        if (fact.isBlank()) return
        viewModelScope.launch {
            repository.insertMemory(Memory(fact = fact))
        }
    }

    fun deleteMemory(id: Int) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }
}
