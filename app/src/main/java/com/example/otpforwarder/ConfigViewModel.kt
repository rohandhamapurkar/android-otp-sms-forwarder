package com.example.otpforwarder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val configDao = AppDatabase.getDatabase(application).configDao()
    val allConfigs = configDao.getAllConfigs().asLiveData()

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
