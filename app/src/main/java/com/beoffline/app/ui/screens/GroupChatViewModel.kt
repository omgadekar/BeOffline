package com.beoffline.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.util.disambiguatedFirstNames
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

    /** Mentionable members (everyone but me) — for the @ picker. */
    private val _members = MutableStateFlow<List<ChatMember>>(emptyList())
    val members: StateFlow<List<ChatMember>> = _members.asStateFlow()

    /** All member names (including mine) — for highlighting @tokens in bubbles. */
    private val _mentionNames = MutableStateFlow<List<String>>(emptyList())
    val mentionNames: StateFlow<List<String>> = _mentionNames.asStateFlow()

    /** uid → the name to show in chat: first name, or "Priya S." if two share one. */
    private val _chatNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val chatNames: StateFlow<Map<String, String>> = _chatNames.asStateFlow()

    /** Only the group's owner can remove someone else; everyone can leave. */
    private val _isOwner = MutableStateFlow(false)
    val isOwner: StateFlow<Boolean> = _isOwner.asStateFlow()

    /** One-line result of a report / leave / remove, shown and then dismissed. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** True while a report / leave / remove is in flight. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** True once leaving succeeded, so the screen can close itself. */
    private val _left = MutableStateFlow(false)
    val left: StateFlow<Boolean> = _left.asStateFlow()

    init {
        viewModelScope.launch {
            repository.groups.collect { groups ->
                groups.firstOrNull { it.groupId == groupId }?.let { group ->
                    val members = repository.membersOf(group)
                    _groupName.value = group.name
                    _memberCount.value = members.size
                    _isOwner.value = myUid != null && group.ownerUid == myUid

                    // Chat names people by first name — you know who these people
                    // are. Two Priyas keep a surname initial so a mention still
                    // lands on exactly one person.
                    val named = members.filter { !it.displayName.isNullOrBlank() }
                    val shortNames = disambiguatedFirstNames(named.map { it.displayName!! })
                    val byUid = named.associate { it.uid to (shortNames[it.displayName] ?: it.displayName!!) }

                    _chatNames.value = byUid
                    _members.value = named
                        .filter { it.uid != myUid }
                        .mapNotNull { member -> byUid[member.uid]?.let { ChatMember(member.uid, it) } }
                    _mentionNames.value = byUid.values.toList()
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

    // ── Moderation ───────────────────────────────────────────────────────────
    // Play's user-generated-content policy: a chat has to offer a way to report
    // a message and a way to get away from whoever sent it.

    fun report(messageId: String, reason: ReportReason) = launchBusy {
        try {
            val result = repository.reportChatMessage(groupId, messageId, reason.code)
            _notice.value = if (result.alreadyReported) {
                "You had already reported this. It is with us."
            } else {
                "Reported. We look at every report and will act on this one."
            }
        } catch (e: Exception) {
            _notice.value = friendly(e)
        }
    }

    /** Owner only — starts the same visible cooldown as removing from the group page. */
    fun removeMember(memberUid: String) = launchBusy {
        val name = _chatNames.value[memberUid] ?: "That member"
        try {
            repository.removeGroupMember(groupId, memberUid)
            _notice.value = "$name is on their way out. Everyone was told, and the membership " +
                "stays live through the cooldown."
        } catch (e: Exception) {
            _notice.value = friendly(e)
        }
    }

    fun leaveGroup() = launchBusy {
        try {
            val me = myUid ?: return@launchBusy
            repository.removeGroupMember(groupId, me)
            _left.value = true
        } catch (e: Exception) {
            _notice.value = friendly(e)
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }

    private fun friendly(e: Exception): String = when {
        e is retrofit2.HttpException && e.code() == 404 -> "That message is no longer there."
        e is retrofit2.HttpException && e.code() == 409 -> "Already done."
        e is retrofit2.HttpException && e.code() == 403 -> "Not allowed."
        e is retrofit2.HttpException && e.code() == 400 -> "The server would not take that."
        e is java.io.IOException -> "No connection — try again when you're online."
        else -> e.message ?: "Something went wrong."
    }

    /** What someone can say is wrong with a message. Codes match the API's fixed set. */
    enum class ReportReason(val code: String, val label: String) {
        Spam("spam", "Spam"),
        Harassment("harassment", "Harassment or bullying"),
        Hate("hate", "Hate speech"),
        Sexual("sexual", "Sexual content"),
        Violence("violence", "Threats or violence"),
        SelfHarm("selfharm", "Self-harm"),
        Other("other", "Something else")
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
