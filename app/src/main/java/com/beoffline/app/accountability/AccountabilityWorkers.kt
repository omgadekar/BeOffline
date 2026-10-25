package com.beoffline.app.accountability

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.beoffline.app.background.BackgroundProtectionManager
import com.beoffline.app.data.repository.OpenBlockRuleRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Drains the offline outbox whenever the network allows. Retries with backoff;
 * WorkManager persistence means queued requests/tamper events survive reboots.
 */
@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AccountabilityRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "accountability_outbox"

        /**
         * REPLACE, not KEEP. Unique work that asked to be retried sits in
         * WorkManager's exponential backoff — up to five hours of it — and
         * KEEP made every later enqueue a no-op for that whole window. One
         * failed send was therefore enough to silence the queue: later
         * messages and unlock requests were written to the outbox and simply
         * never left the device, which is what "my messages stopped arriving
         * on the other phone" looked like from the outside.
         *
         * Replacing cancels the waiting attempt and starts a fresh one now.
         * Cancelling a drain that happens to be running is safe: every send is
         * idempotent on its client key, and the rows stay in the outbox until
         * the server has acknowledged them.
         */
        fun enqueue(context: Context) {
            val work = OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }
    }

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return if (repository.drainOutbox()) Result.success() else Result.retry()
    }
}

/**
 * Periodic liveness + self-check:
 *  - server heartbeat (its absence is the server's uninstall signal)
 *  - protection check: active open-block rules but accessibility OFF →
 *    report ACCESSIBILITY_DISABLED (deduped per day)
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AccountabilityRepository,
    private val openBlockRuleRepository: OpenBlockRuleRepository,
    private val backgroundProtectionManager: BackgroundProtectionManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "accountability_heartbeat"

        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<HeartbeatWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()

        repository.heartbeat()
        // A second, slower chance for anything the outbox still owes — the
        // per-send enqueue is the fast path, this is the one that runs even if
        // the app is never opened.
        repository.drainOutbox()

        val hasActiveRules = openBlockRuleRepository.getActiveRulesOnce().isNotEmpty()
        val accessibilityOn = backgroundProtectionManager.isAccessibilityServiceEnabled()
        if (hasActiveRules && !accessibilityOn && (repository.hasPartner() || repository.hasGroup())) {
            val day = java.time.LocalDate.now().toString()
            repository.reportTamper(
                type = "ACCESSIBILITY_DISABLED",
                packageName = null,
                dedupeKey = "a11y-off-$day"
            )
        }

        return Result.success()
    }
}
