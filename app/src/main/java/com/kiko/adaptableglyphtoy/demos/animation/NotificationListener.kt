package com.kiko.adaptableglyphtoy.demos.animation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.getMappedText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NotificationItem(
    val appName: String,
    val title: String?,
    val text: String?
)

class NotificationListener : NotificationListenerService() {
    companion object {
        private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
        val notifications: StateFlow<List<NotificationItem>> = _notifications

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

    override fun onListenerConnected() {
        loadActiveNotifications()
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