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

    private val _memberCount = MutableStateFlow(0)
    val memberCount: StateFlow<Int> = _memberCount.asStateFlow()

    /** Mentionable members (everyone but me, with a display name) — for the @ picker. */
    private val _members = MutableStateFlow<List<ChatMember>>(emptyList())
    val members: StateFlow<List<ChatMember>> = _members.asStateFlow()

    /** All member names (including mine) — for highlighting @tokens in bubbles. */
    private val _mentionNames = MutableStateFlow<List<String>>(emptyList())
    val mentionNames: StateFlow<List<String>> = _mentionNames.asStateFlow()

    init {
        viewModelScope.launch {
            repository.groups.collect { groups ->
                groups.firstOrNull { it.groupId == groupId }?.let { group ->
                    val members = repository.membersOf(group)
                    _groupName.value = group.name
                    _memberCount.value = members.size
                    _members.value = members
                        .filter { it.uid != myUid && !it.displayName.isNullOrBlank() }
                        .map { ChatMember(it.uid, it.displayName!!) }
                    _mentionNames.value = members.mapNotNull { it.displayName?.takeIf(String::isNotBlank) }
                }
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
        val body = text.trim()
        if (body.isEmpty()) return@launch
        // Resolve @-mentions from the final text against real members — robust to
        // multi-word names and to the user editing after inserting a suggestion.
        val mentioned = _members.value
            .filter { body.contains("@${it.name}") }
            .map { it.uid }
            .distinct()
        repository.sendChatMessage(groupId, body, mentioned)
    }

    /** A member that can be @-mentioned. */
    data class ChatMember(val uid: String, val name: String)

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
