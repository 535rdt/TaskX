package com.example.moderntodo.data.remote

import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.data.local.TaskBookEntity
import retrofit2.http.*

interface SupabaseApi {
    @GET("rest/v1/task_books?select=*")
    suspend fun getTaskBooks(): List<TaskBookEntity>

    @POST("rest/v1/task_books")
    @Headers("Prefer: return=representation")
    suspend fun insertTaskBook(@Body book: TaskBookEntity): List<TaskBookEntity>

    @PATCH("rest/v1/task_books")
    suspend fun updateTaskBook(@Query("id") eq: String, @Body book: TaskBookEntity)

    @DELETE("rest/v1/task_books")
    suspend fun deleteTaskBook(@Query("id") eq: String)

    @GET("rest/v1/tasks?select=*")
    suspend fun getTasks(@Query("book_id") eq: String): List<TaskEntity>

    @GET("rest/v1/tasks?select=*")
    suspend fun getAllTasks(): List<TaskEntity>

    @POST("rest/v1/tasks")
    @Headers("Prefer: return=representation")
    suspend fun insertTask(@Body task: TaskEntity): List<TaskEntity>

    @PATCH("rest/v1/tasks")
    suspend fun updateTask(@Query("id") eq: String, @Body task: TaskEntity)

    @DELETE("rest/v1/tasks")
    suspend fun deleteTask(@Query("id") eq: String)


    @POST("auth/v1/signup")
    suspend fun signUpWithEmail(@Body request: EmailAuthRequest): AuthResponse

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithEmail(@Body request: EmailAuthRequest): AuthResponse
    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithIdToken(@Body request: IdTokenRequest): AuthResponse

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse
}
