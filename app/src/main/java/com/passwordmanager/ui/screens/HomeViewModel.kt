package com.passwordmanager.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passwordmanager.data.PasswordEntity
import com.passwordmanager.data.PasswordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repo: PasswordRepository) : ViewModel() {

    val passwords = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(), emptyList())

    fun delete(entity: PasswordEntity) {
        viewModelScope.launch {
            repo.deletePassword(entity)
        }
    }
}