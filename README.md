# TaskX (ModernToDo) 📱

A modern, offline-first Android task management and project organizer built with **Jetpack Compose**, **Material 3**, **Room Database**, and **Supabase Realtime Sync**.

---

## ✨ Features

- **🎯 Task Management:** Clean, compact task cards with swipe-to-dismiss, instant completion toggles, priority levels (`P1` Urgent, `P2` High, `P3` Medium, `P4` Low), tags, and smart metadata.
- **📁 Projects & Notes Feed:** Unified project workspace combining notes and actionable checklist tasks in a clean, chronological feed. Direct 1-tap task creation right into notes.
- **📅 Advanced Calendar & Planner:**
  - **Month View:** Interactive 7x6 calendar grid with multi-color priority dots.
  - **Week View:** Focused 7-day horizontal strip with day-by-day navigation.
  - **Agenda View:** Continuous timeline feed grouping tasks into Overdue, Today, Tomorrow, This Week, Later, and Completed.
  - **1-Tap Quick Add:** Schedule tasks directly for any selected date.
- **⏱️ Focus Mode:** Pomodoro-style focus timer with session logs and productivity analytics.
- **☁️ Cloud Sync & Sharing:** Supabase backend integration for realtime synchronization across devices and QR code shared lists.
- **👤 Profile & Security:** Account management, offline data backup and restore, and customizable app settings.

---

## 🛠️ Tech Stack & Architecture

- **UI:** Jetpack Compose, Material 3, Compose Navigation
- **Architecture:** MVVM (Model-View-ViewModel) with Kotlin Coroutines & StateFlow
- **Local Persistence:** Android Room Database & SQLite
- **Remote / Backend:** Supabase (Auth & Realtime Database), Retrofit & Gson
- **Tooling:** Gradle (Kotlin DSL), KSP, Coil Compose

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17
- Android SDK 36 (targetSdk 36, minSdk 24)

### Building and Running
1. Clone the repository:
   ```bash
   git clone <REPO_URL>
   cd ModernToDo
   ```
2. Open the project in Android Studio.
3. Sync Gradle and run on an Android device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 License
This project is open source and available under the MIT License.
