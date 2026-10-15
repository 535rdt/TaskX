package com.example.moderntodo.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.moderntodo.data.local.TaskBookEntity
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.data.remote.BookMember
import com.example.moderntodo.data.remote.SharedBookService
import com.example.moderntodo.util.QrCodeGenerator
import com.example.moderntodo.util.QrScannerHelper
import com.example.moderntodo.util.SettingsRepository
import com.example.moderntodo.viewmodel.TaskViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListAdminScreen(
    viewModel: TaskViewModel,
    bookId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val currentUserEmail by SettingsRepository.userEmailFlow.collectAsState()

    val taskBooks by viewModel.taskBooks.collectAsState()
    val book = taskBooks.find { it.id == bookId }

    if (book == null) {
        onBack()
        return
    }

    val bookTasks by viewModel.getTasksForBook(bookId).collectAsState(initial = emptyList())
    val totalCount = bookTasks.size
    val completedCount = bookTasks.count { it.isCompleted }
    val pendingCount = totalCount - completedCount
    val priorityCount = bookTasks.count { it.isImportant }
    val progressPct = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    var listName by remember { mutableStateOf(book.name) }
    var isEditingName by remember { mutableStateOf(false) }

    val inviteCode = remember(bookId) {
        val saved = SharedBookService.getSavedInviteCode(context, bookId, currentUserEmail)
        if (saved != null) saved else {
            val generated = SharedBookService.generateInviteCode(book.name, bookId)
            SharedBookService.saveInviteCode(context, bookId, generated, currentUserEmail)
            generated
        }
    }

    var members by remember(bookId) {
        val savedMembers = SharedBookService.getMembersForBook(context, bookId, currentUserEmail)
        if (savedMembers.isNotEmpty()) {
            mutableStateOf(savedMembers)
        } else {
            val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "You (Owner)"
            val owner = BookMember(
                id = "owner",
                name = ownerName,
                email = currentUserEmail,
                role = "owner",
                joinedAt = System.currentTimeMillis(),
                avatarColor = SharedBookService.MEMBER_AVATAR_COLORS[0]
            )
            val initial = listOf(owner)
            SharedBookService.saveMembersForBook(context, bookId, initial, currentUserEmail)
            mutableStateOf(initial)
        }
    }

    LaunchedEffect(bookId) {
        val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
        SharedBookService.exportBookPayload(
            context = context,
            repository = viewModel.repository,
            bookId = bookId,
            ownerName = ownerName,
            ownerEmail = currentUserEmail
        )
    }

    var isSyncing by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }
    var copiedToken by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<BookMember?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = book.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "List Administration • ${members.size} ${if (members.size == 1) "Member" else "Members"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                try {
                                    val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                    val result = SharedBookService.syncSharedBook(
                                        context = context,
                                        repository = viewModel.repository,
                                        bookId = bookId,
                                        currentUserName = ownerName,
                                        currentUserEmail = currentUserEmail
                                    )
                                    members = SharedBookService.getMembersForBook(context, bookId, currentUserEmail)
                                    if (result.success) {
                                        Toast.makeText(context, "Cloud sync complete! (${result.tasksCount} tasks)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = "Sync Cloud")
                        }
                    }
                    IconButton(onClick = { showJoinDialog = true }) {
                        Icon(Icons.Filled.Key, contentDescription = "Join List")
                    }
                    IconButton(onClick = {
                        sharePdfReport(context, book, bookTasks)
                    }) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                            val payload = SharedBookService.exportBookPayload(
                                context = context,
                                repository = viewModel.repository,
                                bookId = bookId,
                                ownerName = ownerName,
                                ownerEmail = currentUserEmail
                            )
                            if (payload != null) {
                                SharedBookService.shareBookNative(context, payload)
                            } else {
                                shareLiveListLink(context, book, bookTasks)
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Live Share")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -------------------------------------------------------------
            // 1. List Header & Rename Card
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (book.id == 1) Icons.Filled.Home else Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (isEditingName) {
                                    OutlinedTextField(
                                        value = listName,
                                        onValueChange = { listName = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                if (listName.isNotBlank()) {
                                                    viewModel.updateBook(book, listName)
                                                    isEditingName = false
                                                    Toast.makeText(context, "List renamed", Toast.LENGTH_SHORT).show()
                                                }
                                            }) {
                                                Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = book.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (book.id == 1) "Default Task Book • System" else "Custom List • ID #${book.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isEditingName) {
                                IconButton(onClick = { isEditingName = true }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. List Analytics & Progress Overview
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "List Progress",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(progressPct * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progressPct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatBox(
                                title = "Total",
                                count = totalCount.toString(),
                                icon = Icons.Outlined.Checklist,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                title = "Pending",
                                count = pendingCount.toString(),
                                icon = Icons.Outlined.Schedule,
                                color = Color(0xFFE65100),
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                title = "Completed",
                                count = completedCount.toString(),
                                icon = Icons.Outlined.CheckCircle,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                title = "Priority",
                                count = priorityCount.toString(),
                                icon = Icons.Outlined.Flag,
                                color = Color(0xFFC2185B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 3. Live List Share & Real-Time Collaboration (LOSE EXP Match)
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Hero Gradient Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF4C59D9),
                                            Color(0xFF3742B5)
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SHARED BOOK INVITE CODE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.85f),
                                            letterSpacing = 1.2.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = inviteCode,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White,
                                            letterSpacing = 2.sp
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(inviteCode))
                                            copiedCode = true
                                            Toast.makeText(context, "Invite code $inviteCode copied!", Toast.LENGTH_SHORT).show()
                                            scope.launch {
                                                val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                                SharedBookService.exportBookPayload(context, viewModel.repository, bookId, ownerName, currentUserEmail)
                                                delay(2000)
                                                copiedCode = false
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.22f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedCode) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (copiedCode) "Copied" else "Copy Code",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Share this list with your partner, family, or team to collaborate on tasks and project notes with real-time cloud sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                                val payload = SharedBookService.exportBookPayload(
                                                    context = context,
                                                    repository = viewModel.repository,
                                                    bookId = bookId,
                                                    ownerName = ownerName,
                                                    ownerEmail = currentUserEmail
                                                )
                                                if (payload != null) {
                                                    SharedBookService.shareBookNative(context, payload)
                                                } else {
                                                    Toast.makeText(context, "Could not export list payload", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color(0xFF4C59D9)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share Invite", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                                val payload = SharedBookService.exportBookPayload(
                                                    context = context,
                                                    repository = viewModel.repository,
                                                    bookId = bookId,
                                                    ownerName = ownerName,
                                                    ownerEmail = currentUserEmail
                                                )
                                                if (payload != null) {
                                                    val token = SharedBookService.encodeToken(payload)
                                                    clipboardManager.setText(AnnotatedString(token))
                                                    copiedToken = true
                                                    Toast.makeText(context, "Sync Token copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                    delay(2000)
                                                    copiedToken = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedToken) Icons.Filled.Check else Icons.Filled.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (copiedToken) "Copied" else "Token", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = { showQrDialog = true },
                                        modifier = Modifier.weight(0.9f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("QR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Collaborators Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "List Members (${members.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = Color(0xFF4C59D9).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "Your Role: Owner",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4C59D9)
                                        )
                                    }
                                }
                                Text(
                                    text = "Assign roles and manage who can add or view tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = { showAddMemberDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Members List Cards
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            members.forEach { member ->
                                MemberItemCard(
                                    member = member,
                                    isCurrentUser = member.role == "owner" || member.id == "owner" ||
                                            (!currentUserEmail.isNullOrBlank() && member.email == currentUserEmail),
                                    onRoleChange = { newRole ->
                                        val updated = SharedBookService.updateMemberRole(context, bookId, member.id, newRole)
                                        members = updated
                                        scope.launch {
                                            val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                            SharedBookService.exportBookPayload(context, viewModel.repository, bookId, ownerName, currentUserEmail)
                                        }
                                        Toast.makeText(context, "${member.name} role changed to $newRole", Toast.LENGTH_SHORT).show()
                                    },
                                    onRemove = {
                                        memberToRemove = member
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 4. Report Generation to PDF & Data Exports
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reports & Data Export",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // PDF Report Generation Action
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    sharePdfReport(context, book, bookTasks)
                                },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Generate PDF Report",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Printable A4 report with status, deadlines, and metrics",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // CSV / Excel Export
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    shareCsvReport(context, book, bookTasks)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.TableChart,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Export to CSV / Excel",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Spreadsheet format compatible with Excel and Google Sheets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Copy / Share Text Summary
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val summaryText = buildTextChecklist(book, bookTasks)
                                    clipboardManager.setText(AnnotatedString(summaryText))
                                    Toast.makeText(context, "Checklist copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Copy Text Checklist",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Formatted checklist ready for WhatsApp, Notes, or Messages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // 5. Productivity & Batch Operations
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Batch Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ListItem(
                            headlineContent = { Text("Duplicate List") },
                            supportingContent = { Text("Clone this list and all its tasks into a new list") },
                            leadingContent = { Icon(Icons.Outlined.FileCopy, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.duplicateBook(book) {
                                    Toast.makeText(context, "List duplicated successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        ListItem(
                            headlineContent = { Text("Mark All as Completed") },
                            supportingContent = { Text("Mark all pending tasks in this list as finished") },
                            leadingContent = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.completeAllTasksInBook(bookId)
                                Toast.makeText(context, "All tasks marked as completed", Toast.LENGTH_SHORT).show()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        ListItem(
                            headlineContent = { Text("Clear Completed Tasks") },
                            supportingContent = { Text("Remove finished tasks from this list") },
                            leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showClearCompletedDialog = true
                            }
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 6. Danger Zone (Delete List)
            // -------------------------------------------------------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (book.id == 1) "The default 'My Tasks' list is protected and cannot be deleted."
                            else "Deleting this list will permanently remove it and all ${bookTasks.size} associated tasks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = book.id != 1,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete List")
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Dialogs: Invite, QR, Delete Confirm, Clear Completed Confirm
        // -------------------------------------------------------------
        // -------------------------------------------------------------
        // Dialogs: Add Member, Join Book, Real QR, Remove Member, Clear, Delete
        // -------------------------------------------------------------
        if (showAddMemberDialog) {
            var newName by remember { mutableStateOf("") }
            var newContact by remember { mutableStateOf("") }
            var selectedRole by remember { mutableStateOf("editor") }

            AlertDialog(
                onDismissRequest = { showAddMemberDialog = false },
                title = { Text("Add Member to '${book.name}'") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Add a partner, teammate, or friend to collaborate on this list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Member Name *") },
                            placeholder = { Text("e.g. Rahul, Sarah, Team Lead") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newContact,
                            onValueChange = { newContact = it },
                            label = { Text("Email or Phone (Optional)") },
                            placeholder = { Text("e.g. partner@example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "SELECT ROLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "editor" to "Partner/Editor",
                                "admin" to "Admin",
                                "viewer" to "Viewer"
                            ).forEach { (rKey, rLabel) ->
                                FilterChip(
                                    selected = selectedRole == rKey,
                                    onClick = { selectedRole = rKey },
                                    label = { Text(rLabel, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                val colorIndex = (members.size % SharedBookService.MEMBER_AVATAR_COLORS.size)
                                val newMember = BookMember(
                                    id = "mem_${System.currentTimeMillis()}",
                                    name = newName.trim(),
                                    email = if (newContact.isNotBlank()) newContact.trim() else null,
                                    role = selectedRole,
                                    joinedAt = System.currentTimeMillis(),
                                    avatarColor = SharedBookService.MEMBER_AVATAR_COLORS[colorIndex]
                                )
                                val updated = SharedBookService.addMemberToBook(context, bookId, newMember)
                                members = updated
                                scope.launch {
                                    val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                    SharedBookService.exportBookPayload(context, viewModel.repository, bookId, ownerName, currentUserEmail)
                                }
                                Toast.makeText(context, "${newName.trim()} added as $selectedRole", Toast.LENGTH_SHORT).show()
                                showAddMemberDialog = false
                            } else {
                                Toast.makeText(context, "Please enter member name", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Add Member")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddMemberDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showJoinDialog) {
            var joinInput by remember { mutableStateOf("") }
            var isJoining by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isJoining) showJoinDialog = false },
                title = { Text("Join a Shared List") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Enter the List Invite Code (e.g. $inviteCode), paste the full Sync Token, or scan a QR code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        joinInput = clip.trim()
                                        Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    QrScannerHelper.startScan(
                                        context = context,
                                        onSuccess = { rawResult ->
                                            joinInput = rawResult.trim()
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan QR", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        OutlinedTextField(
                            value = joinInput,
                            onValueChange = { joinInput = it },
                            placeholder = { Text("Paste code (e.g. PRJ-4K2A) or token here...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            maxLines = 5,
                            enabled = !isJoining
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val raw = joinInput.trim()
                            if (raw.isBlank()) {
                                Toast.makeText(context, "Please enter an invite code or token", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isJoining = true
                            scope.launch {
                                try {
                                    val payload = SharedBookService.parseSharedInput(raw)
                                    if (payload == null) {
                                        Toast.makeText(context, "Could not find list. Check your code or token.", Toast.LENGTH_LONG).show()
                                        isJoining = false
                                        return@launch
                                    }
                                    val myName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "You"
                                    val newId = SharedBookService.importSharedBook(
                                        context = context,
                                        repository = viewModel.repository,
                                        payload = payload,
                                        currentUserName = myName,
                                        currentUserEmail = currentUserEmail
                                    )
                                    viewModel.switchBook(newId)
                                    Toast.makeText(context, "Joined '${payload.book.name}' successfully! (${payload.tasks.size} tasks)", Toast.LENGTH_LONG).show()
                                    showJoinDialog = false
                                    onBack()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Join failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isJoining = false
                                }
                            }
                        },
                        enabled = !isJoining && joinInput.isNotBlank()
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Join & Sync")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinDialog = false }, enabled = !isJoining) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showQrDialog) {
            val qrBitmapState = remember(inviteCode) {
                QrCodeGenerator.generateQrBitmap(inviteCode, 512)
            }

            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                title = {
                    Text(
                        text = "Scan to Join '${book.name}'",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Scan this QR code with DO-IT on another phone to join this list instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(2.dp, Color(0xFF4C59D9), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmapState != null) {
                                Image(
                                    bitmap = qrBitmapState.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "INVITE CODE: $inviteCode",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF4C59D9),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(inviteCode))
                        Toast.makeText(context, "Invite code copied: $inviteCode", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Code")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQrDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (memberToRemove != null) {
            val target = memberToRemove!!
            AlertDialog(
                onDismissRequest = { memberToRemove = null },
                title = { Text("Remove Collaborator?") },
                text = { Text("Are you sure you want to remove '${target.name}' from '${book.name}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val updated = SharedBookService.removeMemberFromBook(context, bookId, target.id)
                            members = updated
                            scope.launch {
                                val ownerName = if (!currentUserEmail.isNullOrBlank()) currentUserEmail!!.substringBefore("@") else "Owner"
                                SharedBookService.exportBookPayload(context, viewModel.repository, bookId, ownerName, currentUserEmail)
                            }
                            Toast.makeText(context, "${target.name} removed from list", Toast.LENGTH_SHORT).show()
                            memberToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToRemove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showClearCompletedDialog) {
            AlertDialog(
                onDismissRequest = { showClearCompletedDialog = false },
                title = { Text("Clear Completed Tasks?") },
                text = { Text("This will delete all completed tasks from '${book.name}'. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteCompletedTasksInBook(bookId)
                        showClearCompletedDialog = false
                        Toast.makeText(context, "Completed tasks cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCompletedDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete '${book.name}'?") },
                text = { Text("Are you sure you want to delete this list and all of its tasks? This action is permanent.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteBook(book)
                            showDeleteConfirmDialog = false
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun MemberItemCard(
    member: BookMember,
    isCurrentUser: Boolean,
    onRoleChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val roleColor = when (member.role.lowercase(Locale.ROOT)) {
        "owner" -> Color(0xFF4C59D9)
        "admin" -> Color(0xFF8B5CF6)
        "editor" -> Color(0xFF10B981)
        else -> Color(0xFF64748B)
    }

    val roleLabel = when (member.role.lowercase(Locale.ROOT)) {
        "owner" -> "Owner"
        "admin" -> "Admin"
        "editor" -> "Partner / Editor"
        else -> "Viewer"
    }

    val roleIcon = when (member.role.lowercase(Locale.ROOT)) {
        "owner" -> Icons.Filled.Star
        "admin" -> Icons.Filled.Security
        "editor" -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Visibility
    }

    val avatarBg = try {
        Color(android.graphics.Color.parseColor(member.avatarColor ?: "#4C59D9"))
    } catch (_: Exception) {
        Color(0xFF4C59D9)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (isCurrentUser) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4C59D9).copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.name.take(1).uppercase(Locale.ROOT),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (isCurrentUser) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF4C59D9).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "YOU",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4C59D9),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = member.email ?: "Shared List Collaborator",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = roleColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(roleIcon, contentDescription = null, tint = roleColor, modifier = Modifier.size(13.dp))
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = roleColor
                        )
                    }
                }

                if (member.role != "owner" && member.id != "owner") {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Role selection buttons for non-owner members
            if (member.role != "owner" && member.id != "owner") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Role:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    listOf(
                        "admin" to "Admin",
                        "editor" to "Editor",
                        "viewer" to "Viewer"
                    ).forEach { (rKey, rLabel) ->
                        val isSelected = member.role.equals(rKey, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (!isSelected) onRoleChange(rKey) },
                            label = { Text(rLabel, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(count, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
        }
    }
}

// -------------------------------------------------------------
// Report Generation Helpers
// -------------------------------------------------------------
fun sharePdfReport(context: Context, book: TaskBookEntity, tasks: List<TaskEntity>) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(33, 33, 33)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = AndroidColor.rgb(100, 100, 100)
            textSize = 11f
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = AndroidColor.rgb(30, 136, 229)
        }

        val headerTextPaint = Paint().apply {
            color = AndroidColor.WHITE
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headerSubTextPaint = Paint().apply {
            color = AndroidColor.rgb(225, 245, 254)
            textSize = 10f
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = AndroidColor.rgb(40, 40, 40)
            textSize = 11f
            isAntiAlias = true
        }

        val bodySubPaint = Paint().apply {
            color = AndroidColor.rgb(120, 120, 120)
            textSize = 9f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = AndroidColor.rgb(230, 230, 230)
            strokeWidth = 1f
        }

        val donePaint = Paint().apply {
            color = AndroidColor.rgb(46, 125, 50)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val todoPaint = Paint().apply {
            color = AndroidColor.rgb(230, 81, 0)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        // Draw Header Banner
        canvas.drawRect(0f, 0f, 595f, 85f, headerBgPaint)
        canvas.drawText("DO-IT • TASK LIST REPORT", 36f, 40f, headerTextPaint)
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())
        canvas.drawText("Generated on: ${dateFormat.format(Date())}", 36f, 64f, headerSubTextPaint)

        // List Name & Summary
        var yPos = 120f
        canvas.drawText(book.name, 36f, yPos, titlePaint)
        yPos += 18f
        val completed = tasks.count { it.isCompleted }
        val pending = tasks.size - completed
        val pct = if (tasks.isNotEmpty()) (completed * 100 / tasks.size) else 0
        canvas.drawText("Total: ${tasks.size} tasks | Completed: $completed ($pct%) | Pending: $pending", 36f, yPos, subtitlePaint)

        yPos += 16f
        canvas.drawLine(36f, yPos, 559f, yPos, linePaint)
        yPos += 22f

        // Table Header
        val colHeaderPaint = Paint().apply {
            color = AndroidColor.rgb(70, 70, 70)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("STATUS", 36f, yPos, colHeaderPaint)
        canvas.drawText("TASK DETAILS", 110f, yPos, colHeaderPaint)
        canvas.drawText("DEADLINE", 460f, yPos, colHeaderPaint)

        yPos += 8f
        canvas.drawLine(36f, yPos, 559f, yPos, linePaint)
        yPos += 20f

        if (tasks.isEmpty()) {
            canvas.drawText("No tasks recorded in this list yet.", 36f, yPos, bodySubPaint)
        } else {
            tasks.take(28).forEach { task ->
                if (yPos > 790f) return@forEach

                val statusStr = if (task.isCompleted) "[COMPLETED]" else "[PENDING]"
                val statusPaint = if (task.isCompleted) donePaint else todoPaint
                canvas.drawText(statusStr, 36f, yPos, statusPaint)

                val displayTitle = if (task.title.length > 50) task.title.take(47) + "..." else task.title
                canvas.drawText(displayTitle, 110f, yPos, bodyPaint)

                val dueStr = task.dueDate?.let {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
                } ?: "None"
                canvas.drawText(dueStr, 460f, yPos, bodySubPaint)

                yPos += 12f
                if (task.description.isNotBlank()) {
                    val descStr = if (task.description.length > 60) task.description.take(57) + "..." else task.description
                    canvas.drawText(descStr, 110f, yPos, bodySubPaint)
                    yPos += 12f
                }
                canvas.drawLine(36f, yPos, 559f, yPos, linePaint)
                yPos += 18f
            }
        }

        // Footer
        val footerPaint = Paint().apply {
            color = AndroidColor.rgb(160, 160, 160)
            textSize = 9f
            isAntiAlias = true
        }
        canvas.drawText("Generated by DO-IT To-Do App • ${book.name}", 36f, 820f, footerPaint)

        pdfDocument.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val sanitized = book.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = File(reportsDir, "${sanitized}_report.pdf")
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${book.name} - PDF Task Report")
            putExtra(Intent.EXTRA_TEXT, "Here is the PDF report for '${book.name}' generated from DO-IT.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share PDF Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareCsvReport(context: Context, book: TaskBookEntity, tasks: List<TaskEntity>) {
    try {
        val csvHeader = "ID,Title,Description,Status,Completed,DueDate,Priority,Tags\n"
        val csvRows = tasks.joinToString("\n") { task ->
            val due = task.dueDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: ""
            "\"${task.id ?: 0}\",\"${task.title.replace("\"", "\"\"")}\",\"${task.description.replace("\"", "\"\"")}\",\"${task.status}\",${task.isCompleted},\"$due\",${task.isImportant},\"${task.tags}\""
        }
        val content = csvHeader + csvRows
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val sanitized = book.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val file = File(reportsDir, "${sanitized}_tasks.csv")
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${book.name} - CSV Data Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export CSV")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "CSV export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareLiveListLink(context: Context, book: TaskBookEntity, tasks: List<TaskEntity>) {
    val pendingCount = tasks.count { !it.isCompleted }
    val inviteMessage = """
📋 *Join my live list on DO-IT: ${book.name}*

$pendingCount pending tasks ready for collaboration.
Live invite link: https://doit.app/join/list_${book.id}?code=DOIT${book.id}77

Recent tasks in ${book.name}:
${tasks.take(5).joinToString("\n") { (if (it.isCompleted) "✓ " else "○ ") + it.title }}
${if (tasks.size > 5) "...and ${tasks.size - 5} more tasks" else ""}
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Collaborate on ${book.name}")
        putExtra(Intent.EXTRA_TEXT, inviteMessage)
    }
    val chooser = Intent.createChooser(intent, "Live List Share")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

fun buildTextChecklist(book: TaskBookEntity, tasks: List<TaskEntity>): String {
    return buildString {
        appendLine("📋 ${book.name}")
        appendLine("================")
        if (tasks.isEmpty()) {
            appendLine("No tasks")
        } else {
            tasks.forEach { task ->
                val mark = if (task.isCompleted) "[x]" else "[ ]"
                val due = task.dueDate?.let { " (Due: ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(it))})" } ?: ""
                appendLine("$mark ${task.title}$due")
            }
        }
    }
}
