package com.limdale.llm

sealed class LLMStatus {
    data class Downloading(val progress: Float) : LLMStatus()
    data object Initializing : LLMStatus()
    data object Ready : LLMStatus()
    data object Responding : LLMStatus()
}
