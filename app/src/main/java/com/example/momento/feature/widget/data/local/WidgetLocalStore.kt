package com.example.momento.feature.widget.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.momento.feature.widget.domain.model.WidgetSnapshot
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class WidgetAccount(
    val firebaseUid: String,
    val backendUserId: String,
    // A new generation blocks writes from jobs started before sign-out.
    val generation: String,
)

@Serializable
data class StoredWidgetData(
    val account: WidgetAccount? = null,
    val snapshot: WidgetSnapshot? = null,
    val selectedFriendId: String? = null,
)

class WidgetSessionChangedException : IllegalStateException("Widget account is no longer active")

@Singleton
class WidgetLocalStore @Inject constructor(
    private val preferences: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("widget_data")
    val data = preferences.data.map(::decode)

    suspend fun read(): StoredWidgetData = data.first()

    suspend fun activate(
        firebaseUid: String,
        backendUserId: String,
        checkSession: () -> Unit = {},
    ): WidgetAccount {
        require(firebaseUid.isNotBlank() && backendUserId.isNotBlank())
        val updated = preferences.edit { values ->
            checkSession()
            val state = decode(values)
            val previousAccount = state.account
            if (previousAccount != null &&
                previousAccount.firebaseUid == firebaseUid &&
                previousAccount.backendUserId == backendUserId
            ) {
                return@edit
            }

            val newState = StoredWidgetData(
                account = WidgetAccount(
                    firebaseUid = firebaseUid,
                    backendUserId = backendUserId,
                    generation = UUID.randomUUID().toString(),
                ),
            )
            values[key] = json.encodeToString(newState)
        }
        return checkNotNull(decode(updated).account)
    }

    suspend fun clearSession(
        expected: WidgetAccount? = null,
        checkSession: () -> Unit = {},
    ) {
        preferences.edit { values ->
            checkSession()
            val state = decode(values)
            if (expected != null && state.account != expected) {
                return@edit
            }

            values.remove(key)
        }
    }

    suspend fun saveSnapshot(
        account: WidgetAccount,
        snapshot: WidgetSnapshot,
        checkSession: () -> Unit = {},
    ) {
        preferences.edit { values ->
            // Validate inside the atomic edit so a sign-out cannot be followed by an old write.
            checkSession()
            val state = decode(values)
            if (state.account != account) {
                throw WidgetSessionChangedException()
            }

            values[key] = json.encodeToString(state.copy(snapshot = snapshot))
        }
    }

    suspend fun saveSelectedFriend(
        account: WidgetAccount,
        friendId: String?,
        checkSession: () -> Unit,
    ) {
        preferences.edit { values ->
            checkSession()
            val state = decode(values)
            if (state.account != account) {
                throw WidgetSessionChangedException()
            }
            val isCurrentFriend = state.snapshot?.friends?.any { friend ->
                friend.id == friendId
            } == true
            require(friendId == null || isCurrentFriend) {
                "Select a current friend"
            }

            val updatedState = state.copy(selectedFriendId = friendId)
            values[key] = json.encodeToString(updatedState)
        }
    }

    private fun decode(values: Preferences): StoredWidgetData {
        val storedJson = values[key] ?: return StoredWidgetData()
        return try {
            json.decodeFromString<StoredWidgetData>(storedJson)
        } catch (_: SerializationException) {
            StoredWidgetData()
        } catch (_: IllegalArgumentException) {
            StoredWidgetData()
        }
    }
}
