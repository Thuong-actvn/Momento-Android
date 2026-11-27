package com.example.momento.feature.auth.data.local

import androidx.room.withTransaction
import com.example.momento.core.database.MomentoDatabase
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.domain.repository.SavedAccount
import com.example.momento.feature.moment.data.local.JournalPreviewStore
import javax.inject.Inject

class OfflineSessionStore @Inject constructor(
    private val database: MomentoDatabase,
    private val previews: JournalPreviewStore,
) {
    private val sessions = database.offlineSessionDao()
    private val chat = database.chatDao()
    private val journal = database.momentCacheDao()

    suspend fun read(): SavedAccount? {
        val session = sessions.read() ?: return null
        return SavedAccount(session.firebaseUid, session.backendUserId, session.displayName)
    }

    suspend fun save(firebaseUid: String, user: CurrentUser) {
        check(user.isProfileCompleted)
        database.withTransaction {
            val previous = sessions.read()
            if (previous != null &&
                (previous.firebaseUid != firebaseUid || previous.backendUserId != user.id)
            ) {
                deleteAccountCache(previous.backendUserId)
            }
            sessions.save(
                OfflineSessionEntity(
                    firebaseUid = firebaseUid,
                    backendUserId = user.id,
                    displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.username,
                )
            )
        }
    }

    suspend fun forget(activeUserId: String? = null) {
        database.withTransaction {
            val savedUserId = sessions.read()?.backendUserId
            sessions.delete()
            if (savedUserId != null) {
                deleteAccountCache(savedUserId)
            }
            if (activeUserId != null && activeUserId != savedUserId) {
                deleteAccountCache(activeUserId)
            }
        }
    }

    private suspend fun deleteAccountCache(userId: String) {
        chat.deleteConversations(userId)
        chat.deleteSyncState(userId)
        journal.deleteAccountPhotos(userId)
        journal.deleteFriends(userId)
        journal.deleteSyncState(userId)
        previews.deleteAccount(userId)
    }
}
