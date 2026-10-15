package com.example.moderntodo.data.remote

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.example.moderntodo.data.local.ProjectNoteEntity
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.repository.TaskRepository
import com.example.moderntodo.util.ProjectPreferences
import com.example.moderntodo.util.SettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

interface SharedBooksRetrofitApi {
    @GET("rest/v1/shared_books?select=*")
    suspend fun fetchBook(@Query(value = "id", encoded = true) idFilter: String): List<SharedBookRow>

    @GET("rest/v1/shared_books?select=*")
    suspend fun fetchBooksQuery(@Query(value = "or", encoded = true) orFilter: String): List<SharedBookRow>

    @GET("rest/v1/shared_books?select=id,updated_at")
    suspend fun fetchBookHeader(@Query(value = "id", encoded = true) idFilter: String): List<SharedBookHeaderRow>

    @POST("rest/v1/shared_books")
    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    suspend fun upsertBook(@Body body: SharedBookRow): List<SharedBookRow>
}

object SharedBookService {
    private const val BASE_URL = "https://vfihkumkdhzlmsnkdkat.supabase.co/"
    private const val API_KEY = "sb_publishable_oyGlSgFHE8lvufy1sImjAA_Zum5TwGn"

    private val gson = Gson()

    val MEMBER_AVATAR_COLORS = listOf(
        "#4C59D9",
        "#10B981",
        "#F59E0B",
        "#EC4899",
        "#8B5CF6",
        "#06B6D4",
        "#EF4444",
        "#14B8A6"
    )

    private val api: SharedBooksRetrofitApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", API_KEY)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }.build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SharedBooksRetrofitApi::class.java)
    }

    /**
     * Generates a clean 6-7 character invite code like LOSE EXP (e.g. PRJ-4K2A, DOIT-8X92).
     */
    fun generateInviteCode(bookName: String, bookId: Int): String {
        val cleanName = bookName.replace(Regex("[^a-zA-Z0-9]"), "").uppercase(Locale.ROOT)
        val prefix = if (cleanName.length >= 3) cleanName.take(3) else cleanName.padEnd(3, 'X')
        val num = Math.abs((bookId * 9301 + 49297) % 233280)
        val hash = java.lang.Long.toString(num.toLong(), 36).uppercase(Locale.ROOT).padStart(4, '7').takeLast(4)
        return "$prefix-$hash"
    }

    /**
     * Encodes a payload into a robust single-string share token: DOIT-BOOK:[base64]
     */
    fun encodeToken(payload: SharedBookPayload): String {
        val json = gson.toJson(payload)
        val base64 = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "DOIT-BOOK:$base64"
    }

    /**
     * Publishes the snapshot payload to Supabase shared_books table.
     */
    suspend fun publishToCloud(payload: SharedBookPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val row = SharedBookRow(
                id = payload.inviteCode.uppercase(Locale.ROOT),
                bookName = payload.book.name,
                ownerName = payload.owner.name,
                payload = payload,
                updatedAt = System.currentTimeMillis()
            )
            val result = api.upsertBook(row)
            Log.d("SharedBookService", "publishToCloud succeeded for ${payload.inviteCode}: returned ${result.size} rows")
            result.isNotEmpty()
        } catch (e: Exception) {
            Log.e("SharedBookService", "publishToCloud failed for ${payload.inviteCode}: ${e.message}", e)
            false
        }
    }

    /**
     * Fetches a shared book payload from Supabase by invite code, supporting
     * case-insensitivity, optional hyphens, and whitespace tolerance.
     */
    suspend fun fetchFromCloud(inviteCode: String): SharedBookPayload? = withContext(Dispatchers.IO) {
        val trimmed = inviteCode.trim()
        if (trimmed.isBlank()) return@withContext null

        val upper = trimmed.uppercase(Locale.ROOT)
        val noHyphen = upper.replace("-", "")
        val withHyphen = if (!upper.contains("-") && upper.length in 7..8) {
            val prefixLen = if (upper.length == 7) 3 else 4
            upper.substring(0, prefixLen) + "-" + upper.substring(prefixLen)
        } else upper

        try {
            val candidates = linkedSetOf(upper, withHyphen, noHyphen, trimmed)
            val orConditions = candidates.joinToString(",") { "id.eq.$it,id.ilike.$it" }
            Log.d("SharedBookService", "fetchFromCloud querying: or=($orConditions)")
            val list = api.fetchBooksQuery("($orConditions)")
            if (list.isNotEmpty()) {
                Log.d("SharedBookService", "fetchFromCloud found: ${list.first().id}")
                return@withContext list.first().payload
            }
        } catch (e: Exception) {
            Log.w("SharedBookService", "fetchBooksQuery failed: ${e.message}, attempting direct eq lookup")
            try {
                val list = api.fetchBook("eq.$upper")
                if (list.isNotEmpty()) return@withContext list.first().payload
            } catch (e2: Exception) {
                Log.e("SharedBookService", "direct eq lookup also failed: ${e2.message}")
            }
        }
        Log.w("SharedBookService", "fetchFromCloud: No book found for code: $trimmed")
        null
    }

    /**
     * Robust parser: extracts SharedBookPayload from ANY input:
     * - 6-8 character Invite Code (e.g. PRJ-4K2A, PRJ4K2A, "Code: PRJ-4K2A")
     * - Full DOIT-BOOK:... or LOSEXP-BOOK:... token
     * - Raw JSON string
     */
    suspend fun parseSharedInput(input: String): SharedBookPayload? = withContext(Dispatchers.IO) {
        val raw = input.trim()
        if (raw.isBlank()) return@withContext null

        // 1. Token format DOIT-BOOK:... or LOSEXP-BOOK:...
        if (raw.contains("DOIT-BOOK:") || raw.contains("LOSEXP-BOOK:")) {
            val regex = Regex("""(DOIT-BOOK:|LOSEXP-BOOK:)([A-Za-z0-9+/=_\-\s]+)""")
            val match = regex.find(raw)
            if (match != null) {
                try {
                    val cleanBase64 = match.groupValues[2].replace(Regex("""[\s\r\n]+"""), "")
                    val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    val jsonStr = String(decodedBytes, Charsets.UTF_8)
                    return@withContext gson.fromJson(jsonStr, SharedBookPayload::class.java)
                } catch (e: Exception) {
                    Log.w("SharedBookService", "Failed to decode base64 token: ${e.message}")
                }
            }
        }

        // 2. Raw JSON string
        if (raw.startsWith("{") && raw.endsWith("}")) {
            try {
                return@withContext gson.fromJson(raw, SharedBookPayload::class.java)
            } catch (_: Exception) {}
        }

        // 3. Short Invite Code: try regex pattern to extract clean code if surrounded by extra text
        val codePattern = Regex("""\b([A-Za-z0-9]{3,4})-?([A-Za-z0-9]{4})\b""")
        val codeMatch = codePattern.find(raw)
        if (codeMatch != null) {
            val formatted = "${codeMatch.groupValues[1].uppercase(Locale.ROOT)}-${codeMatch.groupValues[2].uppercase(Locale.ROOT)}"
            val payload = fetchFromCloud(formatted)
            if (payload != null) return@withContext payload
        }

        // 4. Raw code fallback
        val cleanCode = raw.replace(Regex("""\s+"""), "").uppercase(Locale.ROOT)
        fetchFromCloud(cleanCode)
    }

    fun recordDeletedTask(context: Context, bookId: Int, syncId: String, userEmail: String? = null) {
        if (syncId.isBlank()) return
        val prefs = getPrefs(context, userEmail)
        val current = prefs.getStringSet("deleted_tasks_$bookId", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(syncId)
        prefs.edit().putStringSet("deleted_tasks_$bookId", current).apply()
    }

    fun getDeletedTaskSyncIds(context: Context, bookId: Int, userEmail: String? = null): List<String> {
        val prefs = getPrefs(context, userEmail)
        return prefs.getStringSet("deleted_tasks_$bookId", emptySet())?.toList() ?: emptyList()
    }

    fun clearDeletedTaskSyncIds(context: Context, bookId: Int, userEmail: String? = null) {
        val prefs = getPrefs(context, userEmail)
        prefs.edit().remove("deleted_tasks_$bookId").apply()
    }

    suspend fun fetchCloudUpdatedAt(inviteCode: String): Long? = withContext(Dispatchers.IO) {
        try {
            val upper = inviteCode.trim().uppercase(Locale.ROOT)
            val headers = api.fetchBookHeader("eq.$upper")
            if (headers.isNotEmpty()) {
                return@withContext headers.first().updatedAt
            }
        } catch (e: Exception) {
            Log.d("SharedBookService", "fetchCloudUpdatedAt header error: ${e.message}")
        }
        null
    }

    /**
     * Exports a complete self-contained snapshot payload of a book with ALL its tasks,
     * project notes, and members.
     */
    suspend fun exportBookPayload(
        context: Context,
        repository: TaskRepository,
        bookId: Int,
        ownerName: String,
        ownerEmail: String?
    ): SharedBookPayload? = withContext(Dispatchers.IO) {
        val allBooks = repository.allTaskBooksList()
        val book = allBooks.find { it.id == bookId } ?: return@withContext null

        val inviteCode = getSavedInviteCode(context, bookId, ownerEmail) ?: run {
            val code = generateInviteCode(book.name, bookId)
            saveInviteCode(context, bookId, code, ownerEmail)
            code
        }

        val tasks = repository.getTasksForBookList(bookId)
        val projectNotes = if (book.isProject) repository.getNotesForProjectList(bookId) else emptyList()

        val savedMembers = getMembersForBook(context, bookId, ownerEmail)
        val members = if (savedMembers.isNotEmpty()) {
            savedMembers
        } else {
            val owner = BookMember(
                id = "owner",
                name = ownerName.ifBlank { "You (Owner)" },
                email = ownerEmail,
                role = "owner",
                joinedAt = System.currentTimeMillis(),
                avatarColor = MEMBER_AVATAR_COLORS[0]
            )
            listOf(owner).also { saveMembersForBook(context, bookId, it, ownerEmail) }
        }

        val deletedSyncIds = getDeletedTaskSyncIds(context, bookId, ownerEmail)

        val taskItems = tasks.map { t ->
            var syncId = t.syncId
            if (syncId.isNullOrBlank()) {
                syncId = "task_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                repository.updateTask(t.copy(syncId = syncId))
            }
            SharedTaskItem(
                syncId = syncId,
                title = t.title,
                description = t.description,
                isCompleted = t.isCompleted,
                dueDate = t.dueDate,
                dueTime = t.dueTime,
                priority = t.priority,
                status = t.status,
                tags = t.tags,
                attachments = t.attachments,
                assignee = t.assignee,
                comments = t.comments,
                subTasks = t.subTasks
            )
        }

        val payload = SharedBookPayload(
            version = 2,
            type = "DOIT_SHARED_BOOK",
            inviteCode = inviteCode,
            book = SharedBookMeta(
                name = book.name,
                description = ProjectPreferences.getDescription(bookId),
                color = ProjectPreferences.getColorHex(bookId),
                icon = ProjectPreferences.getIconName(bookId),
                isProject = book.isProject,
                category = ProjectPreferences.getCategory(bookId),
                deadline = ProjectPreferences.getDeadline(bookId)
            ),
            owner = SharedOwnerMeta(
                name = ownerName.ifBlank { "Owner" },
                email = ownerEmail
            ),
            members = members,
            tasks = taskItems,
            deletedTaskSyncIds = deletedSyncIds,
            projectNotes = projectNotes.map { n ->
                SharedNoteItem(
                    title = n.title,
                    content = n.content,
                    attachedTaskIds = n.attachedTaskIds,
                    createdAt = n.createdAt
                )
            },
            updatedAt = System.currentTimeMillis()
        )

        // Publish to cloud in background
        publishToCloud(payload)
        payload
    }

    /**
     * Imports and reconciles a SharedBookPayload into the local Room database:
     * - Creates/updates the TaskBook
     * - Stores project metadata (color, icon, description, category, deadline)
     * - Adds the current user as an editor member
     * - Handles deletions specified in deletedTaskSyncIds
     * - Updates existing tasks (completed status, priority, comments, tags, etc.)
     * - Inserts newly created cloud tasks
     * - Inserts/updates project notes
     */
    suspend fun importSharedBook(
        context: Context,
        repository: TaskRepository,
        payload: SharedBookPayload,
        currentUserName: String,
        currentUserEmail: String?
    ): Int = withContext(Dispatchers.IO) {
        val cleanCode = payload.inviteCode.uppercase(Locale.ROOT)
        val allBooks = repository.allTaskBooksList()

        // Check if book with this invite code already exists
        var targetBook = allBooks.find {
            val savedCode = getSavedInviteCode(context, it.id ?: 0, currentUserEmail)?.uppercase(Locale.ROOT)
            savedCode != null && (savedCode == cleanCode || savedCode == payload.inviteCode.uppercase(Locale.ROOT))
        }

        val bookId: Int
        if (targetBook == null) {
            // Avoid clobbering personal default list "My Tasks" if owner is different
            val existingSameName = allBooks.find { it.name.equals(payload.book.name, ignoreCase = true) }
            val finalName = if (existingSameName != null && (existingSameName.id == 1 || !payload.book.name.contains("("))) {
                val ownerSnippet = payload.owner.name.trim().take(12).ifBlank { "Shared" }
                "${payload.book.name} ($ownerSnippet)"
            } else {
                payload.book.name
            }
            val newBook = TaskBookEntity(name = finalName, isProject = payload.book.isProject)
            val generatedId = repository.insertTaskBook(newBook)
            bookId = generatedId.toInt()
            saveInviteCode(context, bookId, cleanCode, currentUserEmail)
        } else {
            bookId = targetBook.id ?: 1
            saveInviteCode(context, bookId, cleanCode, currentUserEmail)
            if (payload.book.isProject && !targetBook.isProject) {
                repository.updateTaskBook(targetBook.copy(isProject = true))
            }
        }

        // Apply Project Preferences
        ProjectPreferences.setProjectMetadata(
            bookId = bookId,
            colorHex = payload.book.color,
            iconName = payload.book.icon,
            description = payload.book.description,
            deadline = payload.book.deadline,
            category = payload.book.category
        )

        // Update Members list: ensure current user is registered
        val members = payload.members.toMutableList()
        val myName = currentUserName.ifBlank { "You" }
        val existingMember = members.find {
            it.name.equals(myName, ignoreCase = true) ||
            (!currentUserEmail.isNullOrBlank() && it.email.equals(currentUserEmail, ignoreCase = true))
        }

        if (existingMember == null) {
            val colorIndex = (members.size % MEMBER_AVATAR_COLORS.size)
            members.add(
                BookMember(
                    id = "mem_${System.currentTimeMillis()}",
                    name = myName,
                    email = currentUserEmail,
                    role = "editor",
                    joinedAt = System.currentTimeMillis(),
                    avatarColor = MEMBER_AVATAR_COLORS[colorIndex]
                )
            )
        }
        saveMembersForBook(context, bookId, members, currentUserEmail)

        // 1. Process cloud deletions locally
        if (payload.deletedTaskSyncIds.isNotEmpty()) {
            val localTasks = repository.getTasksForBookList(bookId)
            val toDelete = localTasks.filter { it.syncId != null && payload.deletedTaskSyncIds.contains(it.syncId) }
            for (del in toDelete) {
                repository.deleteTask(del)
                com.example.moderntodo.util.AlarmScheduler.cancel(context, del.id ?: 0)
            }
        }

        // 2. Reconcile tasks: update existing or insert new
        val existingTasks = repository.getTasksForBookList(bookId)
        val localBySyncId = existingTasks.filter { !it.syncId.isNullOrBlank() }.associateBy { it.syncId!! }.toMutableMap()
        val localByTitle = existingTasks.associateBy { it.title.trim().lowercase(Locale.ROOT) }.toMutableMap()

        for (item in payload.tasks) {
            val syncId = item.syncId.ifBlank { "task_${item.title.hashCode()}_${item.dueDate ?: 0}" }
            val matchedTask = localBySyncId[syncId] ?: localByTitle[item.title.trim().lowercase(Locale.ROOT)]

            if (matchedTask != null) {
                // Task exists locally - reconcile properties
                val hasChanged = matchedTask.isCompleted != item.isCompleted ||
                    matchedTask.status != item.status ||
                    matchedTask.priority != item.priority ||
                    matchedTask.title != item.title ||
                    matchedTask.description != item.description ||
                    matchedTask.dueDate != item.dueDate ||
                    matchedTask.dueTime != item.dueTime ||
                    matchedTask.assignee != item.assignee ||
                    matchedTask.attachments != item.attachments ||
                    matchedTask.tags != item.tags ||
                    matchedTask.comments != item.comments ||
                    matchedTask.subTasks != item.subTasks ||
                    matchedTask.syncId != syncId

                if (hasChanged) {
                    val updatedTask = matchedTask.copy(
                        syncId = syncId,
                        title = item.title,
                        description = item.description,
                        isCompleted = item.isCompleted,
                        dueDate = item.dueDate,
                        dueTime = item.dueTime,
                        priority = item.priority,
                        status = item.status,
                        tags = item.tags,
                        attachments = item.attachments,
                        assignee = item.assignee,
                        comments = item.comments,
                        subTasks = item.subTasks,
                        isImportant = item.priority <= 2
                    )
                    repository.updateTask(updatedTask)

                    if (matchedTask.isCompleted != item.isCompleted) {
                        if (item.isCompleted) {
                            com.example.moderntodo.util.AlarmScheduler.cancel(context, updatedTask.id ?: 0)
                        } else if (updatedTask.hasAlarm || updatedTask.dueTime != null) {
                            com.example.moderntodo.util.AlarmScheduler.schedule(context, updatedTask)
                        }
                    }
                }
            } else {
                // New task created by collaborator
                val newTask = TaskEntity(
                    syncId = syncId,
                    title = item.title,
                    description = item.description,
                    isCompleted = item.isCompleted,
                    dueDate = item.dueDate,
                    dueTime = item.dueTime,
                    bookId = bookId,
                    priority = item.priority,
                    status = item.status,
                    tags = item.tags,
                    attachments = item.attachments,
                    assignee = item.assignee,
                    comments = item.comments,
                    subTasks = item.subTasks,
                    isImportant = item.priority <= 2,
                    isProjectTask = payload.book.isProject,
                    projectId = if (payload.book.isProject) bookId else null
                )
                val newId = repository.insertTask(newTask)
                if (!item.isCompleted && (newTask.hasAlarm || newTask.dueTime != null)) {
                    com.example.moderntodo.util.AlarmScheduler.schedule(context, newTask.copy(id = newId.toInt()))
                }
            }
        }

        // 3. Import Project Notes
        if (payload.book.isProject && payload.projectNotes.isNotEmpty()) {
            val existingNotes = repository.getNotesForProjectList(bookId)
            val existingNoteMap = existingNotes.associateBy { it.title.trim().lowercase(Locale.ROOT) }

            for (note in payload.projectNotes) {
                val existing = existingNoteMap[note.title.trim().lowercase(Locale.ROOT)]
                if (existing != null) {
                    if (existing.content != note.content || existing.attachedTaskIds != note.attachedTaskIds) {
                        repository.updateNote(existing.copy(
                            content = note.content,
                            attachedTaskIds = note.attachedTaskIds,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                } else {
                    repository.insertNote(
                        ProjectNoteEntity(
                            projectId = bookId,
                            title = note.title,
                            content = note.content,
                            attachedTaskIds = note.attachedTaskIds,
                            createdAt = note.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        val prefs = getPrefs(context, currentUserEmail)
        prefs.edit().putLong("last_sync_time_$bookId", payload.updatedAt).apply()

        bookId
    }

    private val syncInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Live catch-up synchronization matching LOSE EXP:
     * - Fast header check: if cloud has newer updatedAt, pulls and merges snapshot
     * - Preserves local additions and publishes back
     */
    suspend fun syncSharedBookCatchUp(
        context: Context,
        repository: TaskRepository,
        bookId: Int,
        currentUserName: String,
        currentUserEmail: String?,
        force: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        val inviteCode = getSavedInviteCode(context, bookId, currentUserEmail)
            ?: return@withContext SyncResult(false, "No invite code for book")

        val code = inviteCode.trim().uppercase(Locale.ROOT)
        if (syncInProgress.contains(code)) {
            return@withContext SyncResult(true, "Sync already in progress")
        }
        syncInProgress.add(code)

        try {
            val prefs = getPrefs(context, currentUserEmail)
            val lastSync = prefs.getLong("last_sync_time_$bookId", 0L)
            val cloudUpdatedAt = fetchCloudUpdatedAt(code)

            var pulled = false
            if (force || cloudUpdatedAt == null || cloudUpdatedAt > lastSync) {
                val cloudPayload = fetchFromCloud(code)
                if (cloudPayload != null) {
                    importSharedBook(context, repository, cloudPayload, currentUserName, currentUserEmail)
                    pulled = true
                }
            }

            // Always ensure local snapshot is pushed if there are local-only items or forced
            val freshPayload = exportBookPayload(context, repository, bookId, currentUserName, currentUserEmail)
            if (freshPayload != null) {
                publishToCloud(freshPayload)
                prefs.edit().putLong("last_sync_time_$bookId", freshPayload.updatedAt).apply()
            }

            SyncResult(
                success = true,
                message = if (pulled) "Synced live updates" else "Up to date",
                tasksCount = freshPayload?.tasks?.size ?: 0,
                notesCount = freshPayload?.projectNotes?.size ?: 0
            )
        } catch (e: Exception) {
            Log.w("SharedBookService", "syncSharedBookCatchUp failed: ${e.message}")
            SyncResult(false, "Sync failed: ${e.message}")
        } finally {
            syncInProgress.remove(code)
        }
    }

    /**
     * Synchronizes local book state with cloud (forced pull and re-publish).
     */
    suspend fun syncSharedBook(
        context: Context,
        repository: TaskRepository,
        bookId: Int,
        currentUserName: String,
        currentUserEmail: String?
    ): SyncResult = syncSharedBookCatchUp(
        context = context,
        repository = repository,
        bookId = bookId,
        currentUserName = currentUserName,
        currentUserEmail = currentUserEmail,
        force = true
    )

    /**
     * Silently pushes snapshots of all local books to Supabase
     * ensuring any existing invite codes are live and accessible by peers.
     */
    suspend fun syncAllUserBooksToCloud(
        context: Context,
        repository: TaskRepository,
        userEmail: String?
    ) = withContext(Dispatchers.IO) {
        try {
            val books = repository.allTaskBooksList()
            val ownerName = if (!userEmail.isNullOrBlank()) userEmail.substringBefore("@") else "Owner"
            for (book in books) {
                val bookId = book.id ?: continue
                exportBookPayload(
                    context = context,
                    repository = repository,
                    bookId = bookId,
                    ownerName = ownerName,
                    ownerEmail = userEmail
                )
            }
            Log.d("SharedBookService", "syncAllUserBooksToCloud completed for ${books.size} books")
        } catch (e: Exception) {
            Log.w("SharedBookService", "syncAllUserBooksToCloud failed: ${e.message}")
        }
    }

    /**
     * Native share sheet trigger matching LOSE EXP formatting.
     */
    fun shareBookNative(context: Context, payload: SharedBookPayload) {
        val token = encodeToken(payload)
        val safeName = payload.book.name.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
        val fileName = "${safeName}_Shared_Book.doit"

        val message = """
📚 Join my shared list "${payload.book.name}" on DO-IT!

🔑 Book Invite Code: ${payload.inviteCode}
👥 Current Members: ${payload.members.joinToString(", ") { it.name }}
📊 Includes: ${payload.tasks.size} shared tasks ready for collaboration

To join immediately on your phone:
1. Open DO-IT
2. Tap "Join Shared List" in the left menu or Top Bar
3. Enter code "${payload.inviteCode}" or paste this message.

$token
        """.trimIndent()

        try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, fileName)
            file.writeText(gson.toJson(payload))

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Join '${payload.book.name}' on DO-IT")
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Book with Collaborators")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback to text-only share
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Join '${payload.book.name}' on DO-IT")
                putExtra(Intent.EXTRA_TEXT, message)
            }
            val chooser = Intent.createChooser(intent, "Share Book with Collaborators")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    // -------------------------------------------------------------
    // SharedPreferences Helpers for Invite Codes & Members
    // -------------------------------------------------------------
    private fun getPrefs(context: Context, userEmail: String?): android.content.SharedPreferences {
        val sanitized = com.example.moderntodo.data.local.AppDatabase.sanitizeUserEmail(userEmail)
        return context.getSharedPreferences("shared_books_meta_$sanitized", Context.MODE_PRIVATE)
    }

    fun getSavedInviteCode(context: Context, bookId: Int, userEmail: String? = null): String? {
        val email = userEmail ?: SettingsRepository.userEmailFlow.value
        val prefs = getPrefs(context, email)
        val code = prefs.getString("invite_code_$bookId", null)
        if (code != null) return code
        return context.getSharedPreferences("shared_books_meta", Context.MODE_PRIVATE)
            .getString("invite_code_$bookId", null)
    }

    fun saveInviteCode(context: Context, bookId: Int, code: String, userEmail: String? = null) {
        val email = userEmail ?: SettingsRepository.userEmailFlow.value
        getPrefs(context, email).edit().putString("invite_code_$bookId", code).apply()
        context.getSharedPreferences("shared_books_meta", Context.MODE_PRIVATE)
            .edit().putString("invite_code_$bookId", code).apply()
    }

    fun getMembersForBook(context: Context, bookId: Int, userEmail: String? = null): List<BookMember> {
        val email = userEmail ?: SettingsRepository.userEmailFlow.value
        val prefs = getPrefs(context, email)
        val json = prefs.getString("members_$bookId", null)
            ?: context.getSharedPreferences("shared_books_meta", Context.MODE_PRIVATE).getString("members_$bookId", null)
            ?: return emptyList()
        return try {
            val type = object : TypeToken<List<BookMember>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMembersForBook(context: Context, bookId: Int, members: List<BookMember>, userEmail: String? = null) {
        val email = userEmail ?: SettingsRepository.userEmailFlow.value
        val json = gson.toJson(members)
        getPrefs(context, email).edit().putString("members_$bookId", json).apply()
        context.getSharedPreferences("shared_books_meta", Context.MODE_PRIVATE)
            .edit().putString("members_$bookId", json).apply()
    }

    fun addMemberToBook(context: Context, bookId: Int, member: BookMember, userEmail: String? = null): List<BookMember> {
        val current = getMembersForBook(context, bookId, userEmail).toMutableList()
        val index = current.indexOfFirst { it.name.equals(member.name, ignoreCase = true) }
        if (index >= 0) {
            current[index] = member
        } else {
            current.add(member)
        }
        saveMembersForBook(context, bookId, current, userEmail)
        return current
    }

    fun updateMemberRole(context: Context, bookId: Int, memberId: String, newRole: String, userEmail: String? = null): List<BookMember> {
        val current = getMembersForBook(context, bookId, userEmail).toMutableList()
        val index = current.indexOfFirst { it.id == memberId }
        if (index >= 0) {
            current[index] = current[index].copy(role = newRole)
            saveMembersForBook(context, bookId, current, userEmail)
        }
        return current
    }

    fun removeMemberFromBook(context: Context, bookId: Int, memberId: String, userEmail: String? = null): List<BookMember> {
        val current = getMembersForBook(context, bookId, userEmail).filter { it.id != memberId }
        saveMembersForBook(context, bookId, current, userEmail)
        return current
    }
}
