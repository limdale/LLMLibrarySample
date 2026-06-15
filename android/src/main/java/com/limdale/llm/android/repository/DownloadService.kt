package com.limdale.llm.android.repository

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface DownloadService {
    @Streaming
    @GET
    suspend fun downloadModel(@Url url: String): ResponseBody
}