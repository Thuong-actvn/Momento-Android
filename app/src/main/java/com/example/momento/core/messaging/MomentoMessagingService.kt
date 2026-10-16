package com.example.momento.core.messaging

import com.example.momento.core.background.BackgroundWorkScheduler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MomentoMessagingService : FirebaseMessagingService() {
    @Inject lateinit var work: BackgroundWorkScheduler

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // The worker gets the latest SDK token and binds it to the active account.
        work.registerFcmToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "NEW_PHOTO") {
            work.refreshWidgetData()
        }
    }

    override fun onDeletedMessages() {
        work.refreshWidgetData()
    }
}
