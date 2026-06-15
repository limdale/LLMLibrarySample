package com.limdale.llm.android.repository

import android.content.Context
import android.os.Environment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.limdale.llm.model.Model
import com.limdale.llm.model.ModelDownload
import com.limdale.llm.model.ModelDownloadStatus
import com.limdale.llm.model.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class AndroidModelRepository(
    val context: Context,
) : ModelRepository {
    val workManager = WorkManager.getInstance(context)
    private val downloadedModels: MutableMap<String, Model> = hashMapOf()

    init {
        val downloadedModel = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "gemma-4-E2B-it.litertlm"
        )
        if (downloadedModel.exists()) {
            downloadedModels["gemma-4-E2B-it.litertlm"] = Model(
                id = "gemma-4-E2B-it.litertlm",
                url = "",
                filePath = downloadedModel.absolutePath
            )
        }
    }

    override fun downloadModel(modelDownload: ModelDownload): Flow<ModelDownloadStatus> {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .build()
        workManager.enqueue(workRequest)

        return workManager.getWorkInfoByIdFlow(workRequest.id)
            .map { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val downloadedModel = File(
                            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                            "gemma-4-E2B-it.litertlm"
                        ).absolutePath

                        ModelDownloadStatus.Done(
                            Model(
                                id = modelDownload.name,
                                url = modelDownload.url,
                                filePath = downloadedModel
                            )
                        )
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        ModelDownloadStatus.Error(
                            url = modelDownload.url,
                            message = "Work failed: ${workInfo.stopReason}"
                        )
                    }

                    else -> {
                        ModelDownloadStatus.Downloading(modelDownload.url)
                    }
                }
            }
    }

    override fun getModel(name: String): Model? {
        return downloadedModels[name]
    }

    override fun getModels(): List<Model> {
        return downloadedModels.values.toList()
    }
}