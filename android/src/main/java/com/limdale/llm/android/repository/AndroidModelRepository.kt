package com.limdale.llm.android.repository

import android.content.Context
import android.os.Environment
import android.util.Log
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Uses WorkManager and Retrofit to download models.
 * Stores models in app's [context.getExternalFilesDir()/llm_models]
 */
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun downloadModel(modelDownload: ModelDownload): Flow<ModelDownloadStatus> {
        return workManager.getWorkInfosByTagFlow(modelDownload.name)
            .flatMapConcat { workInfos ->
                if (workInfos.isEmpty()) {
                    startNewDownload(modelDownload)
                    workManager.getWorkInfosByTagFlow(modelDownload.name).map { it.first() }
                } else {
                    Log.i("AndroidModelRepository", "Existing work found for download $modelDownload: $workInfos")
                    flow { workInfos.first() }
                }
            }
            .map { workInfo ->
                when (workInfo.state) {
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
                        val progress = workInfo.progress.getFloat(DownloadWorker.PROGRESS, 0f)
                        ModelDownloadStatus.Downloading(modelDownload.url, progress)
                    }
                }
            }
    }

    private fun startNewDownload(modelDownload: ModelDownload) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DOWNLOAD_FILE_NAME to modelDownload.name,
                    DOWNLOAD_FILE_DIRECTORY to downloadDir.absolutePath,
                    DOWNLOAD_URL to modelDownload.url
                )
            )
            .addTag(modelDownload.name)
            .build()

        workManager.enqueue(workRequest)
    }

    override fun getModel(name: String): Model? {
        return downloadedModels[name]
    }

    override fun getModels(): List<Model> {
        return downloadedModels.values.toList()
    }
}