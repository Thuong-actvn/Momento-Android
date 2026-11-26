package com.example.momento

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.momento.app.MomentoApp
import com.example.momento.core.realtime.RealtimeManager
import com.example.momento.ui.theme.MomentoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var widgetPhotoId by mutableStateOf<String?>(null)

    @Inject
    lateinit var realtime: RealtimeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var startupContentReady = false
        splashScreen.setKeepOnScreenCondition { !startupContentReady }

        widgetPhotoId = if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_WIDGET_PHOTO_ID)
        } else {
            savedInstanceState.getString(EXTRA_WIDGET_PHOTO_ID)
        }
        enableEdgeToEdge()

        setContent {
            MomentoTheme(dynamicColor = false) {
                MomentoApp(
                    onExit = ::finish,
                    onStartupContentReady = { startupContentReady = true },
                    widgetPhotoId = widgetPhotoId,
                    onWidgetPhotoOpened = { widgetPhotoId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetPhotoId = intent.getStringExtra(EXTRA_WIDGET_PHOTO_ID)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_WIDGET_PHOTO_ID, widgetPhotoId)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        realtime.setForeground(true)
    }

    override fun onStop() {
        realtime.setForeground(false)
        super.onStop()
    }

    companion object {
        const val EXTRA_WIDGET_PHOTO_ID = "widget_photo_id"
    }
}
