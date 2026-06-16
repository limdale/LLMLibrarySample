package com.limdale.llm.android.repository

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import com.limdale.llm.model.Model
import com.limdale.llm.model.ModelDownload
import com.limdale.llm.model.ModelDownloadStatus
import com.limdale.llm.model.ModelRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

// Uses DownloadManager to download, highly inefficient but works for initial testing
class TestModelRepository(val context: Context) : ModelRepository {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val downloadedModels: MutableMap<String, Model> = hashMapOf()

    init {
        val downloadedModel = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "gemma-4-E2B-it.litertlm"
        )
        if (downloadedModel.exists()) {
            downloadedModels["gemma-4-E2B-it.litertlm"] = Model(
                id = "gemma-4-E2B-it.litertlm",
                filePath = downloadedModel.absolutePath
            )
        }
    }

    override fun downloadModel(modelDownload: ModelDownload): Flow<ModelDownloadStatus> = flow {
        val downloadId = downloadManager.enqueue(
            DownloadManager.Request(modelDownload.url.toUri()).setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS, modelDownload.name
            )
        )

        while (true) {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                val fileIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val fileUri = cursor.getString(fileIndex)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val reason = cursor.getInt(reasonIndex)

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val model = Model(
                        id = modelDownload.name,
                        filePath = File(
                            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), modelDownload.name
                        ).absolutePath
                    )
                    downloadedModels[modelDownload.name] = model
                    emit(ModelDownloadStatus.Done(model))
                    Log.d("DownloadManagerRepository", "Download successful: $fileUri, $model")
                    break
                } else if (status == DownloadManager.STATUS_FAILED) {
                    emit(ModelDownloadStatus.Error(modelDownload.url, "Download failed: $reason"))
                    Log.d("DownloadManagerRepository", "Download failed: $reason")
                    break
                } else {
                    emit(ModelDownloadStatus.Downloading(modelDownload.url))
                }
            }
            cursor.close()
            delay(1000L)
        }
    }

    override fun getModel(name: String): Model? {
        return downloadedModels[name]
    }

    override fun getModels(): List<Model> {
        return downloadedModels.values.toList()
    }
}