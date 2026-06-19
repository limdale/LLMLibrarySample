package com.limdale.llm.litertlm

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
    private val modelRepository: ModelRepository
) : LLMLibrary {
    lateinit var engine: Engine
    lateinit var conversation: Conversation
    private val _status: MutableStateFlow<LLMStatus> = MutableStateFlow(LLMStatus.Initializing)
    override val status: StateFlow<LLMStatus> = _status

    private var llmSettings: LLMSettings? = null

    override suspend fun initialize(llmSettings: LLMSettings) {
        this.llmSettings = llmSettings
        val model = modelRepository.getModel(GEMMA_4_E2B_IT_LITERTLM.name)

        if (model == null) {
            _status.value = LLMStatus.Downloading(0f)
            modelRepository.downloadModel(GEMMA_4_E2B_IT_LITERTLM).collect {
                Log.d("LiteRtLLM", "LLM Status: $it")
                when (it) {
                    is ModelDownloadStatus.Done -> initializeEngine(it.model)
                    is ModelDownloadStatus.Error -> {}
                    is ModelDownloadStatus.Downloading -> {
                        _status.value = LLMStatus.Downloading(it.progress ?: 0f)
                    }
                }
            }
        } else {
            initializeEngine(model)
        }
    }

    fun initializeEngine(model: Model) {
        // Not 100% sure about this, not a fan of checking lateinit initialized, will update this
        if (::engine.isInitialized && engine.isInitialized()) {
            _status.value = LLMStatus.Ready
            return
        }

        _status.value = LLMStatus.Initializing
        val engineConfig = EngineConfig(
            modelPath = model.filePath,
            backend = Backend.GPU(),
            cacheDir = llmSettings?.cacheDir.orEmpty()
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
        _status.value = LLMStatus.Responding
        conversation.sendMessageAsync(prompt)
            .collect {
                response += it.toString()
            }
        _status.value = LLMStatus.Ready
        return response
    }
}