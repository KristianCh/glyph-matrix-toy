package com.nothinglondon.sdkdemo.demos.animation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.Math.clamp
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AnimationDemoService : GlyphMatrixService("Animation-Demo") {

    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)


    // Enhanced audio detection with Visualizer API
    private var audioVisualizer: AudioVisualizer = AudioVisualizer()
    private var lastUpdateTime: Long = System.currentTimeMillis()

    private var debugText = "NO"

    private lateinit var mService: NotificationListener
    private var mNLSBound: Boolean = false
    private var activeNotifications = 0
    lateinit var sharedPreferences: SharedPreferences
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to NotificationListener service, cast the IBinder and get NotificationListener instance.
            val binder = service as NotificationListener.NotificationListenerBinder
            mService = binder.getService()
            mNLSBound = true

            //mService.
            mService.setListener(object : OnNotificationListener {
                override fun onNotificationsChanged(
                    remaining: Int
                )
                {
                    activeNotifications += remaining + 1
                }

                override fun test(int: Int) {
                    activeNotifications += int
                }
            })
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService.unsetListener()
            mNLSBound = false
        }
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        audioVisualizer.initialize()

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        Intent(this, NotificationListener::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        backgroundScope.launch {
            while (isActive) {

                val currentTime = LocalDateTime.now()
                val hourText = formatTime(currentTime.hour)
                val minuteText = formatTime(currentTime.minute)
                val secondsArray = getSecondsArray(currentTime.second + currentTime.nano / 1000000000.0)

                val textObject = GlyphMatrixObject.Builder().setText(hourText)
                    .setPosition(getCenteredTextX(hourText), TOP_LINE)
                    .build()

                val textObject2 = GlyphMatrixObject.Builder().setText(minuteText)
                    .setPosition( getCenteredTextX(minuteText), BOTTOM_LINE)
                    .build()

                val frameToDraw = GlyphMatrixFrame.Builder()
                    .addTop(textObject)
                    .addMid(textObject2)
                    .addLow(secondsArray)
                    .build(applicationContext)

                var frameData = frameToDraw.render()

                val audioVisualizerEnabled = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, true)
                val audioPresent = audioVisualizer.canPlay()
                val currentTimeMillis = System.currentTimeMillis()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastUpdateTime < 2000)
                if (showVisualizer) {
                    if (audioPresent)
                        lastUpdateTime = currentTimeMillis
                    frameData = GlyphMatrixFrame.Builder()
                        .addTop(audioVisualizer.getFrameData(null))
                        .build(applicationContext).render()
                }

                uiScope.launch {
                    glyphMatrixManager.setMatrixFrame(frameData)
                }

                // wait a bit
                //delay((60 - currentTime.second.toLong()) * 1000)
                delay((if (showVisualizer) audioVisualizer.getFrameTime() else CLOCK_ANIMATION_SPEED).toLong())
            }
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        backgroundScope.cancel()

        try {
            unbindService(connection)
        } catch (e: Exception) {
            Log.w("Visualizer", "Error cleaning up visualizer: ${e.message}")
        }
    }

    private fun formatTime(time: Int): String {
        if (time < 10)
            return "0$time"
        return time.toString()
    }

    private fun getTextLength(string: String): Int {
        var l = 0
        var wasLastSpace = false
        for (i in 0..<string.length) {
            if (string[i] != ' ') {
                l += CHARACTER_WIDTH
                wasLastSpace = false
            }
            else {
                if (!wasLastSpace)
                    l++
                wasLastSpace = true
            }
            if (i < string.length - 1)
                l += CHARACTER_SEPARATOR_WIDTH
        }
        return l
    }

    private fun getCenteredTextX(text: String): Int {
        return MID_POINT - getTextLength(text) / 2 + 1
    }

    private fun getSecondsArray(seconds: Double): IntArray {
        val grid = Array(HEIGHT * WIDTH) { 0 }
        val angleRange = 4.0
        val targetAngle = seconds / 60.0 * 360

        val targetX = sin(toRadians(targetAngle-90)) * 6
        val targetY = cos(-toRadians(targetAngle-90)) * 6
        for (i in 0..<HEIGHT) {
            for (j in 0..<WIDTH) {
                val x = j - MID_POINT
                val y = i - MID_POINT
                val distance = sqrt((targetX-x).pow(2) + (targetY-y).pow(2))
                grid[j * WIDTH + i] = (clamp(angleRange-distance, 0.0, angleRange).pow(3) / angleRange * 255).toInt()
            }
        }
        return grid.toIntArray()
    }

    private companion object {
        private val LOG_TAG = GlyphMatrixService::class.java.simpleName
        private const val WIDTH = 13
        private const val HEIGHT = 13
        private const val HALF_HEIGHT = HEIGHT.toDouble() / 2
        private const val MID_POINT = HEIGHT / 2

        private const val CHARACTER_WIDTH = 4
        private const val TOP_LINE = 0
        private const val BOTTOM_LINE = 6
        private const val CHARACTER_SEPARATOR_WIDTH = 2
        private const val CLOCK_ANIMATION_SPEED = 200
        private const val AUDIO_VISUALIZER_ANIMATION_SPEED = 33
        private const val AUDIO_BANDS = 13
        private const val SETTINGS_PREFERENCES_NAME = "SettingsPreferences"
        private const val AUDIO_VISUALIZER_ENABLED_SETTING_KEY = "AudioVisualizerEnabled"
    }
}