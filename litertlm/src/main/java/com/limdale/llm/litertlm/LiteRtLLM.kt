package com.limdale.llm.litertlm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.limdale.llm.LLMLibrary
import com.limdale.llm.LLMSettings
import com.limdale.llm.LLMStatus
import com.limdale.llm.model.Model
import com.limdale.llm.model.ModelDownloadStatus
import com.limdale.llm.model.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class LiteRtLLM(
    private val context: Context,
    private val modelRepository: ModelRepository
) : LLMLibrary {
    lateinit var engine: Engine
    lateinit var conversation: Conversation
    private val _status: MutableStateFlow<LLMStatus> = MutableStateFlow(LLMStatus.Initializing)
    override val status: StateFlow<LLMStatus> = _status

    override fun initialize(llmSettings: LLMSettings) {
        runBlocking {
            initializeAsync()
        }
    }

    suspend fun initializeAsync() {
        val modelName = "gemma-4-E2B-it.litertlm"
        val model = modelRepository.getModel(modelName)

        if (model == null) {
            Log.d("LiteRtLLM", "Model $modelName not found, downloading...")
            _status.value = LLMStatus.Downloading(0f)
            modelRepository.downloadModel(
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
                modelName
            ).collect {
                when (it) {
                    is ModelDownloadStatus.Done -> {
                        initializeEngine(it.model)
                    }

                    is ModelDownloadStatus.Error -> {
                        Log.d("LiteRtLLM", "Failed to download model ${it.url}: ${it.message}")
                    }

                    is ModelDownloadStatus.Downloading -> {
                        Log.d("LiteRtLLM", "Downloading model: ${it.url}")
                    }
                }
            }
        } else {
            initializeEngine(model)
        }
    }

    fun initializeEngine(model: Model) {
        Log.d("LiteRtRecommender", "Initializing Engine Config")
        _status.value = LLMStatus.Initializing
        val engineConfig = EngineConfig(
            modelPath = model.filePath,
            backend = Backend.GPU(),
            cacheDir = context.cacheDir.path
        )

        engine = Engine(engineConfig)
        engine.initialize()
        conversation = engine.createConversation()
        _status.value = LLMStatus.Ready
    }

    override fun systemInstruction(instruction: String) {

    }

    override suspend fun prompt(prompt: String): String {
        var response = ""
        conversation.sendMessageAsync(prompt)
            .collect {
                response += it.toString()
            }
        return response
    }
}