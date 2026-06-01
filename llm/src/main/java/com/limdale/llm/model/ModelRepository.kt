package com.limdale.llm.model

import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun downloadModel(url: String, name: String): Flow<ModelDownloadStatus>
    fun getModel(name: String): Model?
    fun getModels(): List<Model>
}

sealed class ModelDownloadStatus {
    data class Downloading(val url: String) : ModelDownloadStatus()
    data class Done(val model: Model) : ModelDownloadStatus()
    data class Error(val url: String, val message: String) : ModelDownloadStatus()
}
