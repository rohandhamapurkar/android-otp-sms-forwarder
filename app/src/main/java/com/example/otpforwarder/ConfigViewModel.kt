package com.example.otpforwarder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val configDao = AppDatabase.getDatabase(application).configDao()
    val allConfigs = configDao.getAllConfigs().asLiveData()

    init {
        viewModelScope.launch {
            try {
                LegacyConfigMigration.migrate(application, AppDatabase.getDatabase(application))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OTP_APP", "Legacy configuration retained after migration failure", e)
            }
        }
    }

    fun insert(config: ForwardConfig) = viewModelScope.launch {
        configDao.insertConfig(config)
    }

    fun update(config: ForwardConfig) = viewModelScope.launch {
        configDao.updateConfig(config)
    }

    fun delete(config: ForwardConfig) = viewModelScope.launch {
        configDao.deleteConfig(config)
    }
}
