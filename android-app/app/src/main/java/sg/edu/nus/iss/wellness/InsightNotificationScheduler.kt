package sg.edu.nus.iss.wellness

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import sg.edu.nus.iss.wellness.api.RecommendationResponse

/**
 * Registers the local scheduled broadcast used for generated-insight notifications.
 *
 * @author Tiong Zhong Cheng
 */
object InsightNotificationScheduler {
    const val ACTION_POLL_INSIGHTS = "sg.edu.nus.iss.wellness.action.POLL_INSIGHTS"
    const val ACTION_INSIGHT_GENERATED = "sg.edu.nus.iss.wellness.action.INSIGHT_GENERATED"
    const val EXTRA_RECOMMENDATION_ID = "extra_recommendation_id"
    const val EXTRA_RECOMMENDATION_TITLE = "extra_recommendation_title"
    const val EXTRA_RECOMMENDATION_TEXT = "extra_recommendation_text"

    private const val REQUEST_NOTIFICATIONS = 7401
    private const val POLL_REQUEST_CODE = 7402
    private const val INITIAL_DELAY_MS = 30 * 1000L
    private const val POLL_INTERVAL_MS = 2 * 60 * 1000L

    fun prepare(activity: Activity) {
        requestNotificationPermissionIfNeeded(activity)
        ensureScheduled(activity.applicationContext)
    }

    fun ensureScheduled(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INITIAL_DELAY_MS,
            POLL_INTERVAL_MS,
            pollIntent(context)
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pollIntent(context))
    }

    fun broadcastGeneratedInsight(context: Context, recommendation: RecommendationResponse) {
        val intent = Intent(context, InsightNotificationReceiver::class.java)
            .setAction(ACTION_INSIGHT_GENERATED)
            .putExtra(EXTRA_RECOMMENDATION_ID, recommendation.id)
            .putExtra(EXTRA_RECOMMENDATION_TITLE, recommendation.title)
            .putExtra(EXTRA_RECOMMENDATION_TEXT, notificationText(recommendation))
        context.sendBroadcast(intent)
    }

    fun notificationText(recommendation: RecommendationResponse): String =
        recommendation.recommendationText.ifBlank { recommendation.trendSummary }.take(180)

    private fun requestNotificationPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun pollIntent(context: Context): PendingIntent {
        val intent = Intent(context, InsightNotificationReceiver::class.java)
            .setAction(ACTION_POLL_INSIGHTS)
        return PendingIntent.getBroadcast(
            context,
            POLL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
