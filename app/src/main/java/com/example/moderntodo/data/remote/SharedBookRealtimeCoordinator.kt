package com.example.moderntodo.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.moderntodo.repository.TaskRepository
import com.example.moderntodo.util.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SharedBookRealtimeCoordinator {
    private const val TAG = "SharedRealtime"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isLiveSyncing = MutableStateFlow(false)
    val isLiveSyncing: StateFlow<Boolean> = _isLiveSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _activeBookId = MutableStateFlow<Int?>(null)
    val activeBookId: StateFlow<Int?> = _activeBookId.asStateFlow()

    private var isStarted = false
    private var syncJob: Job? = null

    fun setActiveBook(bookId: Int?) {
        _activeBookId.value = bookId
    }

    /**
     * Initializes background real-time sync ticker (every 5-6 seconds when online),
     * matching LOSE EXP shared book behavior.
     */
    fun start(context: Context, repositoryProvider: () -> TaskRepository) {
        if (isStarted) return
        isStarted = true

        syncJob = scope.launch {
            while (isActive) {
                try {
                    delay(5500)
                    if (isOnline(context)) {
                        val repo = repositoryProvider()
                        val userEmail = SettingsRepository.userEmailFlow.value
                        val userName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "You"

                        val books = repo.allTaskBooksList()
                        val currentActive = _activeBookId.value

                        // Prioritize active book first
                        val sortedBooks = if (currentActive != null) {
                            val active = books.filter { it.id == currentActive }
                            val others = books.filter { it.id != currentActive }
                            active + others
                        } else {
                            books
                        }

                        for (book in sortedBooks) {
                            val bId = book.id ?: continue
                            val code = SharedBookService.getSavedInviteCode(context, bId, userEmail)
                            if (!code.isNullOrBlank()) {
                                SharedBookService.syncSharedBookCatchUp(
                                    context = context,
                                    repository = repo,
                                    bookId = bId,
                                    currentUserName = userName,
                                    currentUserEmail = userEmail,
                                    force = false
                                )
                            }
                        }
                        _lastSyncTimestamp.value = System.currentTimeMillis()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "Ticker sync cycle: ${e.message}")
                }
            }
        }
    }

    /**
     * Immediately pushes an updated snapshot to Supabase whenever a task/note
     * is created, edited, toggled, or deleted locally.
     */
    fun notifyLocalMutation(context: Context, repository: TaskRepository, bookId: Int) {
        scope.launch {
            try {
                val userEmail = SettingsRepository.userEmailFlow.value
                val inviteCode = SharedBookService.getSavedInviteCode(context, bookId, userEmail)
                if (!inviteCode.isNullOrBlank()) {
                    _isLiveSyncing.value = true
                    val ownerName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "You"
                    val payload = SharedBookService.exportBookPayload(
                        context = context,
                        repository = repository,
                        bookId = bookId,
                        ownerName = ownerName,
                        ownerEmail = userEmail
                    )
                    if (payload != null) {
                        SharedBookService.publishToCloud(payload)
                        _lastSyncTimestamp.value = System.currentTimeMillis()
                        Log.d(TAG, "Live mutation published for book $bookId ($inviteCode)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "notifyLocalMutation failed: ${e.message}")
            } finally {
                _isLiveSyncing.value = false
            }
        }
    }

    /**
     * Forces an immediate two-way sync for a specific book (e.g. for pull-to-refresh).
     */
    fun triggerManualSync(
        context: Context,
        repository: TaskRepository,
        bookId: Int,
        onComplete: ((SyncResult) -> Unit)? = null
    ) {
        scope.launch {
            _isLiveSyncing.value = true
            try {
                val userEmail = SettingsRepository.userEmailFlow.value
                val ownerName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "You"
                val result = SharedBookService.syncSharedBook(
                    context = context,
                    repository = repository,
                    bookId = bookId,
                    currentUserName = ownerName,
                    currentUserEmail = userEmail
                )
                _lastSyncTimestamp.value = System.currentTimeMillis()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(SyncResult(false, "Sync failed: ${e.message}"))
                }
            } finally {
                _isLiveSyncing.value = false
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
