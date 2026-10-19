package com.example.otpforwarder

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LegacyConfigMigration {
    private val mutex = Mutex()

    suspend fun migrate(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = context.getSharedPreferences("forwarder_prefs", Context.MODE_PRIVATE)
            if (!prefs.contains("forward_to")) return@withLock
            val destination = prefs.getString("forward_to", "").orEmpty()
            val keywords = Converters().fromStringList(prefs.getString("filter_texts_json", "[]"))
            // Retain incomplete/corrupt legacy data rather than silently discarding it.
            if (destination.isBlank() || keywords.none { it.isNotBlank() }) return@withLock
            val legacy = ForwardConfig(
                forwardTo = destination,
                filterKeywords = keywords,
                messagePrefix = prefs.getString("prefix_text", "my lord here you go").orEmpty()
            )
            database.withTransaction {
                val dao = database.configDao()
                // A process may have stopped after the insert but before preference cleanup.
                if (dao.getConfigSnapshot().none { it.copy(id = 0, isEnabled = true) == legacy }) {
                    dao.insertConfig(legacy)
                }
            }
            check(prefs.edit().remove("forward_to").remove("prefix_text")
                .remove("include_filter").remove("filter_texts_json").commit()) {
                "Could not finish legacy configuration migration"
            }
        }
    }
}
