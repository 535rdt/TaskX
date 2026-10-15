package com.example.moderntodo.ui.main

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.moderntodo.ModernToDoApp
import com.example.moderntodo.viewmodel.TaskViewModel
import com.example.moderntodo.viewmodel.TaskViewModelFactory
import androidx.activity.compose.BackHandler
import com.example.moderntodo.data.local.TaskEntity
import com.example.moderntodo.util.SettingsRepository

enum class AuthScreen {
    Login, Signup
}

enum class Screen {
    Home, Completed, Profile, Settings, ListAdmin, Focus, Analytics
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val application = context.applicationContext as ModernToDoApp
    val accessToken by SettingsRepository.accessTokenFlow.collectAsState()
    val userEmail by SettingsRepository.userEmailFlow.collectAsState()

    var authScreen by remember { mutableStateOf(AuthScreen.Login) }

    if (accessToken == null) {
        when (authScreen) {
            AuthScreen.Login -> LoginScreen(onNavigateToSignup = { authScreen = AuthScreen.Signup })
            AuthScreen.Signup -> SignupScreen(onNavigateToLogin = { authScreen = AuthScreen.Login })
        }
        return
    }

    // Keyed by userEmail: ensures completely isolated ViewModel and state per account
    key(userEmail) {
        val userRepo = remember(userEmail) { application.getRepository(userEmail) }
        val viewModel: TaskViewModel = viewModel(
            key = "TaskViewModel_${userEmail ?: "guest"}",
            factory = TaskViewModelFactory(userRepo)
        )

        var currentScreen by remember { mutableStateOf(Screen.Home) }
        var adminBookId by remember { mutableStateOf<Int?>(null) }
        var focusTask by remember { mutableStateOf<TaskEntity?>(null) }
        var backPressTime by remember { mutableStateOf(0L) }
        val activity = LocalContext.current as? android.app.Activity

        BackHandler(enabled = currentScreen != Screen.Home) {
            currentScreen = Screen.Home
        }

        BackHandler(enabled = currentScreen == Screen.Home) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressTime < 2000) {
                activity?.finish()
            } else {
                backPressTime = currentTime
                android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        when (currentScreen) {
        Screen.Home -> HomeScreen(
            viewModel = viewModel,
            onNavigateToCompleted = { currentScreen = Screen.Completed },
            onNavigateToProfile = { currentScreen = Screen.Profile },
            onNavigateToAdmin = { bookId ->
                adminBookId = bookId
                currentScreen = Screen.ListAdmin
            },
            onNavigateToSettings = { currentScreen = Screen.Settings },
            onNavigateToFocus = { task ->
                focusTask = task
                currentScreen = Screen.Focus
            },
            onNavigateToAnalytics = { currentScreen = Screen.Analytics }
        )
        Screen.Completed -> CompletedScreen(
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Home }
        )
        Screen.Profile -> ProfileScreen(
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Home },
            onSettings = { currentScreen = Screen.Settings },
            onNavigateToAnalytics = { currentScreen = Screen.Analytics },
            onNavigateToFocus = { currentScreen = Screen.Focus }
        )
        Screen.Settings -> SettingsScreen(
            onBack = { currentScreen = Screen.Home }
        )
        Screen.ListAdmin -> {
            adminBookId?.let { bookId ->
                ListAdminScreen(
                    viewModel = viewModel,
                    bookId = bookId,
                    onBack = { currentScreen = Screen.Home }
                )
            } ?: run {
                currentScreen = Screen.Home
            }
        }
        Screen.Focus -> FocusScreen(
            viewModel = viewModel,
            initialTask = focusTask,
            onBack = { currentScreen = Screen.Home }
        )
        Screen.Analytics -> {
            val allTasks by viewModel.allTasks.collectAsState()
            val allBooks by viewModel.taskBooks.collectAsState()
            AnalyticsScreen(
                tasks = allTasks,
                books = allBooks,
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}
}

