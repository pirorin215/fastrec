package com.pirorin215.fastrecmob.service

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for interacting with Gemini API to generate AI responses
 */
class GeminiService(
    private val context: Context,
    private val apiKey: String,
    private val modelName: String = "gemini-2.0-flash"
) {
    private var generativeModel: GenerativeModel? = null

    init {
        if (apiKey.isNotBlank()) {
            try {
                generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Generate an AI response based on the transcription text
     */
    suspend fun generateResponse(transcription: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val model = generativeModel ?: throw IllegalStateException("Gemini API key not set")

                // Create prompt for generating a helpful response
                val prompt = buildString {
                    append("以下の発言内容に対して、役立つ応答を簡潔に生成してください：\n\n")
                    append(transcription)
                    append("\n\n応答は100文字以内で実的にしてください。")
                }

                val response = model.generateContent(prompt)
                val text = response.text ?: "応答が生成されませんでした"

                Result.success(text)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Verify that the API key is valid
     */
    suspend fun verifyApiKey(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API key is empty"))
            }
            try {
                val model = generativeModel ?: throw IllegalStateException("Model not initialized")
                // Try to generate a simple test response
                model.generateContent("test")
                Result.success(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }
}
