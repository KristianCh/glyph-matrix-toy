package com.nothinglondon.sdkdemo.demos.animation

import android.content.Context
import android.content.SharedPreferences
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.getNotificationFrame
import com.nothinglondon.sdkdemo.demos.animation.Renderers.AudioVisualizerRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.ClockRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.GameOfLiveRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.IFrameRenderer
import com.nothinglondon.sdkdemo.demos.animation.Renderers.NotificationTextScrollRenderer
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ROTATION_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SHOW_NOTIFICATION_RING_SETTING_KEY
import com.nothinglondon.sdkdemo.demos.animation.SettingsConstants.SHOW_NOTIFICATION_SCROLL_SETTING_KEY
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
        private const val NOTIFICATION_SCROLL_COOLDOWN_TIME = 30000

        private val _currentRotation = MutableStateFlow(Orientation.PORTRAIT_UP)
        val currentRotation: StateFlow<Orientation> = _currentRotation
        private val _currentAngle = MutableStateFlow(0)
        val currentAngle: StateFlow<Int> = _currentAngle
        var audioVisualizerEnabled = true
        var audioVisualizerRotationType = AudioVisualizerRotationType.Axis
    }

    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val clockRenderer: ClockRenderer = ClockRenderer()
    private val gameOfLiveRenderer: GameOfLiveRenderer = GameOfLiveRenderer()
    private var orientationListenerUI: OrientationListener? = null
    private var orientationListenerGame: OrientationListener? = null
    lateinit var sharedPreferences: SharedPreferences

    // Audio visualizer values
    private val audioVisualizerRenderer: AudioVisualizerRenderer = AudioVisualizerRenderer()
    private var lastAudioTime: Long = System.currentTimeMillis()

    // Notification values
    private val notificationTextScrollRenderer: NotificationTextScrollRenderer = NotificationTextScrollRenderer()
    private var lastNotificationCount = 0
    private var lastNotificationScrollFinishTime: Long = System.currentTimeMillis()

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        audioVisualizerRenderer.initialize(this)
        clockRenderer.initialize(this)
        gameOfLiveRenderer.initialize(this)
        notificationTextScrollRenderer.initialize(this)

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

                var currentFrameRenderer: IFrameRenderer = when (currentPrimaryToy) {
                    0 -> clockRenderer
                    1 -> gameOfLiveRenderer
                    else -> clockRenderer
                }

                // AUDIO VISUALIZER HANDLING
                var lastAudioVisualizerEnabled = audioVisualizerEnabled
                audioVisualizerEnabled = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, false)
                if (lastAudioVisualizerEnabled != audioVisualizerEnabled) {
                    audioVisualizerRenderer.setEnabled(audioVisualizerEnabled)
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

                val audioPresent = audioVisualizerRenderer.canPlay()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)
                if (showVisualizer) {
                    if (audioPresent)
                        lastAudioTime = currentTimeMillis
                    currentFrameRenderer = audioVisualizerRenderer
                }

                // NOTIFICATION HANDLING

                val notificationRingEnabled = sharedPreferences.getBoolean(SHOW_NOTIFICATION_RING_SETTING_KEY, false)
                val notificationScrollEnabled = sharedPreferences.getBoolean(SHOW_NOTIFICATION_SCROLL_SETTING_KEY, false)
                val notificationBodyEnabled = sharedPreferences.getBoolean(NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY, false)
                val notificationScrollCooldown = sharedPreferences.getInt(NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY, 0)
                val currentNotificationCount = NotificationListener.notifications.value.size
                val isNewNotification = lastNotificationCount < currentNotificationCount
                val notificationsRemoved = lastNotificationCount > currentNotificationCount
                lastNotificationCount = currentNotificationCount

                if (notificationScrollEnabled)
                {
                    if (isNewNotification ||
                        (
                            notificationScrollCooldown > 0 &&
                            !notificationTextScrollRenderer.canPlay() &&
                            currentTimeMillis - lastNotificationScrollFinishTime > notificationScrollCooldown * 1000 &&
                            currentNotificationCount > 0
                        ))
                    {
                        notificationTextScrollRenderer.TryStartScroll({
                            lastNotificationScrollFinishTime = System.currentTimeMillis()
                        }, notificationBodyEnabled)
                    }
                    else if (notificationsRemoved) {
                        notificationTextScrollRenderer.clear()
                    }
                }

                if (notificationTextScrollRenderer.canPlay()) {
                    currentFrameRenderer = notificationTextScrollRenderer
                }

                val modifier: IntArray? = if (notificationRingEnabled && currentNotificationCount > 0) getNotificationFrame() else null
                val frameData = currentFrameRenderer.getFrameData(modifier).build(applicationContext).render()
                uiScope.launch {
                    glyphMatrixManager.setMatrixFrame(frameData)
                }

                // wait a bit
                delay(currentFrameRenderer.getFrameTime())
            }
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        backgroundScope.cancel()
        orientationListenerUI?.disable()
        orientationListenerGame?.disable()
    }
}