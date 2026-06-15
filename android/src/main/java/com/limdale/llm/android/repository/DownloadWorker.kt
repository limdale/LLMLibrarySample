package com.limdale.llm.android.repository

import android.content.Context
import android.os.Environment
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
        const val Progress = "Progress"
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
            val response =
                downloadService.downloadModel("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true")
            val file = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "gemma-4-E2B-it.litertlm"
            )

            val total = response.contentLength()
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
                        Log.d("Test Dale", "Progress: ${(bytesCopied * 100 / total).toInt()}   $bytesCopied / $total")
                        setProgress(workDataOf(Progress to (bytesCopied * 100 / total).toInt()))
                    }
                }
            }

            setProgress(workDataOf(Progress to 100))
        }

        return Result.success()
    }
}
