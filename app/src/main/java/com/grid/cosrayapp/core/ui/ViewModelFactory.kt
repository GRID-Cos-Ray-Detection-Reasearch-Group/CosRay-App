package com.grid.cosrayapp.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@Suppress("UNCHECKED_CAST")
fun <VM : ViewModel> viewModelFactory(initializer: () -> VM): ViewModelProvider.Factory =
  object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = initializer() as T
  }
