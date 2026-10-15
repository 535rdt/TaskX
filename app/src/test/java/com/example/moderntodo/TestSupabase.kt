package com.example.moderntodo

import kotlinx.coroutines.runBlocking
import com.example.moderntodo.data.remote.SupabaseClient
import com.example.moderntodo.data.local.TaskBookEntity

fun main() = runBlocking {
    try {
        val result = SupabaseClient.api.insertTaskBook(TaskBookEntity(name = "Test Book"))
        println("Success: ")
    } catch (e: Exception) {
        println("Error: ")
        if (e is retrofit2.HttpException) {
            println("Response: ")
        }
    }
}
