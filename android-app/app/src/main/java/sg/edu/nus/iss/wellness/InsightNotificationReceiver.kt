package sg.edu.nus.iss.wellness

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.api.ApiClient
import sg.edu.nus.iss.wellness.api.RecommendationResponse

/**
 * Handles local generated-insight broadcasts and scheduled recommendation checks.
 *
 * @author Tiong Zhong Cheng
 */
class InsightNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            InsightNotificationScheduler.ACTION_INSIGHT_GENERATED -> notifyFromIntent(context, intent)
            InsightNotificationScheduler.ACTION_POLL_INSIGHTS -> pollLatestRecommendation(context)
        }
    }

    private fun notifyFromIntent(context: Context, intent: Intent) {
        val id = intent.getLongExtra(InsightNotificationScheduler.EXTRA_RECOMMENDATION_ID, -1L)
        if (id <= 0L) return
        val title = intent.getStringExtra(InsightNotificationScheduler.EXTRA_RECOMMENDATION_TITLE).orEmpty()
        val text = intent.getStringExtra(InsightNotificationScheduler.EXTRA_RECOMMENDATION_TEXT).orEmpty()
        showNotification(context, id, title, text)
        saveLastNotifiedId(context, id)
    }

    private fun pollLatestRecommendation(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val tokenStore = TokenStore(appContext)
                if (tokenStore.token().isNullOrBlank()) return@launch

                val latest = runCatching {
                    ApiClient.create(tokenStore).recommendations().firstOrNull()
                }.getOrNull() ?: return@launch
                val lastId = lastNotifiedId(appContext)
                if (lastId < 0L) {
                    saveLastNotifiedId(appContext, latest.id)
                    return@launch
                }
                if (latest.id > lastId) {
                    showNotification(
                        appContext,
                        latest.id,
                        latest.title,
                        InsightNotificationScheduler.notificationText(latest)
                    )
                    saveLastNotifiedId(appContext, latest.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, recommendationId: Long, title: String, text: String) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(manager)

        val openIntent = Intent(context, RecommendationsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            recommendationId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentTitle = title.ifBlank { "New wellness insight" }
        val contentText = text.ifBlank { "Your latest generated wellness insight is ready." }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_insight)
            .setContentTitle("New wellness insight")
            .setContentText(contentTitle)
            .setStyle(Notification.BigTextStyle().bigText("$contentTitle\n\n$contentText"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(recommendationId.toInt(), notification)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wellness insights",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Generated wellness recommendation notifications"
        }
        manager.createNotificationChannel(channel)
    }

    private fun lastNotifiedId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_ID, -1L)

    private fun saveLastNotifiedId(context: Context, id: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ID, id)
            .apply()
    }

    private companion object {
        const val CHANNEL_ID = "wellness_insights"
        const val PREFS = "wellness_insight_notifications"
        const val KEY_LAST_ID = "last_notified_recommendation_id"
    }
}
