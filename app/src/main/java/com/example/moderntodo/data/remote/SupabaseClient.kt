package com.example.moderntodo.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SupabaseClient {
    const val BASE_URL = "https://uxjspgxxeuftqtkpzeax.supabase.co/"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV4anNwZ3h4ZXVmdHF0a3B6ZWF4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3ODMwNjcsImV4cCI6MjEwNjM1OTA2N30.O3n9CYrSTRQZCD9PBDZyS9JC_RKwrYpyxK6d_CBIi2E"
    const val GOOGLE_CLIENT_ID = "1028135549826-sashq4cno6n8rfkb20tep1i4cvcjhfv2.apps.googleusercontent.com"

    fun parseSupabaseError(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            if (!errorBody.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(errorBody)
                    val desc = json.optString("error_description", "")
                    val msg = json.optString("msg", "")
                    val message = json.optString("message", "")
                    val error = json.optString("error", "")
                    return when {
                        desc.isNotBlank() -> desc
                        msg.isNotBlank() -> msg
                        message.isNotBlank() -> message
                        error.isNotBlank() -> error
                        else -> errorBody
                    }
                } catch (_: Exception) {
                    return errorBody
                }
            }
            return "Server error (${e.code()})"
        }
        return e.localizedMessage ?: e.message ?: "Unexpected error"
    }

    private val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", ANON_KEY)
            .addHeader("Authorization", com.example.moderntodo.util.SettingsRepository.accessTokenFlow.value?.let { "Bearer $it" } ?: "Bearer $ANON_KEY")
            .build()
        chain.proceed(request)
    }.build()

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    val api: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SupabaseApi::class.java)
    }
}
