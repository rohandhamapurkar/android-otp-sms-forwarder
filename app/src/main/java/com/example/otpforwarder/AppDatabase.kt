package com.example.otpforwarder

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(entities = [ForwardConfig::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao

    companion object {
        /**
         * Every schema change needs a migration registered here, or existing installs crash on
         * upgrade. Bump the @Database version, let KSP export the new schema under app/schemas,
         * then append the migration. Example for adding a nullable column:
         *
         *     private val MIGRATION_1_2 = Migration(1, 2) { db ->
         *         db.execSQL("ALTER TABLE forward_configs ADD COLUMN includeFilter INTEGER NOT NULL DEFAULT 0")
         *     }
         */
        private val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Re-check: another thread may have built the instance while we waited.
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "otp_forwarder_database"
                )
                    .addMigrations(*MIGRATIONS)
                    // Downgrades (a user sideloading an older APK) have no forward path, so
                    // rebuild rather than crash. Upgrades still require a real migration above.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
