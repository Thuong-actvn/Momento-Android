package com.example.momento.feature.widget.presentation.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.momento.MainActivity
import com.example.momento.R
import com.example.momento.feature.widget.data.WidgetSessionManager
import com.example.momento.feature.widget.data.local.WidgetLocalStore
import com.example.momento.feature.widget.presentation.WidgetUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localStore: WidgetLocalStore,
    private val sessionManager: WidgetSessionManager,
    private val imageLoader: WidgetImageLoader,
) {
    private val widgetManager = AppWidgetManager.getInstance(context)
    private val provider = ComponentName(context, MomentoWidgetProvider::class.java)
    private val renderLock = Any()
    private var clearVersion = 0
    private var lastRenderedViews: RemoteViews? = null

    fun showCached() {
        synchronized(renderLock) {
            val views = lastRenderedViews ?: return
            val widgetIds = widgetManager.getAppWidgetIds(provider)
            if (widgetIds.isNotEmpty()) {
                widgetManager.updateAppWidget(widgetIds, views)
            }
        }
    }

    suspend fun updateAll() {
        val widgetIds = widgetManager.getAppWidgetIds(provider)
        if (widgetIds.isEmpty()) {
            return
        }

        val versionBeforeLoading = synchronized(renderLock) { clearVersion }
        val storedData = localStore.read()
        val account = storedData.account
        if (account == null || !sessionManager.matchesIdentity(account)) {
            clear()
            return
        }

        val state = WidgetUiState(
            snapshot = storedData.snapshot,
            selectedFriendId = storedData.selectedFriendId,
            currentPhotoId = storedData.currentPhotoId,
        )
        val photoSizeDp = widgetIds.maxOf { widgetId ->
            widgetManager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_PHOTO_SIZE_DP)
        }
        val photoUri = imageLoader.load(
            url = state.photo?.imageUrl,
            filePrefix = "photo",
            sizeDp = photoSizeDp,
        )
        val avatarUri = imageLoader.load(
            url = state.sender?.avatarUrl,
            filePrefix = "avatar",
            sizeDp = AVATAR_SIZE_DP,
        )

        // A slow image request must not restore an old account or photo after a change.
        if (localStore.read() != storedData || !sessionManager.matchesIdentity(account)) {
            return
        }

        val views = createViews(state, photoUri, avatarUri)
        synchronized(renderLock) {
            if (versionBeforeLoading != clearVersion) {
                return
            }
            imageLoader.grantLauncherAccess(photoUri, avatarUri)
            lastRenderedViews = views
            widgetManager.updateAppWidget(widgetIds, views)
            imageLoader.removeOldImages(photoUri, avatarUri)
        }
    }

    suspend fun nextPhoto() {
        val account = sessionManager.current()
        if (account == null) {
            clear()
            return
        }
        localStore.showNextPhoto(account) {
            sessionManager.checkIdentity(account)
        }
        updateAll()
    }

    fun clear() {
        val widgetIds = widgetManager.getAppWidgetIds(provider)
        val views = createViews(
            state = WidgetUiState(),
            photoUri = null,
            avatarUri = null,
        )
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_sign_in))
        synchronized(renderLock) {
            // Invalidate image requests still in progress before clearing the launcher.
            clearVersion += 1
            lastRenderedViews = views
            if (widgetIds.isNotEmpty()) {
                widgetManager.updateAppWidget(widgetIds, views)
            }
            imageLoader.clear()
        }
    }

    private fun createViews(
        state: WidgetUiState,
        photoUri: Uri?,
        avatarUri: Uri?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.momento_widget)
        val photo = state.photo
        val sender = state.sender
        val displayName = sender?.displayName
        val senderName = if (!displayName.isNullOrBlank()) {
            displayName
        } else {
            sender?.username.orEmpty()
        }
        val contentColor = if (photoUri != null) {
            Color.WHITE
        } else {
            context.getColor(R.color.widget_primary)
        }

        // Photo and empty state.
        if (photoUri != null) {
            views.setImageViewUri(R.id.widget_photo, photoUri)
            views.setViewVisibility(R.id.widget_shade, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
        } else {
            // Hosts can reuse the previous layout, so clear its image explicitly.
            views.setImageViewResource(R.id.widget_photo, 0)
            views.setViewVisibility(R.id.widget_shade, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            val emptyMessageRes = when {
                photo != null -> R.string.widget_photo_error
                state.hasUnavailableFriend -> R.string.widget_friend_unavailable
                else -> R.string.widget_photo_empty
            }
            views.setTextViewText(R.id.widget_empty, context.getString(emptyMessageRes))
        }

        // Avatar and sender name.
        val footerVisibility = if (photo != null) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.widget_footer, footerVisibility)
        views.setTextViewText(R.id.widget_sender, senderName)
        views.setTextColor(R.id.widget_sender, contentColor)
        if (avatarUri != null) {
            views.setImageViewUri(R.id.widget_avatar, avatarUri)
        } else {
            views.setImageViewResource(R.id.widget_avatar, R.drawable.ic_avatar_placeholder)
        }

        // Next photo button.
        val showNextButton = photo != null && state.selectedFriendId == null
        val canGoToNextPhoto = state.photos.size > 1
        val nextButtonVisibility = if (showNextButton) View.VISIBLE else View.GONE
        val nextIconAlpha = if (canGoToNextPhoto) {
            ENABLED_ICON_ALPHA
        } else {
            DISABLED_ICON_ALPHA
        }
        views.setViewVisibility(R.id.widget_next_button, nextButtonVisibility)
        views.setBoolean(R.id.widget_next_button, "setEnabled", canGoToNextPhoto)
        views.setInt(R.id.widget_next_button, "setImageAlpha", nextIconAlpha)
        views.setInt(R.id.widget_next_button, "setColorFilter", contentColor)
        views.setOnClickPendingIntent(R.id.widget_next_button, nextPhotoIntent())

        // Tapping the photo opens this moment after the app restores its session.
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(photo?.id))
        views.setContentDescription(
            R.id.widget_root,
            context.getString(R.string.widget_photo_description, senderName),
        )
        return views
    }

    private fun openAppIntent(photoId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        if (photoId != null) {
            // Different photos need different PendingIntents, including across widget updates.
            intent.data = Uri.Builder()
                .scheme("momento")
                .authority("widget")
                .appendPath("photos")
                .appendPath(photoId)
                .build()
            intent.putExtra(MainActivity.EXTRA_WIDGET_PHOTO_ID, photoId)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextPhotoIntent(): PendingIntent {
        val intent = Intent(context, MomentoWidgetProvider::class.java)
            .setAction(MomentoWidgetProvider.ACTION_NEXT)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val DEFAULT_PHOTO_SIZE_DP = 180
        const val AVATAR_SIZE_DP = 20
        const val ENABLED_ICON_ALPHA = 255
        const val DISABLED_ICON_ALPHA = 102 // 40% opacity.
    }
}
