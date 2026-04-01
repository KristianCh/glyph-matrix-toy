package com.nothinglondon.sdkdemo.demos.animation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import com.nothing.ketchum.GlyphMatrixManager
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.notificationFrame
import com.nothinglondon.sdkdemo.demos.animation.Renderers.AudioVisualizerRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.ClockRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.GameOfLiveRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.IFrameRenderer
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AnimationDemoService : GlyphMatrixService("Animation-Demo") {
    private companion object {
        private const val AUDIO_COOLDOWN_TIME = 2000
    }

    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)


    // Enhanced audio detection with Visualizer API
    private val audioVisualizerProvider: AudioVisualizerRenderer = AudioVisualizerRenderer()
    private val clockProvider: ClockRenderer = ClockRenderer()
    private val gameOfLiveProvider: GameOfLiveRenderer = GameOfLiveRenderer()
    private var lastAudioTime: Long = System.currentTimeMillis()

    private lateinit var mService: NotificationListener
    private var mNLSBound: Boolean = false
    private var activeNotifications = 0
    lateinit var sharedPreferences: SharedPreferences

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        audioVisualizerProvider.initialize(this)
        clockProvider.initialize(this)
        gameOfLiveProvider.initialize(this)

        lastAudioTime = System.currentTimeMillis() - AUDIO_COOLDOWN_TIME

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        backgroundScope.launch {
            while (isActive) {
                val currentPrimaryToy = sharedPreferences.getInt(PRIMARY_TOY_SETTING_KEY, 0)

                var currentFrameProvider: IFrameRenderer = when (currentPrimaryToy) {
                    0 -> clockProvider
                    1 -> gameOfLiveProvider
                    else -> clockProvider
                }

                val audioVisualizerEnabled = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, true)
                val audioPresent = audioVisualizerProvider.canPlay()
                val currentTimeMillis = System.currentTimeMillis()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)
                if (showVisualizer) {
                    if (audioPresent)
                        lastAudioTime = currentTimeMillis
                    currentFrameProvider = audioVisualizerProvider
                }

                val modifier: IntArray? = if (NotificationListener.notifications.value.size > 0) notificationFrame else null
                val frameData = currentFrameProvider.getFrameData(modifier).build(applicationContext).render()
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
    }
}