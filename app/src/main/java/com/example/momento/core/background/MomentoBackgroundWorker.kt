package com.example.momento.core.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.messaging.FcmTokenManager
import com.example.momento.core.network.ApiException
import com.example.momento.feature.widget.data.local.WidgetSessionChangedException
import com.example.momento.feature.widget.domain.repository.WidgetRepository
import com.example.momento.feature.widget.presentation.home.WidgetRenderer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.SerializationException

@HiltWorker
class MomentoBackgroundWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val tokenManager: FcmTokenManager,
    private val widgetRepository: WidgetRepository,
    private val widgetRenderer: WidgetRenderer,
    private val workScheduler: BackgroundWorkScheduler,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(BackgroundWorkScheduler.ACTION_KEY)
        val result = when (action) {
            BackgroundWorkScheduler.REGISTER_TOKEN -> {
                val tokenResult = tokenManager.register()
                // Failed account verification can clear the saved session.
                if (tokenResult.isFailure) {
                    workScheduler.updateWidgets()
                }
                tokenResult
            }
            BackgroundWorkScheduler.REFRESH_WIDGET -> {
                val refreshResult = widgetRepository.refresh()
                // Also render after a failure: keep cached photos or show the cleared session.
                workScheduler.updateWidgets()
                refreshResult
            }
            BackgroundWorkScheduler.UPDATE_WIDGETS -> suspendRunCatching {
                widgetRenderer.updateAll()
            }
            BackgroundWorkScheduler.NEXT_WIDGET_PHOTO -> suspendRunCatching {
                // A retry should render the saved selection, not advance a second time.
                if (runAttemptCount == 0) {
                    widgetRenderer.nextPhoto()
                } else {
                    widgetRenderer.updateAll()
                }
            }
            else -> return Result.failure()
        }

        val error = result.exceptionOrNull() ?: return Result.success()
        if (error is WidgetSessionChangedException) {
            return Result.success()
        }

        Log.w("MomentoBackground", "Background task failed: $action", error)
        return if (shouldRetryBackgroundWork(error, runAttemptCount)) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}

fun shouldRetryBackgroundWork(error: Throwable, attempt: Int): Boolean {
    if (attempt >= 3) {
        return false
    }

    return when (error) {
        is WidgetSessionChangedException,
        is SerializationException,
        is IllegalArgumentException -> false

        is ApiException -> {
            val statusCode = error.statusCode ?: return false
            statusCode == 408 || statusCode == 429 || statusCode >= 500
        }

        else -> true
    }
}
