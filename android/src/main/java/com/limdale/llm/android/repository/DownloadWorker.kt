package com.limdale.llm.android.repository

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.create
import java.io.File

data class DownloadWorker(
    val appContext: Context,
    val workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val PROGRESS = "Progress"
        const val DOWNLOAD_FILE_NAME = "DownloadFileName"
        const val DOWNLOAD_FILE_DIRECTORY = "DownloadFileDirectory"
        const val DOWNLOAD_URL = "DownloadUrl"
    }

    /**
     * Ideally this should either be:
     * 1. Injected via constructor (but WorkManager is a pain)
     * 2. Not even used - instead use raw HttpUrlConnection
     **/
    val downloadService = Retrofit.Builder()
        .baseUrl("https://test.com")
        .build()
        .create<DownloadService>()

    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            val downloadFileDirectory =
                workerParams.inputData.getString(DOWNLOAD_FILE_DIRECTORY).orEmpty()
            val downloadFileName = workerParams.inputData.getString(DOWNLOAD_FILE_NAME).orEmpty()
            val downloadUrl = workerParams.inputData.getString(DOWNLOAD_URL).orEmpty()
            val response = downloadService.downloadModel(downloadUrl)
            val file = File(downloadFileDirectory, downloadFileName)

            val totalBytes = response.contentLength()
            response.byteStream().use { inputStream ->
                file.outputStream().use { outputStream ->

                    // Copied from InputStream.copyTo
                    val bufferSize = DEFAULT_BUFFER_SIZE
                    var bytesCopied: Long = 0
                    val buffer = ByteArray(bufferSize)
                    var bytes = inputStream.read(buffer)
                    while (bytes >= 0) {
                        outputStream.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        bytes = inputStream.read(buffer)
                        Log.d(
                            "DownloadWorker",
                            "Downloading model $downloadFileName\nProgress: ${bytesCopied.toFloat() * 100 / totalBytes}   $bytesCopied / $totalBytes"
                        )
                        setProgress(workDataOf(PROGRESS to bytesCopied.toFloat() * 100 / totalBytes))
                    }
                }
            }

            setProgress(workDataOf(PROGRESS to 100f))
        }

        return Result.success()
    }
}
