package com.nothinglondon.sdkdemo.demos.animation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()

        // Filter junk / ghost notifications
        if (title.isNullOrBlank() && text.isNullOrBlank() || !sbn.isGroup) return null

        return NotificationItem(
            appName = sbn.packageName,
            title = title,
            text = text
        )
    }
}