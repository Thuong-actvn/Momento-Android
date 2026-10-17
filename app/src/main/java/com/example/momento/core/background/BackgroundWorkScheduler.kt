package com.example.momento.core.background

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun registerFcmToken() {
        enqueueNetworkWork(REGISTER_TOKEN)
    }

    fun refreshWidgetData() {
        enqueueNetworkWork(REFRESH_WIDGET)
    }

    fun updateWidgets() {
        enqueueWidgetDisplay(UPDATE_WIDGETS)
    }

    fun nextWidgetPhoto() {
        enqueueWidgetDisplay(NEXT_WIDGET_PHOTO)
    }

    fun scheduleWidgetRefresh() {
        val request = PeriodicWorkRequestBuilder<MomentoBackgroundWorker>(30, TimeUnit.MINUTES)
            .setInputData(workDataOf(ACTION_KEY to REFRESH_WIDGET))
            .setConstraints(networkConstraints)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()

        // Reuse one periodic refresh while widgets exist. WorkManager then keeps its
        // receiver enabled instead of resetting the launcher after each short job.
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WIDGET_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelWidgetRefresh() {
        workManager.cancelUniqueWork(PERIODIC_WIDGET_REFRESH)
    }

    fun cancelSessionWork() {
        workManager.cancelAllWorkByTag(SESSION_WORK_TAG)
    }

    private fun enqueueNetworkWork(action: String) {
        val requestBuilder = OneTimeWorkRequestBuilder<MomentoBackgroundWorker>()
            .setInputData(workDataOf(ACTION_KEY to action))
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(SESSION_WORK_TAG)

        // The BE can publish NEW_PHOTO before its photo transaction commits.
        if (action == REFRESH_WIDGET) {
            requestBuilder.setInitialDelay(1, TimeUnit.SECONDS)
        }

        val request = requestBuilder.build()
        // Append preserves a signal arriving while the preceding request is finishing.
        workManager.enqueueUniqueWork(action, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun enqueueWidgetDisplay(action: String) {
        // Display work also runs offline and after sign-out, independently of session jobs.
        val request = OneTimeWorkRequestBuilder<MomentoBackgroundWorker>()
            .setInputData(workDataOf(ACTION_KEY to action))
            .build()
        workManager.enqueueUniqueWork(
            WIDGET_DISPLAY_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        const val ACTION_KEY = "action"
        const val REGISTER_TOKEN = "momento_register_fcm_token"
        const val REFRESH_WIDGET = "momento_refresh_widget_data"
        const val UPDATE_WIDGETS = "momento_update_widgets"
        const val NEXT_WIDGET_PHOTO = "momento_next_widget_photo"
        private const val PERIODIC_WIDGET_REFRESH = "momento_periodic_widget_refresh"
        private const val WIDGET_DISPLAY_WORK = "momento_widget_display"
        private const val SESSION_WORK_TAG = "momento_session_background_work"
    }
}
