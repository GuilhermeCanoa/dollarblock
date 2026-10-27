package com.dollarblock.service.accessibility

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dollarblock.R
import com.dollarblock.data.local.prefs.NotificationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispara o aviso passivo-agressivo de "faltam N min de limite" (ver [LimitWarningPolicy]
 * para o gatilho). Voz "gerente do banco de tempo": nunca motivacional, sempre cobrança.
 */
@Singleton
class LimitWarningNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationPreferences: NotificationPreferences,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    suspend fun notifyLimitApproaching(appLabel: String, minutesRemaining: Int) {
        if (!notificationPreferences.isEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.notif_limit_warning_title,
                    minutesRemaining,
                    minutesRemaining,
                    appLabel,
                ),
            )
            .setContentText(context.getString(R.string.notif_limit_warning_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(appLabel.hashCode(), notification)
    }

    /** Toque na notificação abre o DollarBlock (antes não fazia nada além de sumir). */
    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL_ID = "limit_warning"
    }
}
