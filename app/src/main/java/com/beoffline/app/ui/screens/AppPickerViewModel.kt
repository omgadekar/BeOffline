package com.beoffline.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.data.model.AppInfo
import com.beoffline.app.data.repository.BlockRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppPickerUiState(
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val repository: BlockRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    /** Pre-populate selections when editing an existing rule */
    fun initializeSelection(packages: List<String>) {
        if (packages.isNotEmpty() && _uiState.value.selectedPackages.isEmpty()) {
            _uiState.update { it.copy(selectedPackages = packages.toSet()) }
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = repository.getInstalledApps()
            _uiState.update { it.copy(allApps = apps, filteredApps = apps, isLoading = false) }
        }
    }

    fun onSearch(query: String) {
        val filtered = if (query.isBlank()) {
            _uiState.value.allApps
        } else {
            _uiState.value.allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        _uiState.update { it.copy(searchQuery = query, filteredApps = filtered) }
    }

    fun toggleSelection(packageName: String) {
        val current = _uiState.value.selectedPackages.toMutableSet()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        _uiState.update { it.copy(selectedPackages = current) }
    }

    fun getSelectedPackages(): List<String> = _uiState.value.selectedPackages.toList()
}
