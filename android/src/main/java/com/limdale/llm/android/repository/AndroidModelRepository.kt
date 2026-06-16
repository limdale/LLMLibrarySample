package com.limdale.llm.android.repository

import android.content.Context
import android.os.Environment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.limdale.llm.android.repository.DownloadWorker.Companion.DOWNLOAD_FILE_DIRECTORY
import com.limdale.llm.android.repository.DownloadWorker.Companion.DOWNLOAD_FILE_NAME
import com.limdale.llm.android.repository.DownloadWorker.Companion.DOWNLOAD_URL
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

    companion object {
        private const val DOWNLOAD_MODEL_DIRECTORY = "llm_models"
    }

    val workManager = WorkManager.getInstance(context)
    private val downloadedModels: MutableMap<String, Model> = hashMapOf()

    private val downloadDir =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_MODEL_DIRECTORY)

    init {
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        downloadDir.listFiles()?.forEach { file ->
            downloadedModels[file.name] = Model(
                id = file.name,
                filePath = file.absolutePath
            )
        }
    }

    override fun downloadModel(modelDownload: ModelDownload): Flow<ModelDownloadStatus> {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DOWNLOAD_FILE_NAME to modelDownload.name,
                    DOWNLOAD_FILE_DIRECTORY to downloadDir.absolutePath,
                    DOWNLOAD_URL to modelDownload.url
                )
            )
            .build()

        workManager.enqueue(workRequest)

        return workManager.getWorkInfoByIdFlow(workRequest.id)
            .map { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val downloadedModelFile = File(
                            downloadDir,
                            modelDownload.name
                        ).absolutePath

                        ModelDownloadStatus.Done(
                            Model(
                                id = modelDownload.name,
                                filePath = downloadedModelFile
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
                        val progress = workInfo?.progress?.getFloat(DownloadWorker.PROGRESS, 0f)
                        ModelDownloadStatus.Downloading(modelDownload.url, progress)
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