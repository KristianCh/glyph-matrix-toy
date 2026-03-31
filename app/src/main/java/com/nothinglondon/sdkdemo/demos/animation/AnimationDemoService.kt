package com.nothinglondon.sdkdemo.demos.animation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import com.nothing.ketchum.GlyphMatrixManager
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AnimationDemoService : GlyphMatrixService("Animation-Demo") {
    private companion object {
        private const val SETTINGS_PREFERENCES_NAME = "SettingsPreferences"
        private const val AUDIO_VISUALIZER_ENABLED_SETTING_KEY = "AudioVisualizerEnabled"
        private const val AUDIO_COOLDOWN_TIME = 2000
    }

    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)


    // Enhanced audio detection with Visualizer API
    private val audioVisualizer: AudioVisualizer = AudioVisualizer()
    private val clockProvider: ClockProvider = ClockProvider()
    private var lastAudioTime: Long = System.currentTimeMillis()

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
        clockProvider.initialize()
        lastAudioTime = System.currentTimeMillis() - AUDIO_COOLDOWN_TIME

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        Intent(this, NotificationListener::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }

        backgroundScope.launch {
            while (isActive) {
                var currentFrameProvider: IFrameProvider = clockProvider

                val audioVisualizerEnabled = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, true)
                val audioPresent = audioVisualizer.canPlay()
                val currentTimeMillis = System.currentTimeMillis()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)
                if (showVisualizer) {
                    if (audioPresent)
                        lastAudioTime = currentTimeMillis
                    currentFrameProvider = audioVisualizer
                }

                val frameData = currentFrameProvider.getFrameData(null).build(applicationContext).render()
                uiScope.launch {
                    glyphMatrixManager.setMatrixFrame(frameData)
                }

                // wait a bit
                delay(currentFrameProvider.getFrameTime())
            }
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        backgroundScope.cancel()

        try {
            unbindService(connection)
        } catch (e: Exception) { }
    }
}