package com.limdale.llm

import kotlinx.coroutines.flow.StateFlow

interface LLMLibrary {
    val status: StateFlow<LLMStatus>
    fun initialize(llmSettings: LLMSettings)
    suspend fun prompt(prompt: String): String
    fun systemInstruction(instruction: String)
}