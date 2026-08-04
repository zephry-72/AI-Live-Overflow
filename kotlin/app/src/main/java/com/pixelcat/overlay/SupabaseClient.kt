package com.pixelcat.overlay

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

class SupabaseClient(
    private val projectUrl: String,
    private val anonKey: String
) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "$projectUrl/rest/v1"
    private val jsonMediaType = "application/json".toMediaType()

    companion object {
        const val PROJECT_URL = "https://loclplmnrqmjjymstcvy.supabase.co"
        const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2xwbG1ucnFtamp5bXN0Y3Z5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU3MTk3MzUsImV4cCI6MjEwMTI5NTczNX0.rFJUPNPMIpP_P_h_sYiLDIXuS29NoiOFMFEoLrfujlo"
        private var instance: SupabaseClient? = null
        fun getInstance(): SupabaseClient {
            if (instance == null) instance = SupabaseClient(PROJECT_URL, ANON_KEY)
            return instance!!
        }
    }

    data class PetEvent(
        val id: String = UUID.randomUUID().toString(),
        val action: String,
        val emotion: String,
        val bubbleText: String? = null,
        val timestamp: String = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date()),
        val device_id: String = android.provider.Settings.Secure.ANDROID_ID
    )

    fun logEvent(
        action: String,
        emotion: String,
        bubbleText: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val event = PetEvent(action = action, emotion = emotion, bubbleText = bubbleText)
        val body = gson.toJson(event).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/pet_events")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError?.invoke(e.message ?: "网络错误") }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                if (response.isSuccessful) onSuccess?.invoke()
                else onError?.invoke("HTTP ${response.code}")
            }
        })
    }

    fun getRecentEvents(
        limit: Int = 20,
        onResult: (List<PetEvent>) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val request = Request.Builder()
            .url("$baseUrl/pet_events?select=*&order=timestamp.desc&limit=$limit")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .get()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError?.invoke(e.message ?: "网络错误") }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"
                response.close()
                try {
                    val type = com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java, PetEvent::class.java
                    ).type
                    onResult(gson.fromJson(body, type))
                } catch (e: Exception) { onError?.invoke("解析错误: ${e.message}") }
            }
        })
    }
}
