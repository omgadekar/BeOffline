package com.beoffline.app.ui.screens

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beoffline.app.background.BackgroundProtectionManager
import com.beoffline.app.data.local.AllowanceDao
import com.beoffline.app.data.local.PartnerDao
import com.beoffline.app.data.model.Allowance
import com.beoffline.app.data.model.AllowanceSource
import com.beoffline.app.data.model.BlockRule
import com.beoffline.app.data.model.OpenBlockRule
import com.beoffline.app.data.model.RuleType
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.beoffline.app.scheduler.OpenBlockScheduler
import com.beoffline.app.scheduler.RuleScheduler
import com.beoffline.app.util.firstName
import com.beoffline.app.vpn.VpnController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One app, as the list shows it. The rule behind it is an implementation
 * detail the row never names.
 */
data class AppRowUi(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val strictness: Strictness,
    val state: AppBlockState,
    val ruleId: Int,
    /** True when the rule behind this row is an open-block (App Lock) rule. */
    val isLock: Boolean
)

data class AppsUiState(
    val rows: List<AppRowUi> = emptyList(),
    val headline: String = "",
    val subline: String = "",
    val isLoading: Boolean = true,
    /**
     * A lock is switched on but the accessibility service behind it is not.
     * The rule looks active everywhere and enforces nothing, which is the one
     * failure this app must never keep quiet about.
     */
    val lockNotEnforcing: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * The home screen's model: ONE list of apps, built from BOTH rule tables.
 *
 * The app used to show "Rules" and "App Lock" as separate tabs, which forced
 * every user to learn which engine does what before they could do anything.
 * Here the engine is demoted to a per-app strictness, and the only thing on
 * screen is the list of apps you are staying away from.
 *
 * Where one app is covered by more than one rule, the row shows the rule that
 * is doing the most right now — see [PRIORITY].
 */
@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockRuleRepository: BlockRuleRepository,
    private val openBlockRuleRepository: OpenBlockRuleRepository,
    private val allowanceDao: AllowanceDao,
    private val partnerDao: PartnerDao,
    private val vpnController: VpnController,
    private val backgroundProtectionManager: BackgroundProtectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    /** Call on resume: the accessibility switch can be revoked behind our back. */
    fun refreshPermissions() {
        accessibilityOn = backgroundProtectionManager.isAccessibilityServiceEnabled()
    }

    private var accessibilityOn: Boolean = false

    /** packageName -> label and icon. Held for the life of the ViewModel. */
    private val nameCache = mutableMapOf<String, com.beoffline.app.data.model.AppInfo>()

    /**
     * Rows carry live countdowns, so the screen has to re-read the clock. A
     * second is the coarsest tick that still makes "4:02" look like it is
     * counting rather than stuck.
     */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                blockRuleRepository.getAllRules(),
                openBlockRuleRepository.getAllRules(),
                allowanceDao.getAll(),
                partnerDao.getActive(),
                ticker
            ) { blockRules, lockRules, allowances, partners, now ->
                val partnerName = partners.firstOrNull()?.partnerName
                build(blockRules, lockRules, allowances, partnerName, now)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private suspend fun build(
        blockRules: List<BlockRule>,
        lockRules: List<OpenBlockRule>,
        allowances: List<Allowance>,
        partnerName: String?,
        now: Long
    ): AppsUiState {
        // Every candidate row: one per (package, rule), both engines together.
        val candidates = mutableListOf<AppRowUi>()

        val liveAllowances = allowances.filter { it.grantedUntil > now }

        fun allowanceFor(packageName: String): Allowance? =
            liveAllowances.filter { it.packageName == packageName }.maxByOrNull { it.grantedUntil }

        lockRules.forEach { rule ->
            rule.blockedPackages.forEach { pkg ->
                val allowance = allowanceFor(pkg)
                candidates += AppRowUi(
                    packageName = pkg,
                    appName = pkg,
                    icon = null,
                    strictness = Strictness.Locked,
                    state = AppStateCopy.describe(
                        strictness = Strictness.Locked,
                        ruleType = rule.ruleType,
                        window = rule,
                        isActive = rule.isActive,
                        timerDurationMinutes = rule.timerDurationMinutes,
                        timerStartedAt = rule.timerStartedAt,
                        allowanceUntil = allowance?.grantedUntil,
                        allowanceGrantedBy = allowance?.let { grantedBy(it, partnerName) },
                        now = now
                    ),
                    ruleId = rule.id,
                    isLock = true
                )
            }
        }

        blockRules.forEach { rule ->
            rule.blockedPackages.forEach { pkg ->
                candidates += AppRowUi(
                    packageName = pkg,
                    appName = pkg,
                    icon = null,
                    strictness = Strictness.Offline,
                    state = AppStateCopy.describe(
                        strictness = Strictness.Offline,
                        ruleType = rule.ruleType,
                        window = rule,
                        isActive = rule.isActive,
                        timerDurationMinutes = rule.timerDurationMinutes,
                        timerStartedAt = rule.timerStartedAt,
                        // An internet block has no allowance concept of its own.
                        allowanceUntil = null,
                        now = now
                    ),
                    ruleId = rule.id,
                    isLock = false
                )
            }
        }

        // One row per app: the rule doing the most right now wins. Two rows for
        // the same icon would read as a bug, not as detail.
        val perApp = candidates
            .groupBy { it.packageName }
            .mapNotNull { (_, forApp) -> forApp.minByOrNull { PRIORITY.indexOf(it.state.status) } }

        val named = withNames(perApp)

        val sorted = named.sortedWith(
            compareBy<AppRowUi> { PRIORITY.indexOf(it.state.status) }
                .thenBy { it.appName.lowercase() }
        )

        val blockingNow = sorted.count {
            it.state.status == AppStatus.BlockedNow || it.state.status == AppStatus.BorrowedTime
        }
        val later = sorted.count { it.state.status == AppStatus.Later }

        val lockEnforcingNow = sorted.any {
            it.isLock && (it.state.status == AppStatus.BlockedNow || it.state.status == AppStatus.Later)
        }

        return AppsUiState(
            rows = sorted,
            headline = headlineFor(blockingNow),
            subline = sublineFor(blockingNow, later),
            isLoading = false,
            lockNotEnforcing = lockEnforcingNow && !accessibilityOn
        )
    }

    /**
     * Resolves real labels and launcher icons for the packages actually shown.
     *
     * The rows rebuild once a second so the countdowns tick, and PackageManager
     * is far too expensive to ask at that rate — so a package is looked up once
     * and then held. Nothing here changes while the app is running, short of an
     * install or uninstall, and an uninstalled app simply stops matching a rule.
     */
    private suspend fun withNames(rows: List<AppRowUi>): List<AppRowUi> {
        val missing = rows.map { it.packageName }.distinct().filterNot { it in nameCache }
        if (missing.isNotEmpty()) {
            try {
                blockRuleRepository.getAppInfoForPackages(missing).forEach { info ->
                    nameCache[info.packageName] = info
                }
            } catch (_: Exception) {
                // Leave them as package names rather than dropping the row: a row
                // the user cannot read still beats an app that silently vanishes.
            }
        }
        return rows.map { row ->
            val info = nameCache[row.packageName] ?: return@map row
            row.copy(appName = info.appName, icon = info.icon)
        }
    }

    private fun grantedBy(allowance: Allowance, partnerName: String?): String? =
        when (allowance.source) {
            // The user did the work themselves; naming nobody is the honest line.
            AllowanceSource.TEASER -> null
            AllowanceSource.PARTNER -> partnerName.firstName(fallback = "Your partner")
            AllowanceSource.GROUP -> "Your group"
        }

    private fun headlineFor(count: Int): String = when (count) {
        0 -> "Nothing is blocked right now."
        1 -> "One app is blocked right now."
        else -> "${spell(count)} apps are blocked right now."
    }

    private fun sublineFor(blockingNow: Int, later: Int): String {
        val tail = "The rest of your phone works normally."
        return when {
            blockingNow == 0 && later == 0 ->
                "Pick an app and it stays out of reach until you say otherwise."
            later == 0 -> tail
            later == 1 -> "One more starts later. $tail"
            else -> "${spell(later)} more start later. $tail"
        }
    }

    /** Small counts read better as words; past ten, the digit is clearer. */
    private fun spell(count: Int): String = when (count) {
        2 -> "Two"
        3 -> "Three"
        4 -> "Four"
        5 -> "Five"
        6 -> "Six"
        7 -> "Seven"
        8 -> "Eight"
        9 -> "Nine"
        10 -> "Ten"
        else -> count.toString()
    }

    // Actions ────────────────────────────────────────────────────────────────

    /**
     * Pauses a block. Turning OFF a lock that is actively enforcing runs the
     * 24-hour cooldown instead, and that path stays in OpenBlockViewModel where
     * the partner notification lives — this one only handles the honest cases.
     */
    fun setPaused(row: AppRowUi, paused: Boolean, onNeedVpnPermission: (List<String>) -> Unit) {
        viewModelScope.launch {
            if (row.isLock) {
                val rule = openBlockRuleRepository.getRuleById(row.ruleId) ?: return@launch
                openBlockRuleRepository.setRuleActive(rule.id, !paused)
                if (rule.ruleType == RuleType.TIMER) {
                    if (paused) {
                        openBlockRuleRepository.setTimerStartedAt(rule.id, null)
                        OpenBlockScheduler.cancelTimerStop(context, rule.id)
                    } else {
                        val startedAt = System.currentTimeMillis()
                        openBlockRuleRepository.setTimerStartedAt(rule.id, startedAt)
                        OpenBlockScheduler.scheduleTimerStop(
                            context,
                            rule.copy(isActive = true, timerStartedAt = startedAt)
                        )
                    }
                }
            } else {
                val rule = blockRuleRepository.getRuleById(row.ruleId) ?: return@launch
                blockRuleRepository.setRuleActive(rule.id, !paused)
                if (rule.ruleType == RuleType.TIMER) {
                    if (paused) {
                        blockRuleRepository.setTimerStartedAt(rule.id, null)
                        RuleScheduler.cancelTimerStop(context, rule.id)
                    } else {
                        val startedAt = System.currentTimeMillis()
                        blockRuleRepository.setTimerStartedAt(rule.id, startedAt)
                        RuleScheduler.scheduleTimerStop(
                            context,
                            rule.copy(isActive = true, timerStartedAt = startedAt)
                        )
                    }
                }
                val stillOn = blockRuleRepository.getActiveRulesOnce()
                    .flatMap { it.blockedPackages }
                    .distinct()
                if (stillOn.isEmpty()) vpnController.stopVpn() else onNeedVpnPermission(stillOn)
            }
        }
    }

    private companion object {
        /**
         * Which state wins when one app is covered by several rules, and the
         * order the list is sorted in. Borrowed time first because it is the
         * one row with a deadline on it.
         */
        val PRIORITY = listOf(
            AppStatus.BorrowedTime,
            AppStatus.BlockedNow,
            AppStatus.Later,
            AppStatus.Paused
        )
    }
}
