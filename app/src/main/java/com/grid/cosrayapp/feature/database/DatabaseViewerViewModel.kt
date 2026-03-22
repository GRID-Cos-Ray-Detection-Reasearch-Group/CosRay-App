@file:Suppress("MagicNumber")

package com.grid.cosrayapp.feature.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grid.cosrayapp.data.telemetry.DatabaseInspectionRepository
import com.grid.cosrayapp.data.telemetry.DatabaseInspectionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DatabaseViewerViewModel @Inject constructor(
    repository: DatabaseInspectionRepository
) : ViewModel() {

    val uiState: StateFlow<DatabaseInspectionSnapshot> = repository
        .observeInspection(limit = 100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DatabaseInspectionSnapshot()
        )
}
