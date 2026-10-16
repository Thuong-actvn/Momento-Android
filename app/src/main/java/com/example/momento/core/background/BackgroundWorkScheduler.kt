package com.example.momento.core.background

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun registerFcmToken() {
        enqueue(REGISTER_TOKEN)
    }

    fun refreshWidgetData() {
        enqueue(REFRESH_WIDGET)
    }

    fun cancelSessionWork() {
        workManager.cancelAllWorkByTag(SESSION_WORK_TAG)
    }

    private fun enqueue(action: String) {
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

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

    companion object {
        const val ACTION_KEY = "action"
        const val REGISTER_TOKEN = "momento_register_fcm_token"
        const val REFRESH_WIDGET = "momento_refresh_widget_data"
        private const val SESSION_WORK_TAG = "momento_session_background_work"
    }
}
