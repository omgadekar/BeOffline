package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.scheduler.RuleScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleCreatorUiState(
    val name: String = "",
    val selectedPackages: List<String> = emptyList(),
    val ruleType: RuleType = RuleType.PERMANENT,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val timerMinutes: Int = 30,
    val isLoading: Boolean = false
) {
    val isValid: Boolean
        get() = name.isNotBlank() && selectedPackages.isNotEmpty()
}

@HiltViewModel
class RuleCreatorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BlockRuleRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleCreatorUiState())
    val uiState: StateFlow<RuleCreatorUiState> = _uiState.asStateFlow()

    fun loadRule(ruleId: Int) {
        viewModelScope.launch {
            val rule = repository.getRuleById(ruleId) ?: return@launch
            _uiState.update {
                it.copy(
                    name = rule.name,
                    selectedPackages = rule.blockedPackages,
                    ruleType = rule.ruleType,
                    startHour = rule.startHour,
                    startMinute = rule.startMinute,
                    endHour = rule.endHour,
                    endMinute = rule.endMinute,
                    activeDays = rule.activeDays ?: listOf(1, 2, 3, 4, 5),
                    timerMinutes = rule.timerDurationMinutes ?: 30
                )
            }
        }
    }

    /** Called directly by AppPickerScreen (via shared ViewModel reference) */
    fun onPackagesSelected(packages: List<String>) {
        _uiState.update { it.copy(selectedPackages = packages) }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }
    fun onRuleTypeChange(type: RuleType) = _uiState.update { it.copy(ruleType = type) }
    fun onStartTimeSet(hour: Int, minute: Int) = _uiState.update { it.copy(startHour = hour, startMinute = minute) }
    fun onEndTimeSet(hour: Int, minute: Int) = _uiState.update { it.copy(endHour = hour, endMinute = minute) }
    fun onTimerMinutesChange(mins: Int) = _uiState.update { it.copy(timerMinutes = mins) }

    fun onToggleDay(day: Int) {
        val days = _uiState.value.activeDays.toMutableList()
        if (day in days) days.remove(day) else days.add(day)
        _uiState.update { it.copy(activeDays = days.sorted()) }
    }

    fun saveRule() {
        val state = _uiState.value
        val rule = BlockRule(
            name = state.name.trim(),
            blockedPackages = state.selectedPackages,
            ruleType = state.ruleType,
            startHour = state.startHour,
            startMinute = state.startMinute,
            endHour = state.endHour,
            endMinute = state.endMinute,
            activeDays = state.activeDays,
            timerDurationMinutes = if (state.ruleType == RuleType.TIMER) state.timerMinutes else null
        )
        viewModelScope.launch {
            val id = repository.saveRule(rule)
            if (state.ruleType == RuleType.SCHEDULED) {
                RuleScheduler.scheduleRule(context, rule.copy(id = id.toInt()))
            }
        }
    }
}
