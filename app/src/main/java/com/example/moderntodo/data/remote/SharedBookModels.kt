package com.example.moderntodo.data.remote

import com.google.gson.annotations.SerializedName

data class SharedBookPayload(
    @SerializedName("version") val version: Int = 2,
    @SerializedName("type") val type: String = "DOIT_SHARED_BOOK",
    @SerializedName("inviteCode") val inviteCode: String,
    @SerializedName("book") val book: SharedBookMeta,
    @SerializedName("owner") val owner: SharedOwnerMeta,
    @SerializedName("members") val members: List<BookMember> = emptyList(),
    @SerializedName("tasks") val tasks: List<SharedTaskItem> = emptyList(),
    @SerializedName("deletedTaskSyncIds") val deletedTaskSyncIds: List<String> = emptyList(),
    @SerializedName("projectNotes") val projectNotes: List<SharedNoteItem> = emptyList(),
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

data class SharedBookMeta(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("color") val color: String = "#2563EB",
    @SerializedName("icon") val icon: String = "folder",
    @SerializedName("isProject") val isProject: Boolean = false,
    @SerializedName("category") val category: String = "General",
    @SerializedName("deadline") val deadline: Long? = null
)

data class SharedOwnerMeta(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String? = null
)

data class BookMember(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String = "editor", // "owner", "admin", "editor", "viewer"
    @SerializedName("joinedAt") val joinedAt: Long = System.currentTimeMillis(),
    @SerializedName("avatarColor") val avatarColor: String = "#2563EB"
)

data class SharedTaskItem(
    @SerializedName("syncId") val syncId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("isCompleted") val isCompleted: Boolean = false,
    @SerializedName("dueDate") val dueDate: Long? = null,
    @SerializedName("dueTime") val dueTime: Long? = null,
    @SerializedName("priority") val priority: Int = 4,
    @SerializedName("status") val status: String = "Pending",
    @SerializedName("tags") val tags: String = "",
    @SerializedName("attachments") val attachments: String = "",
    @SerializedName("assignee") val assignee: String = "",
    @SerializedName("comments") val comments: String = "",
    @SerializedName("subTasks") val subTasks: String = ""
)

data class SharedNoteItem(
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String = "",
    @SerializedName("attachedTaskIds") val attachedTaskIds: String = "",
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
)

data class SyncResult(
    val success: Boolean,
    val message: String,
    val tasksCount: Int = 0,
    val notesCount: Int = 0
)

data class SharedBookRow(
    @SerializedName("id") val id: String,
    @SerializedName("book_name") val bookName: String,
    @SerializedName("owner_name") val ownerName: String,
    @SerializedName("payload") val payload: SharedBookPayload,
    @SerializedName("updated_at") val updatedAt: Long
)

data class SharedBookHeaderRow(
    @SerializedName("id") val id: String,
    @SerializedName("updated_at") val updatedAt: Long
)
