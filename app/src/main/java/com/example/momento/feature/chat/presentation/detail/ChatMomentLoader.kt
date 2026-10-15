package com.example.momento.feature.chat.presentation.detail

import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.domain.repository.MomentRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ChatMomentLoader(
    private val moments: MomentRepository,
    private val users: CurrentUserStore,
) {
    private val requests = Semaphore(4)

    suspend fun load(
        session: CurrentUserStore.Session,
        photoIds: Set<String>,
        onFinished: (String) -> Unit,
    ) = coroutineScope {
        photoIds.map { photoId ->
            launch {
                requests.withPermit {
                    val request = try {
                        users.withSession(session) {
                            async(start = CoroutineStart.UNDISPATCHED) { moments.getMoment(photoId) }
                        }
                    } catch (_: IllegalStateException) {
                        return@withPermit
                    }
                    request.await()
                    currentCoroutineContext().ensureActive()
                    onFinished(photoId)
                }
            }
        }.joinAll()
    }
}
