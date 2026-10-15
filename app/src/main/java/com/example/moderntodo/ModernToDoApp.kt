package com.example.moderntodo

import android.app.Application
import com.example.moderntodo.data.local.AppDatabase
import com.example.moderntodo.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.util.concurrent.ConcurrentHashMap

class ModernToDoApp : Application() {
    private val repositories = ConcurrentHashMap<String, TaskRepository>()

    val repository: TaskRepository
        get() = getRepository(com.example.moderntodo.util.SettingsRepository.userEmailFlow.value)

    fun getRepository(userEmail: String?): TaskRepository {
        val sanitized = AppDatabase.sanitizeUserEmail(userEmail)
        return repositories.computeIfAbsent(sanitized) {
            val db = AppDatabase.getDatabase(this, userEmail)
            val repo = TaskRepository(db.taskDao(), db.taskBookDao(), db.projectNoteDao(), userEmail)
            CoroutineScope(Dispatchers.IO).launch {
                repo.ensureInitialized()
            }
            repo
        }
    }

    companion object {
        lateinit var instance: ModernToDoApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.moderntodo.util.SettingsRepository.init(this)
        com.example.moderntodo.util.ProjectPreferences.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            repository.ensureInitialized()
        }
        com.example.moderntodo.data.remote.SharedBookRealtimeCoordinator.start(this) { repository }
    }
}
