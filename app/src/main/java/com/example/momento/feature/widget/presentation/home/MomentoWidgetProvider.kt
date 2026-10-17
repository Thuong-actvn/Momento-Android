package com.example.momento.feature.widget.presentation.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.example.momento.core.background.BackgroundWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MomentoWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var workScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var widgetRenderer: WidgetRenderer

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        widgetRenderer.showCached()
        workScheduler.scheduleWidgetRefresh()
        // Show cached data first, including when the device is offline.
        workScheduler.updateWidgets()
        workScheduler.refreshWidgetData()
    }

    override fun onDisabled(context: Context) {
        workScheduler.cancelWidgetRefresh()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_NEXT) {
            workScheduler.nextWidgetPhoto()
        }
    }

    companion object {
        const val ACTION_NEXT = "com.example.momento.widget.NEXT"
    }
}
