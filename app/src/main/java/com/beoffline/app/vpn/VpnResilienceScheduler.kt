package com.beoffline.app.vpn

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.beoffline.app.data.repository.BlockRuleRepository
import com.beoffline.app.receiver.VpnRecoveryReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

object VpnResilienceScheduler {

    private const val RECOVERY_REQUEST_CODE = 40_001
    private const val RECOVERY_WORK_NAME = "vpn_health_monitor"

    fun scheduleRecovery(context: Context, delayMillis: Long = 5_000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildRecoveryPendingIntent(
            context = context,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelRecovery(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildRecoveryPendingIntent(
            context = context,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun ensureHealthMonitor(context: Context) {
        val request = PeriodicWorkRequestBuilder<VpnHealthWorker>(15, TimeUnit.MINUTES)
            .addTag(RECOVERY_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECOVERY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelHealthMonitor(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(RECOVERY_WORK_NAME)
    }

    private fun buildRecoveryPendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, VpnRecoveryReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            RECOVERY_REQUEST_CODE,
            intent,
            flags
        )
    }
}

@HiltWorker
class VpnHealthWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BlockRuleRepository,
    private val vpnController: VpnController,
    private val vpnStateManager: VpnStateManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val activePackages = repository.getActiveBlockedPackages()
        if (activePackages.isEmpty()) {
            VpnResilienceScheduler.cancelHealthMonitor(applicationContext)
            return Result.success()
        }

        if (!vpnStateManager.isRunning.value) {
            vpnController.startVpn(activePackages)
        }

        return Result.success()
    }
}
