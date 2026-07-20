package com.beoffline.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.accountability.AuthManager
import com.beoffline.app.accountability.RealtimeClient
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.Partner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountabilityUiState(
    val signedIn: Boolean = false,
    val userName: String? = null,
    val googleConfigured: Boolean = true,
    val partners: List<Partner> = emptyList(),
    val incomingPending: List<CachedUnlockRequest> = emptyList(),
    val recentRequests: List<CachedUnlockRequest> = emptyList(),
    val inviteCode: String? = null,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class AccountabilityViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val repository: AccountabilityRepository,
    private val realtimeClient: RealtimeClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountabilityUiState())
    val uiState: StateFlow<AccountabilityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _uiState.update { it.copy(signedIn = user != null, userName = user?.displayName) }
                if (user != null) refresh()
            }
        }
        viewModelScope.launch {
            repository.partners.collect { partners ->
                _uiState.update { it.copy(partners = partners) }
            }
        }
        viewModelScope.launch {
            repository.incomingPending.collect { incoming ->
                _uiState.update { it.copy(incomingPending = incoming) }
            }
        }
        viewModelScope.launch {
            repository.allRequests.collect { all ->
                _uiState.update { it.copy(recentRequests = all.take(10)) }
            }
        }
    }

    fun checkConfiguration(context: Context) {
        _uiState.update { it.copy(googleConfigured = authManager.isGoogleSignInConfigured(context)) }
    }

    /** [activityContext] must be an Activity (Credential Manager shows UI). */
    fun signIn(activityContext: Context) = launchBusy {
        authManager.signIn(activityContext)
            .onSuccess {
                repository.registerDevice()
                // The foreground SignalR connection skipped itself pre-sign-in.
                realtimeClient.start()
                refreshQuietly()
            }
            .onFailure { e -> _uiState.update { it.copy(message = e.message) } }
    }

    fun signOut() {
        authManager.signOut()
        _uiState.update { it.copy(inviteCode = null, message = null) }
    }

    fun createInvite() = launchBusy {
        try {
            val invite = repository.createInvite()
            _uiState.update { it.copy(inviteCode = invite.code) }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun acceptInvite(code: String) = launchBusy {
        try {
            repository.acceptInvite(code.trim().uppercase())
            _uiState.update { it.copy(message = "Paired successfully.") }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun removePartner(partner: Partner) = launchBusy {
        try {
            repository.removePartner(partner.pairingId)
            _uiState.update {
                it.copy(message = "Removal started — your partner was notified. The pairing stays active for the cooldown.")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun respond(request: CachedUnlockRequest, approve: Boolean, durationMinutes: Int?) = launchBusy {
        try {
            repository.respondToRequest(request.id, approve, durationMinutes)
        } catch (e: Exception) {
            _uiState.update { it.copy(message = friendly(e)) }
        }
    }

    fun refresh() = viewModelScope.launch { refreshQuietly() }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun refreshQuietly() {
        try {
            repository.refreshPartners()
            repository.refreshRequests()
            repository.refreshGroups()
        } catch (_: Exception) {
            // Offline — cached data stands.
        }
    }

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
        e is retrofit2.HttpException && e.code() == 409 -> "Already done."
        e is retrofit2.HttpException && e.code() == 403 -> "Not allowed yet (approval cooldown may be active)."
        e is java.io.IOException -> "No connection — try again when you're online."
        else -> e.message ?: "Something went wrong."
    }
}
