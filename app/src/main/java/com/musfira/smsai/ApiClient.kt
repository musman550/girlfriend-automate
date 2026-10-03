package com.musfira.smsai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiHttpException(val code: Int, snippet: String) : IOException("HTTP $code: $snippet")

object ApiClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private suspend fun run(rb: Request.Builder, bearer: String?, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            if (!bearer.isNullOrEmpty()) rb.header("Authorization", "Bearer $bearer")
            headers.forEach { (k, v) -> rb.header(k, v) }
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiHttpException(resp.code, text.take(160).replace("\n", " "))
                text
            }
        }

    suspend fun postJson(url: String, json: String, bearer: String? = null, headers: Map<String, String> = emptyMap()): String =
        run(Request.Builder().url(url).post(json.toRequestBody(JSON)), bearer, headers)

    suspend fun getJson(url: String, bearer: String? = null, headers: Map<String, String> = emptyMap()): String =
        run(Request.Builder().url(url).get(), bearer, headers)
}
