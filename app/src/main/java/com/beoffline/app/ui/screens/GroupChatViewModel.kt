package com.beoffline.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.data.model.ChatMessageCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AccountabilityRepository
) : ViewModel() {

    val groupId: String = savedStateHandle["groupId"] ?: ""
    val myUid: String? get() = repository.myUid()

    val messages: StateFlow<List<ChatMessageCache>> = repository.chatMessages(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _groupName = MutableStateFlow("Group chat")
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    init {
        viewModelScope.launch {
            repository.groups.collect { groups ->
                groups.firstOrNull { it.groupId == groupId }?.let { _groupName.value = it.name }
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        try {
            repository.refreshChat(groupId)
        } catch (_: Exception) {
            // Offline — cached history stands; sends still queue.
        }
    }

    fun send(text: String) = viewModelScope.launch {
        repository.sendChatMessage(groupId, text)
    }

    /** While the chat is on screen its messages shouldn't raise notifications. */
    fun setOnScreen(onScreen: Boolean) {
        val key = AccountabilityRepository.conversationKeyFor(groupId)
        if (onScreen) {
            repository.activeConversationKey = key
        } else if (repository.activeConversationKey == key) {
            repository.activeConversationKey = null
        }
    }
}
