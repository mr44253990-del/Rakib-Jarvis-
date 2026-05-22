package com.example.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class JarvisAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisAssistantSession(this)
    }
}

class JarvisAssistantSession(context: android.content.Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
        // Here we could show a custom HUD like the app's UI
    }
}
