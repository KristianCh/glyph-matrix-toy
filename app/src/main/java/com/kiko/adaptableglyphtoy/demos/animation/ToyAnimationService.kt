package com.kiko.adaptableglyphtoy.demos.animation

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.util.Log
import androidx.core.content.edit
import com.nothing.ketchum.GlyphMatrixManager
import com.kiko.adaptableglyphtoy.demos.GlyphMatrixService
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.getNotificationFrame
import com.kiko.adaptableglyphtoy.demos.animation.Renderers.AudioVisualizerRenderer
import com.kiko.adaptableglyphtoy.demos.animation.Renderers.ClockRenderer
import com.kiko.adaptableglyphtoy.demos.animation.Renderers.GameOfLiveRenderer
import com.kiko.adaptableglyphtoy.demos.animation.Renderers.IFrameRenderer
import com.kiko.adaptableglyphtoy.demos.animation.Renderers.NotificationTextScrollRenderer
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.AUDIO_VISUALIZER_ROTATION_SETTING_KEY
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.SHOW_NOTIFICATION_RING_SETTING_KEY
import com.kiko.adaptableglyphtoy.demos.animation.SettingsConstants.SHOW_NOTIFICATION_SCROLL_SETTING_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ToyAnimationService : GlyphMatrixService("ToyAnimation") {
    companion object {
        private val LOG_TAG = ToyAnimationService::class.java.simpleName
        private const val AUDIO_COOLDOWN_TIME = 2000
        private const val NOTIFICATION_SCROLL_COOLDOWN_TIME = 30000

        const val ACTION_CYCLE_TOY = "com.kiko.adaptableglyphtoy.ACTION_CYCLE_TOY"
        const val ACTION_INTERACT_TOY = "com.kiko.adaptableglyphtoy.ACTION_INTERACT_TOY"

        private val _currentRotation = MutableStateFlow(Orientation.PORTRAIT_UP)
        val currentRotation: StateFlow<Orientation> = _currentRotation
        private val _currentAngle = MutableStateFlow(0)
        val currentAngle: StateFlow<Int> = _currentAngle
        var audioVisualizerEnabled = true
        var audioVisualizerRotationType = AudioVisualizerRotationType.Axis

        fun onSettingsUpdated() {
            Log.i(LOG_TAG, "Settings updated")
            instance?.updateSettings()
        }

        private var instance:ToyAnimationService? = null

        fun setInstance(a:ToyAnimationService){
            this.instance = a
        }
    }

    private var backgroundScope = CoroutineScope(Dispatchers.IO)
    private var uiScope = CoroutineScope(Dispatchers.Main)
    private var animationJob: Job? = null

    private val clockRenderer: ClockRenderer = ClockRenderer()
    private val gameOfLiveRenderer: GameOfLiveRenderer = GameOfLiveRenderer()
    private var orientationListenerUI: OrientationListener? = null
    private var orientationListenerGame: OrientationListener? = null

    // Save data
    lateinit var sharedPreferences: SharedPreferences
    private var currentPrimaryToy = PrimaryToy.Clock

    var currentFrameRenderer: IFrameRenderer = clockRenderer
    private var notificationRingEnabled = false
    private var notificationScrollEnabled = false
    private var notificationBodyEnabled = false
    private var notificationScrollCooldown = 0

    // Audio visualizer values
    private val audioVisualizerRenderer: AudioVisualizerRenderer = AudioVisualizerRenderer()
    private var lastAudioTime: Long = System.currentTimeMillis()

    // Notification values
    private val notificationTextScrollRenderer: NotificationTextScrollRenderer = NotificationTextScrollRenderer()
    private var lastNotificationCount = 0
    private var lastNotificationScrollFinishTime: Long = System.currentTimeMillis()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CYCLE_TOY) {
            cyclePrimaryToy()
            // If the service is not currently bound (matrix manager is null), try to re-init it
            if (glyphMatrixManager == null) {
                Log.i(LOG_TAG, "Manager was null on cycle intent, re-initializing")
                initManager()
            }
        }
        else if (intent?.action == ACTION_INTERACT_TOY) {
            interactWithPrimaryToy()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cyclePrimaryToy() {
        val nextToy = currentPrimaryToy.next()
        sharedPreferences.edit { putInt(PRIMARY_TOY_SETTING_KEY, nextToy.value) }
        updateSettings()
        currentFrameRenderer.initialize(this)

        Log.i(LOG_TAG, "Cycled toy to: $nextToy")
    }

    private fun interactWithPrimaryToy() {
        currentFrameRenderer.interact()
        Log.i(LOG_TAG, "Interacted with toy: $currentPrimaryToy")
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        Log.d(LOG_TAG, "performOnServiceConnected")
        setInstance(this@ToyAnimationService)

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
        updateSettings()

        // Ensure scopes are active
        if (!backgroundScope.isActive) backgroundScope = CoroutineScope(Dispatchers.IO)
        if (!uiScope.isActive) uiScope = CoroutineScope(Dispatchers.Main)

        animationJob?.cancel()
        animationJob = backgroundScope.launch {
            Log.i(LOG_TAG, "Starting animation loop")
            while (isActive) {
                val currentTimeMillis = System.currentTimeMillis()
                updateCurrentFrameRenderer()

                // AUDIO VISUALIZER HANDLING
                val audioPresent = audioVisualizerRenderer.canPlay()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)
                if (showVisualizer) {
                    if (audioPresent)
                        lastAudioTime = currentTimeMillis
                    currentFrameRenderer = audioVisualizerRenderer
                }

                // NOTIFICATION HANDLING
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
                
                // Safety check for bound status before rendering
                glyphMatrixManager?.let { gmm ->
                    val frameData = currentFrameRenderer.getFrameData(modifier).build(applicationContext).render()
                    uiScope.launch {
                        gmm.setMatrixFrame(frameData)
                    }
                }

                // wait a bit
                delay(currentFrameRenderer.getFrameTime())
            }
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        Log.d(LOG_TAG, "performOnServiceDisconnected")
        animationJob?.cancel()
        orientationListenerUI?.disable()
        orientationListenerGame?.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundScope.cancel()
        uiScope.cancel()
        instance = null
    }

    private fun updateCurrentFrameRenderer() {
        currentFrameRenderer = when (currentPrimaryToy) {
            PrimaryToy.Clock -> clockRenderer
            PrimaryToy.GameOfLife -> gameOfLiveRenderer
        }
    }

    private fun updateSettings() {
        currentPrimaryToy = PrimaryToy.fromInt(sharedPreferences.getInt(PRIMARY_TOY_SETTING_KEY, 0))

        audioVisualizerRotationType = AudioVisualizerRotationType.fromInt(sharedPreferences.getInt(AUDIO_VISUALIZER_ROTATION_SETTING_KEY, 0))
        audioVisualizerEnabled = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, false)
        audioVisualizerRenderer.setEnabled(audioVisualizerEnabled)
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
        updateCurrentFrameRenderer()

        notificationRingEnabled = sharedPreferences.getBoolean(SHOW_NOTIFICATION_RING_SETTING_KEY, false)
        notificationScrollEnabled = sharedPreferences.getBoolean(SHOW_NOTIFICATION_SCROLL_SETTING_KEY, false)
        notificationBodyEnabled = sharedPreferences.getBoolean(NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY, false)
        notificationScrollCooldown = sharedPreferences.getInt(NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY, 0)
        Log.i(LOG_TAG, "Updated settings")
    }
}