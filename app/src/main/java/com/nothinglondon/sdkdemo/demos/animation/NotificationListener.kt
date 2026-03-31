package com.nothinglondon.sdkdemo.demos.animation

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener: NotificationListenerService() {

    private val binder = NotificationListenerBinder()
    var notificationListener: OnNotificationListener? = null

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        updateListener()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateListener()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        notificationListener?.test(3);
    }

    public fun setListener(listener: OnNotificationListener) {
        notificationListener = listener
        updateListener()
    }

    public fun unsetListener() {
        notificationListener = null
    }

    private fun updateListener() {
        if (notificationListener == null) return
        notificationListener?.onNotificationsChanged(super.activeNotifications.size)
    }

    inner class NotificationListenerBinder : Binder() {
        fun getService(): NotificationListener = this@NotificationListener
    }
}

interface OnNotificationListener {
    fun onNotificationsChanged(remaining: Int)
    fun test(int: Int)
}