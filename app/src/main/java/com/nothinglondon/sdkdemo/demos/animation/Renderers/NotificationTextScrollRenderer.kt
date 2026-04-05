package com.nothinglondon.sdkdemo.demos.animation.Renderers

import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.MID_LINE
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.WIDTH
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.getMappedText
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.getTextLength
import com.nothinglondon.sdkdemo.demos.animation.NotificationItem
import com.nothinglondon.sdkdemo.demos.animation.NotificationListener

class NotificationTextScrollRenderer : IFrameRenderer {
    companion object {
        private const val BASE_POSITION = WIDTH + 4
    }

    private var onFinishedCallback: (() -> Unit)? = null
    private var currentDisplayText: String? = null
    private var textPosition = BASE_POSITION
    private var textLength = 0

    override fun initialize(context: Context) {}

    override fun dispose() {
        clear()
    }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
        val frameToDraw = GlyphMatrixFrame.Builder()
        if (currentDisplayText != null) {

            Log.i("NotifTextScrollRenderer", "Pos: $textPosition")
            val textObject = GlyphMatrixObject.Builder().setText(currentDisplayText)
                .setPosition(textPosition, MID_LINE)
                .setBrightness(255)
                .build()

            frameToDraw.addTop(textObject)
        }
        if (textPosition > -textLength) {
            textPosition --
        }
        else {
            Log.i("NotifTextScrollRenderer", "Finished scroll")
            onFinishedCallback?.invoke()
            clear()
        }

        return frameToDraw
    }

    override fun getFrameTime(): Long {
        return 50
    }

    override fun canPlay(): Boolean {
        return currentDisplayText != null
    }

    fun TryStartScroll(onFinished: () -> Unit, includeBody: Boolean): Boolean {
        Log.i("NotifTextScrollRenderer", "Try start scroll")
        if (NotificationListener.notifications.value.isEmpty()) {
            currentDisplayText = null

            Log.i("NotifTextScrollRenderer", "Failed")
            return false
        }
        currentDisplayText = getMappedText(createTextFromNotification(NotificationListener.notifications.value.get(0), includeBody))
        currentDisplayText?.length?.let {
            if (it > 100)
                currentDisplayText = currentDisplayText?.substring(0, 100)
        }
        textLength = getTextLength(currentDisplayText ?: "")
        onFinishedCallback = onFinished
        textPosition = BASE_POSITION

        Log.i("NotifTextScrollRenderer", "L: $textLength T: $currentDisplayText")

        return true
    }

    fun createTextFromNotification(notification: NotificationItem, includeBody: Boolean): String {
        val name = notification.title ?: "???"
        if (!includeBody) return name
        val text = notification.text ?: "???"
        return "$name: $text"
    }

    fun clear() {
        currentDisplayText = null
        onFinishedCallback = null
    }
}