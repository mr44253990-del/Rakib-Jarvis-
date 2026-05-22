package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object JarvisApiClient {
    private const val TAG = "JarvisApiClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Supported Models
    const val MODEL_GEMINI = "gemini-3.5-flash"
    const val MODEL_OPENCODE_MINIMAX = "opencode/minimax-m2.5-free"
    const val MODEL_OPENCODE_NEMOTRON = "opencode/nemotron-3-super-free"
    const val MODEL_OPENCODE_RING = "opencode/ring-2.6-1t-free"
    const val MODEL_OPENCODE_PICKLE = "opencode/big-pickle"
    const val MODEL_MISTRAL = "mistral-medium"

    /**
     * General completions method that routes the call to the appropriate API endpoint
     */
    suspend fun generateResponse(
        modelName: String,
        prompt: String,
        systemInstruction: String,
        history: List<Pair<String, String>> = emptyList() // Pair of <role, content> where role is "user" or "jarvis"
    ): String = withContext(Dispatchers.IO) {
        try {
            when {
                modelName == MODEL_GEMINI -> {
                    callGeminiApi(prompt, systemInstruction, history)
                }
                modelName.startsWith("opencode/") -> {
                    callOpenCodeApi(modelName, prompt, systemInstruction, history)
                }
                modelName == MODEL_MISTRAL -> {
                    callMistralApi(prompt, systemInstruction, history)
                }
                else -> {
                    callGeminiApi(prompt, systemInstruction, history)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response for $modelName: ${e.message}", e)
            "Error: ${e.message ?: "Unknown network exception occurred. Make sure your internet is stable."}"
        }
    }

    private fun callGeminiApi(
        prompt: String,
        systemInstruction: String,
        history: List<Pair<String, String>>
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Jarvis System Alert: Gemini API Key is missing. Please configure it in your Secrets."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject()
        val contentsArray = JSONArray()

        // Append historical turns first
        history.forEach { (role, content) ->
            val turn = JSONObject()
            val finalRole = if (role == "user") "user" else "model"
            turn.put("role", finalRole)
            
            val partsArr = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", content)
            partsArr.put(partObj)
            
            turn.put("parts", partsArr)
            contentsArray.put(turn)
        }

        // Current turn
        val currentTurn = JSONObject()
        currentTurn.put("role", "user")
        val currentPartsArr = JSONArray()
        val currentPartObj = JSONObject()
        currentPartObj.put("text", prompt)
        currentPartsArr.put(currentPartObj)
        currentTurn.put("parts", currentPartsArr)
        contentsArray.put(currentTurn)

        requestJson.put("contents", contentsArray)

        // System Instruction
        if (systemInstruction.isNotEmpty()) {
            val sysInstructionObj = JSONObject()
            val sysPartsArr = JSONArray()
            val sysPartObj = JSONObject()
            sysPartObj.put("text", systemInstruction)
            sysPartsArr.put(sysPartObj)
            sysInstructionObj.put("parts", sysPartsArr)
            requestJson.put("systemInstruction", sysInstructionObj)
        }

        val body = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: return "No response from Gemini API"
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini failed: Code ${response.code}, body: $responseBody")
                return "Module error (${response.code}): ${JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "API connection failed"}"
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            return parts?.optJSONObject(0)?.optString("text") ?: "Jarvis is looking for thoughts but found empty space."
        }
    }

    private fun callOpenCodeApi(
        modelName: String,
        prompt: String,
        systemInstruction: String,
        history: List<Pair<String, String>>
    ): String {
        // Opencode key (as supplied sk-0mjpE6oH1F5RqCvgtDjYYComjvtvkSBOMOjdzeofziB8GxHCFKDPN8AGkZeOnVcr)
        var apiKey = BuildConfig.OPEN_CODE_API_KEY
        if (apiKey.isEmpty() || apiKey == "sk-0mjpE6oH1F5RqCvgtDjYYComjvtvkSBOMOjdzeofziB8GxHCFKDPN8AGkZeOnVcr") {
            apiKey = "sk-0mjpE6oH1F5RqCvgtDjYYComjvtvkSBOMOjdzeofziB8GxHCFKDPN8AGkZeOnVcr" // Default to supplied key
        }

        // OpenCode utilizes an OpenAI-compatible endpoint. Standard endpoint or OpenRouter endpoint
        // Let's use openrouter as fallback or primary since the models are openrouter-compatible free ones!
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val requestJson = JSONObject()
        requestJson.put("model", modelName)

        val messagesArray = JSONArray()

        // System Instruction
        if (systemInstruction.isNotEmpty()) {
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", systemInstruction)
            messagesArray.put(systemMsg)
        }

        // History
        history.forEach { (role, content) ->
            val msg = JSONObject()
            msg.put("role", if (role == "user") "user" else "assistant")
            msg.put("content", content)
            messagesArray.put(msg)
        }

        // Current prompt
        val currentMsg = JSONObject()
        currentMsg.put("role", "user")
        currentMsg.put("content", prompt)
        messagesArray.put(currentMsg)

        requestJson.put("messages", messagesArray)
        requestJson.put("temperature", 0.7)

        val body = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://ai.studio") // Required by some OpenRouter configurations
            .addHeader("X-Title", "Jarvis Assistant AI Studio")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: return "No response from OpenCode API"
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenCode failed: Code ${response.code}, body: $responseBody")
                return "Module error ${response.code}: $responseBody"
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            return message?.optString("content") ?: "OpenCode model failed to return text."
        }
    }

    private fun callMistralApi(
        prompt: String,
        systemInstruction: String,
        history: List<Pair<String, String>>
    ): String {
        var apiKey = BuildConfig.MISTRAL_API_KEY
        if (apiKey.isEmpty() || apiKey == "EBuoQ4q3D8IcTu1xixZcmf73hNI3tWcB") {
            apiKey = "EBuoQ4q3D8IcTu1xixZcmf73hNI3tWcB" // Fallback to user provided key
        }

        val url = "https://api.mistral.ai/v1/chat/completions"

        val requestJson = JSONObject()
        requestJson.put("model", "mistral-medium-latest")

        val messagesArray = JSONArray()

        // System Instruction
        if (systemInstruction.isNotEmpty()) {
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", systemInstruction)
            messagesArray.put(systemMsg)
        }

        // History
        history.forEach { (role, content) ->
            val msg = JSONObject()
            msg.put("role", if (role == "user") "user" else "assistant")
            msg.put("content", content)
            messagesArray.put(msg)
        }

        // Current prompt
        val currentMsg = JSONObject()
        currentMsg.put("role", "user")
        currentMsg.put("content", prompt)
        messagesArray.put(currentMsg)

        requestJson.put("messages", messagesArray)

        val body = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: return "No response from Mistral AI"
            if (!response.isSuccessful) {
                Log.e(TAG, "Mistral AI failed: Code ${response.code}, body: $responseBody")
                return "Mistral Error (${response.code}): $responseBody"
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            return message?.optString("content") ?: "Mistral model failed to return response text."
        }
    }
}
