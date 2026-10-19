package com.example.otpforwarder

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM forward_configs")
    suspend fun getConfigSnapshot(): List<ForwardConfig>

    @Query("SELECT * FROM forward_configs")
    fun getAllConfigs(): Flow<List<ForwardConfig>>

    @Query("SELECT * FROM forward_configs WHERE isEnabled = 1")
    suspend fun getActiveConfigs(): List<ForwardConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ForwardConfig)

    @Update
    suspend fun updateConfig(config: ForwardConfig)

    @Delete
    suspend fun deleteConfig(config: ForwardConfig)
}
