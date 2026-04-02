package com.nothinglondon.sdkdemo.demos.animation

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_DELAY_UI
import androidx.core.content.ContextCompat
import com.nothing.ketchum.GlyphMatrixManager
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.getNotificationFrame
import com.nothinglondon.sdkdemo.demos.animation.Renderers.AudioVisualizerRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.ClockRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.GameOfLiveRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.IFrameRenderer
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ROTATION_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SHOW_NOTIFICATION_RING_SETTING_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AnimationDemoService : GlyphMatrixService("Animation-Demo") {
    companion object {
        private const val AUDIO_COOLDOWN_TIME = 2000

        private val _currentRotation = MutableStateFlow(Orientation.PORTRAIT_UP)
        val currentRotation: StateFlow<Orientation> = _currentRotation
        private val _currentAngle = MutableStateFlow(0)
        val currentAngle: StateFlow<Int> = _currentAngle
        var audioVisualizerEnabled = true
        var audioVisualizerRotationType = AudioVisualizerRotationType.Axis
    }

    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    // Enhanced audio detection with Visualizer API
    private val audioVisualizerProvider: AudioVisualizerRenderer = AudioVisualizerRenderer()
    private val clockProvider: ClockRenderer = ClockRenderer()
    private val gameOfLiveProvider: GameOfLiveRenderer = GameOfLiveRenderer()
    private var lastAudioTime: Long = System.currentTimeMillis()

    private var orientationListenerUI: OrientationListener? = null
    private var orientationListenerGame: OrientationListener? = null
    lateinit var sharedPreferences: SharedPreferences

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        audioVisualizerProvider.initialize(this)
        clockProvider.initialize(this)
        gameOfLiveProvider.initialize(this)

        orientationListenerUI = orientationListenerUI ?: OrientationListener(this, SENSOR_DELAY_UI, { rotation ->
            _currentRotation.value = rotation
        }, { angle ->
            _currentAngle.value = angle

        })
        orientationListenerUI?.enable()

        orientationListenerGame = orientationListenerGame ?: OrientationListener(this, SENSOR_DELAY_NORMAL, { rotation ->
            _currentRotation.value = rotation
        }, { angle ->
            _currentAngle.value = angle

        })

        lastAudioTime = System.currentTimeMillis() - AUDIO_COOLDOWN_TIME

        sharedPreferences = getSharedPreferences(SETTINGS_PREFERENCES_NAME, MODE_PRIVATE)

        backgroundScope.launch {
            while (isActive) {
                val currentTimeMillis = System.currentTimeMillis()
                val currentPrimaryToy = sharedPreferences.getInt(PRIMARY_TOY_SETTING_KEY, 0)

                var currentFrameProvider: IFrameRenderer = when (currentPrimaryToy) {
                    0 -> clockProvider
                    1 -> gameOfLiveProvider
                    else -> clockProvider
                }

                var lastAudioVisualizerEnabled = audioVisualizerEnabled
                audioVisualizerEnabled = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, false)
                if (lastAudioVisualizerEnabled != audioVisualizerEnabled) {
                    audioVisualizerProvider.setEnabled(audioVisualizerEnabled)
                }

                val lastAudioVisualizerRotationType = audioVisualizerRotationType
                audioVisualizerRotationType = AudioVisualizerRotationType.fromInt(
                    sharedPreferences.getInt(AUDIO_VISUALIZER_ROTATION_SETTING_KEY, 0))
                if (lastAudioVisualizerRotationType != audioVisualizerRotationType) {
                    when(audioVisualizerRotationType) {
                        AudioVisualizerRotationType.Axis -> {
                            orientationListenerUI?.enable()
                            orientationListenerGame?.disable()
                        }
                        AudioVisualizerRotationType.Full -> {
                            orientationListenerUI?.disable()
                            orientationListenerGame?.enable()
                        }
                        AudioVisualizerRotationType.None -> {
                            orientationListenerUI?.disable()
                            orientationListenerGame?.disable()
                        }
                    }
                }

                val audioPresent = audioVisualizerProvider.canPlay()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)
                if (showVisualizer) {
                    if (audioPresent)
                        lastAudioTime = currentTimeMillis
                    currentFrameProvider = audioVisualizerProvider
                }

                val notificationRingEnabled = sharedPreferences.getBoolean(SHOW_NOTIFICATION_RING_SETTING_KEY, false)
                val modifier: IntArray? = if (notificationRingEnabled && NotificationListener.notifications.value.size > 0) getNotificationFrame() else null
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
        orientationListenerUI?.disable()
        orientationListenerGame?.disable()
    }
}