package com.beoffline.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.accountability.AuthManager
import com.beoffline.app.accountability.GroupMemberDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupUi(
    val groupId: String,
    val name: String,
    val ownerUid: String,
    val members: List<GroupMemberDto>
)

data class GroupsUiState(
    val signedIn: Boolean = false,
    val myUid: String? = null,
    val groups: List<GroupUi> = emptyList(),
    /** Last created invite code, tied to the group it belongs to. */
    val inviteCode: String? = null,
    val inviteGroupId: String? = null,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val repository: AccountabilityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _uiState.update { it.copy(signedIn = user != null, myUid = user?.uid) }
                if (user != null) refresh()
            }
        }
        viewModelScope.launch {
            repository.groups.collect { groups ->
                _uiState.update { state ->
                    state.copy(groups = groups.map {
                        GroupUi(it.groupId, it.name, it.ownerUid, repository.membersOf(it))
                    })
                }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        try {
            repository.refreshGroups()
        } catch (_: Exception) {
            // Offline — cached data stands.
        }
    }

    fun createGroup(name: String) = launchBusy {
        try {
            repository.createGroup(name.trim())
            _uiState.update { it.copy(message = "Group created — share an invite code to add members.") }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun createInvite(groupId: String) = launchBusy {
        try {
            val invite = repository.createGroupInvite(groupId)
            _uiState.update { it.copy(inviteCode = invite.code, inviteGroupId = groupId) }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun joinGroup(code: String) = launchBusy {
        try {
            val dto = repository.joinGroup(code.trim().uppercase())
            _uiState.update { it.copy(message = "You joined ${dto.name}.") }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    /** Leaving yourself, or (as owner) removing someone — either way the cooldown starts. */
    fun removeMember(groupId: String, memberUid: String) = launchBusy {
        try {
            repository.removeGroupMember(groupId, memberUid)
            _uiState.update {
                it.copy(message = "Removal started — everyone in the group was notified. The membership stays active through the cooldown.")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private fun friendly(e: Exception): String = when {
        e is retrofit2.HttpException && e.code() == 404 -> "Invalid or expired code."
        e is retrofit2.HttpException && e.code() == 409 -> "Not possible — the group may be full, or this is already done."
        e is retrofit2.HttpException && e.code() == 403 -> "Not allowed."
        e is retrofit2.HttpException && e.code() == 400 -> "The server rejected that — check the input."
        e is java.io.IOException -> "No connection — try again when you're online."
        else -> e.message ?: "Something went wrong."
    }
}
