package com.kiko.adaptableglyphtoy.animation

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getMappedText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

data class NotificationItem(
    val appName: String,
    val title: String?,
    val text: String?
)

class NotificationListener : NotificationListenerService() {
    companion object {
        private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
        val notifications: StateFlow<List<NotificationItem>> = _notifications

        private val _songInfoFlow: MutableStateFlow<String> = MutableStateFlow("")
        val songInfoFlow: StateFlow<String> = _songInfoFlow
        private val _albumArtFlow: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
        val albumArtFlow: StateFlow<Bitmap?> = _albumArtFlow

        private val _mediaPlaybackStateFlow: MutableStateFlow<Int> = MutableStateFlow(0)
        val mediaPlaybackStateFlow: StateFlow<Int> = _mediaPlaybackStateFlow

        fun mostRecentNotificationString(includeBody: Boolean): String? {
            if (notifications.value.isEmpty()) {
                return null
            }
            var outString = getMappedText(createTextFromNotification(notifications.value[0], includeBody))
            outString.length.let {
                if (it > 100)
                    outString = outString.substring(0, 100)
            }
            return outString
        }

        private fun createTextFromNotification(notification: NotificationItem, includeBody: Boolean): String {
            val name = notification.title ?: "???"
            if (!includeBody) return name
            val text = notification.text ?: "???"
            return "$name: $text"
        }
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private val mediaControllerCallback: MediaControllerCallback = MediaControllerCallback()
    private var mediaControllers: List<MediaController> = emptyList()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            handleControllers(controllers)
            Log.d("MediaSession", "SessionsChanged")
        }

    private fun handleControllers(controllers: List<MediaController>?) {
        Log.i("MediaSession", "Controllers: ${mediaControllers.size}")
        controllers?.forEach { controller ->
            controller.unregisterCallback(mediaControllerCallback)
            controller.registerCallback(mediaControllerCallback)
        }
        mediaControllers = controllers ?: emptyList()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        loadActiveNotifications()

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        val componentName = ComponentName(this, javaClass)

        mediaSessionManager.addOnActiveSessionsChangedListener(
            sessionsListener,
            componentName
        )

        // Initial fetch
        handleControllers(mediaSessionManager.getActiveSessions(componentName))
        mediaControllerCallback.setCallback(::onMediaChanged)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
    }


    private fun onMediaChanged() {
        _songInfoFlow.value = mediaControllerCallback.songInfo
        _albumArtFlow.value = mediaControllerCallback.albumArt
        _mediaPlaybackStateFlow.value = mediaControllerCallback.playbackState
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = extractNotification(sbn) ?: return

        val updated = _notifications.value.toMutableList()
        updated.add(0, item)
        _notifications.value = updated
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        loadActiveNotifications()
    }

    private fun loadActiveNotifications() {
        val list: MutableList<NotificationItem> = mutableListOf()
        activeNotifications.forEach {
            val item = extractNotification(it)
            if (item != null) {
                list.add(item)
            }
        }
        _notifications.value = list
    }

    private fun extractNotification(sbn: StatusBarNotification): NotificationItem? {
        val extras = sbn.notification.extras
        val bubble = sbn.notification.bubbleMetadata?.isNotificationSuppressed ?: false

        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()
        val identical = notifications.value.any { n -> n.title == title && n.text == text }

        // Filter junk / ghost notifications
        if (identical || title.isNullOrBlank() && text.isNullOrBlank() || bubble) return null

        return NotificationItem(
            appName = sbn.packageName,
            title = title,
            text = text
        )
    }
}