package com.example.momento

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.momento.app.MomentoApp
import com.example.momento.feature.chat.data.realtime.ChatRealtimeManager
import com.example.momento.ui.theme.MomentoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var chatRealtime: ChatRealtimeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomentoTheme(dynamicColor = false) {
                MomentoApp(onExit = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        chatRealtime.setForeground(true)
    }

    override fun onStop() {
        chatRealtime.setForeground(false)
        super.onStop()
    }
}
