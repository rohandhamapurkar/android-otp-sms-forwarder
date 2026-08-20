package com.example.otpforwarder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forward_configs")
data class ForwardConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val forwardTo: String,
    val filterKeywords: List<String>,
    val messagePrefix: String,
    val isEnabled: Boolean = true
)
