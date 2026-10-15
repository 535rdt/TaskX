package com.example.moderntodo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, TaskBookEntity::class, ProjectNoteEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskBookDao(): TaskBookDao
    abstract fun projectNoteDao(): ProjectNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 4")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isProjectTask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN projectId INTEGER")
                db.execSQL("ALTER TABLE task_books ADD COLUMN isProject INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN assignee TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS project_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        projectId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        attachedTaskIds TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN syncId TEXT")
            }
        }

        private val dbInstances = java.util.concurrent.ConcurrentHashMap<String, AppDatabase>()

        fun sanitizeUserEmail(email: String?): String {
            if (email.isNullOrBlank()) return "guest"
            return email.lowercase().trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
        }

        fun getDatabase(
            context: Context,
            userEmail: String? = com.example.moderntodo.util.SettingsRepository.userEmailFlow.value
        ): AppDatabase {
            val sanitized = sanitizeUserEmail(userEmail)
            val dbName = if (sanitized == "guest") "moderntodo_database" else "moderntodo_user_${sanitized}.db"

            return dbInstances.computeIfAbsent(dbName) {
                val appContext = context.applicationContext

                // Migrate legacy data for original account if target database doesn't exist yet
                if (sanitized != "guest" && (userEmail?.contains("muhammadirfannkm342", ignoreCase = true) == true)) {
                    val targetFile = appContext.getDatabasePath(dbName)
                    val legacyFile = appContext.getDatabasePath("moderntodo_database")
                    if (!targetFile.exists() && legacyFile.exists()) {
                        try {
                            targetFile.parentFile?.mkdirs()
                            legacyFile.copyTo(targetFile, overwrite = true)
                            val legacyWal = appContext.getDatabasePath("moderntodo_database-wal")
                            if (legacyWal.exists()) {
                                legacyWal.copyTo(appContext.getDatabasePath("$dbName-wal"), overwrite = true)
                            }
                            val legacyShm = appContext.getDatabasePath("moderntodo_database-shm")
                            if (legacyShm.exists()) {
                                legacyShm.copyTo(appContext.getDatabasePath("$dbName-shm"), overwrite = true)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppDatabase", "Error migrating legacy database to $dbName", e)
                        }
                    }
                }

                Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}
