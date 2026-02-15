package com.pirorin215.fastrecmob.viewModel.transcription

import android.content.Context
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.Settings
import com.pirorin215.fastrecmob.data.TranscriptionProvider
import com.pirorin215.fastrecmob.data.AIProvider
import com.pirorin215.fastrecmob.service.SpeechToTextService
import com.pirorin215.fastrecmob.service.GroqSpeechService
import com.pirorin215.fastrecmob.service.GeminiService
import com.pirorin215.fastrecmob.service.GroqLLMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine

/**
 * 文字起こし関連サービスの初期化と管理を担当するマネージャークラス
 * SpeechToTextService, GroqSpeechService, GeminiService, GroqLLMServiceの管理
 */
class TranscriptionServiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appSettingsRepository: AppSettingsRepository,
    private val logManager: com.pirorin215.fastrecmob.viewModel.LogManager
) {
    var speechToTextService: SpeechToTextService? = null
        private set

    var groqSpeechService: GroqSpeechService? = null
        private set

    var geminiService: GeminiService? = null
        private set

    var groqLLMService: GroqLLMService? = null
        private set

    /**
     * サービスの監視と初期化を開始
     */
    fun initializeServices() {
        // Watch transcription provider and API keys to initialize appropriate service
        appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).onEach { provider ->
            when (provider) {
                TranscriptionProvider.GOOGLE -> {
                    val apiKey = appSettingsRepository.getFlow(Settings.API_KEY).first()
                    if (apiKey.isNotBlank()) {
                        speechToTextService = SpeechToTextService(context, apiKey)
                        logManager.addDebugLog("Google Speech service initialized")
                    } else {
                        speechToTextService = null
                        logManager.addDebugLog("Google Speech service cleared: no API key")
                    }
                    groqSpeechService = null
                }
                TranscriptionProvider.GROQ -> {
                    val apiKey = appSettingsRepository.getFlow(Settings.GROQ_API_KEY).first()
                    if (apiKey.isNotBlank()) {
                        groqSpeechService = GroqSpeechService(context, apiKey)
                        logManager.addDebugLog("Groq Speech service initialized")
                    } else {
                        groqSpeechService = null
                        logManager.addDebugLog("Groq Speech service cleared: no API key")
                    }
                    speechToTextService = null
                }
            }
        }.launchIn(scope)

        // Also watch individual API key changes
        appSettingsRepository.getFlow(Settings.API_KEY)
            .onEach { apiKey ->
                val provider = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).first()
                if (provider == TranscriptionProvider.GOOGLE) {
                    if (apiKey.isNotBlank()) {
                        speechToTextService = SpeechToTextService(context, apiKey)
                        logManager.addDebugLog("Google Speech service initialized")
                    } else {
                        speechToTextService = null
                        logManager.addDebugLog("Speech service cleared: no API key")
                    }
                }
            }
            .launchIn(scope)

        appSettingsRepository.getFlow(Settings.GROQ_API_KEY)
            .onEach { apiKey ->
                val provider = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).first()
                if (provider == TranscriptionProvider.GROQ) {
                    if (apiKey.isNotBlank()) {
                        groqSpeechService = GroqSpeechService(context, apiKey)
                        logManager.addDebugLog("Groq Speech service initialized")
                    } else {
                        groqSpeechService = null
                        logManager.addDebugLog("Groq Speech service cleared: no API key")
                    }
                }
            }
            .launchIn(scope)

        // Initialize Gemini service for AI button feature
        appSettingsRepository.getFlow(Settings.GEMINI_API_KEY)
            .combine(appSettingsRepository.getFlow(Settings.GEMINI_MODEL)) { apiKey, model ->
                Pair(apiKey, model)
            }
            .onEach { (apiKey, model) ->
                if (apiKey.isNotBlank()) {
                    geminiService = GeminiService(context, apiKey, model.modelName)
                    logManager.addDebugLog("Gemini service initialized with model: ${model.modelName}")
                } else {
                    geminiService = null
                    logManager.addDebugLog("Gemini service cleared: no API key")
                }
            }
            .launchIn(scope)

        // Initialize Groq LLM service for AI button feature
        appSettingsRepository.getFlow(Settings.AI_PROVIDER)
            .combine(appSettingsRepository.getFlow(Settings.GROQ_API_KEY)) { provider, groqApiKey ->
                Pair(provider, groqApiKey)
            }
            .onEach { (provider, groqApiKey) ->
                if (provider == AIProvider.GROQ && groqApiKey.isNotBlank()) {
                    groqLLMService = GroqLLMService(context, groqApiKey)
                    logManager.addDebugLog("Groq LLM service initialized")
                } else {
                    groqLLMService = null
                    logManager.addDebugLog("Groq LLM service cleared")
                }
            }
            .launchIn(scope)
    }

    /**
     * 現在のプロバイダに対応するサービスを取得
     */
    fun getCurrentService(provider: TranscriptionProvider): Any? {
        return when (provider) {
            TranscriptionProvider.GOOGLE -> speechToTextService
            TranscriptionProvider.GROQ -> groqSpeechService
        }
    }

    /**
     * プロバイダ名を取得
     */
    fun getProviderName(provider: TranscriptionProvider): String {
        return when (provider) {
            TranscriptionProvider.GOOGLE -> "Google"
            TranscriptionProvider.GROQ -> "Groq"
        }
    }
}
