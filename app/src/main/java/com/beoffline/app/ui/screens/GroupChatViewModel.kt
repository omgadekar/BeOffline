package com.beoffline.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.accountability.GroupMemberDto
import com.beoffline.app.accountability.serverMessage
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.util.disambiguatedFirstNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    /**
     * Unlock requests raised in this group. They belong in the conversation
     * rather than on the People tab: a group request is addressed to whoever
     * is around, and the chat is where "whoever is around" is already looking.
     */
    val requests: StateFlow<List<CachedUnlockRequest>> = repository.groupRequests(groupId)
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

    /** Who is in here and who owns it — for the group-info sheet. */
    private val _info = MutableStateFlow<GroupInfo?>(null)
    val info: StateFlow<GroupInfo?> = _info.asStateFlow()

    /**
     * When the owner has started removing *you*, the moment it takes effect.
     * Null otherwise. Your own leave is immediate, so this only ever describes
     * somebody else's decision about you — which is exactly the thing that
     * should not happen silently.
     */
    private val _removalAt = MutableStateFlow<Long?>(null)
    val removalAt: StateFlow<Long?> = _removalAt.asStateFlow()

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

                    _info.value = GroupInfo(group.name, group.ownerUid, members)
                    _removalAt.value = members
                        .firstOrNull { it.uid == myUid && it.removalPending }
                        ?.removalEffectiveAtUtc
                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

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

    fun refresh() = viewModelScope.launch { refreshQuietly() }

    private suspend fun refreshQuietly() {
        try {
            repository.refreshChat(groupId)
            repository.refreshRequests()
        } catch (_: Exception) {
            // Offline — cached history stands; sends still queue.
        }
    }

    /** Answer a request from inside the conversation it was raised in. */
    fun respond(request: CachedUnlockRequest, approve: Boolean, durationMinutes: Int?) = launchBusy {
        try {
            repository.respondToRequest(request.id, approve, durationMinutes)
        } catch (e: Exception) {
            _notice.value = friendly(e)
        }
    }

    /** Push a message that never made it out back onto the queue. */
    fun retry(message: ChatMessageCache) = viewModelScope.launch {
        repository.retryChatMessage(groupId, message)
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

    /** Immediate: out of the member list, out of the chat, out of reach. */
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

    /**
     * The server explains itself in a {"message": "..."} body, and it says more
     * than a status code can. "Already done." was this screen's whole answer to
     * every 409, which told nobody anything.
     */
    private fun friendly(e: Exception): String = when (e) {
        is retrofit2.HttpException -> e.serverMessage() ?: when (e.code()) {
            404 -> "That is no longer there."
            409 -> "That has already happened."
            403 -> "You cannot approve in this group yet \u2014 a new member waits out a cooldown first."
            400 -> "The server would not take that."
            else -> e.message()
        }
        is java.io.IOException -> "No connection — try again when you're online."
        else -> e.message ?: "Something went wrong."
    }

    /** A group as the info sheet shows it. */
    data class GroupInfo(
        val name: String,
        val ownerUid: String,
        val members: List<GroupMemberDto>
    )

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

    /**
     * While the chat is on screen its messages shouldn't raise notifications —
     * and it should not be relying on push alone to stay current. A dropped or
     * battery-deferred data message is invisible from here and leaves the
     * conversation looking live while it is quietly a few messages behind, so
     * an open chat pulls on a short cycle as well.
     */
    fun setOnScreen(onScreen: Boolean) {
        val key = AccountabilityRepository.conversationKeyFor(groupId)
        if (onScreen) {
            repository.activeConversationKey = key
            startPolling()
        } else {
            if (repository.activeConversationKey == key) repository.activeConversationKey = null
            poll?.cancel()
            poll = null
        }
    }

    private var poll: Job? = null

    private fun startPolling() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refreshQuietly()
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 15_000L
    }
}
