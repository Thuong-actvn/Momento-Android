package com.example.momento.feature.moment.data.local

import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.domain.model.Moment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Moment đã tải trong phiên, dùng làm ngữ cảnh cho tin PHOTO_COMMENT. */
@Singleton
class MomentMemoryStore @Inject constructor(
    private val users: CurrentUserStore,
    scope: CoroutineScope,
) {
    private data class Snapshot(
        val generation: Long,
        val moments: Map<String, Moment> = emptyMap(),
        val removedIds: Set<String> = emptySet(),
    )

    private val snapshot = MutableStateFlow(Snapshot(users.sessionVersion.value))

    val moments = combine(users.sessionVersion, snapshot) { version, current ->
        if (current.generation == version) current.moments else emptyMap()
    }.distinctUntilChanged()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            users.sessionVersion.collect {
                synchronized(users) {
                    val version = users.sessionVersion.value
                    if (snapshot.value.generation != version) {
                        snapshot.value = Snapshot(version)
                    }
                }
            }
        }
    }

    fun merge(session: CurrentUserStore.Session, incoming: List<Moment>) {
        users.withSession(session) {
            val current = snapshot.value.takeIf { it.generation == session.generation }
                ?: Snapshot(session.generation)
            // Trang tải trước lúc xóa không được đưa ảnh đã xóa trở lại cache.
            val visible = incoming.filterNot { it.id in current.removedIds }
            snapshot.value = current.copy(moments = current.moments + visible.associateBy { it.id })
        }
    }

    fun remove(session: CurrentUserStore.Session, momentId: String) {
        users.withSession(session) {
            val current = snapshot.value
            if (current.generation == session.generation) {
                snapshot.value = current.copy(
                    moments = current.moments - momentId,
                    removedIds = current.removedIds + momentId,
                )
            }
        }
    }

    fun evict(session: CurrentUserStore.Session, momentId: String) {
        users.withSession(session) {
            val current = snapshot.value
            if (current.generation == session.generation) {
                snapshot.value = current.copy(moments = current.moments - momentId)
            }
        }
    }
}
