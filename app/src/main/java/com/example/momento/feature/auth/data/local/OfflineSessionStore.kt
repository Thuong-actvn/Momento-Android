package com.example.momento.feature.auth.data.local

import androidx.room.withTransaction
import com.example.momento.core.database.MomentoDatabase
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.domain.repository.SavedAccount
import javax.inject.Inject

class OfflineSessionStore @Inject constructor(private val database: MomentoDatabase) {
    private val sessions = database.offlineSessionDao()
    private val chat = database.chatDao()

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
                deleteChat(previous.backendUserId)
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
                deleteChat(savedUserId)
            }
            if (activeUserId != null && activeUserId != savedUserId) {
                deleteChat(activeUserId)
            }
        }
    }

    private suspend fun deleteChat(userId: String) {
        chat.deleteConversations(userId)
        chat.deleteSyncState(userId)
    }
}
