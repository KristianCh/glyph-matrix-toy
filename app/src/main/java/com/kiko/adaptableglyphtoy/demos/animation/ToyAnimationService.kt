package com.kiko.adaptableglyphtoy.demos.animation

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import com.kiko.adaptableglyphtoy.demos.GlyphMatrixService
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.getNotificationFrame
import com.kiko.adaptableglyphtoy.demos.animation.renderers.AudioVisualizerRenderer
import com.kiko.adaptableglyphtoy.demos.animation.renderers.ClockRenderer
import com.kiko.adaptableglyphtoy.demos.animation.renderers.GameOfLifeRenderer
import com.kiko.adaptableglyphtoy.demos.animation.renderers.IFrameRenderer
import com.kiko.adaptableglyphtoy.demos.animation.renderers.TextScrollRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

        const val ACTION_CYCLE_TOY = "com.kiko.adaptableglyphtoy.ACTION_CYCLE_TOY"
        const val ACTION_INTERACT_TOY = "com.kiko.adaptableglyphtoy.ACTION_INTERACT_TOY"

        private val _currentRotation = MutableStateFlow(Orientation.PORTRAIT_UP)
        val currentRotation: StateFlow<Orientation> = _currentRotation
        private val _currentAngle = MutableStateFlow(0)
        val currentAngle: StateFlow<Int> = _currentAngle
        var audioVisualizerRotationType = AudioVisualizerRotationType.Axis
    }

    private var backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Manages all coroutines tied to a specific Glyph Matrix connection
    private var connectionJob: Job? = null

    private val clockRenderer = ClockRenderer()
    private val gameOfLifeRenderer = GameOfLifeRenderer()
    private val audioVisualizerRenderer = AudioVisualizerRenderer()
    private val notificationTextScrollRenderer = TextScrollRenderer()

    private var orientationListenerUI: OrientationListener? = null
    private var orientationListenerGame: OrientationListener? = null

    private lateinit var settings: SettingsRepository
    private var currentPrimaryToy = PrimaryToy.Clock
    private var audioVisualizerEnabled = false
    private var currentFrameRenderer: IFrameRenderer = clockRenderer
    private var notificationRingEnabled = false
    private var notificationScrollEnabled = false
    private var notificationBodyEnabled = false
    private var notificationScrollCooldown = 0

    // Coordination state
    private var lastAudioTime: Long = System.currentTimeMillis()
    private var lastNotificationCount = 0
    private var lastNotificationScrollFinishTime: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CYCLE_TOY -> {
                cyclePrimaryToy()
                if (glyphMatrixManager == null) initManager()
            }
            ACTION_INTERACT_TOY -> interactWithPrimaryToy()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cyclePrimaryToy() {
        val nextToy = currentPrimaryToy.next()
        settings.setPrimaryToy(nextToy.value)
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

        // 1. Initialize Renderers
        audioVisualizerRenderer.initialize(this)
        clockRenderer.initialize(this)
        gameOfLifeRenderer.initialize(this)
        notificationTextScrollRenderer.initialize(this)

        // 2. Setup Sensors
        setupOrientationListeners()

        // 3. Initialize Coordination State
        lastAudioTime = System.currentTimeMillis() - AUDIO_COOLDOWN_TIME

        // Ensure scopes are active
        if (!backgroundScope.isActive) backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        if (!uiScope.isActive) uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // 4. Start reactive work tied to this connection
        connectionJob?.cancel()
        connectionJob = backgroundScope.launch {
            // A. Observe Settings
            observeSettings()

            // B. Run Animation Loop
            runAnimationLoop(glyphMatrixManager)
        }
    }

    private fun CoroutineScope.observeSettings() {
        launch {
            settings.primaryToy.collect { toyValue ->
                currentPrimaryToy = PrimaryToy.fromInt(toyValue)
                updateCurrentFrameRenderer()
                currentFrameRenderer.initialize(this@ToyAnimationService)
            }
        }
        launch {
            settings.audioVisualizerEnabled.collect { enabled ->
                audioVisualizerEnabled = enabled
                audioVisualizerRenderer.setEnabled(enabled)
            }
        }
        launch {
            settings.audioVisualizerRotationType.collect { type ->
                audioVisualizerRotationType = AudioVisualizerRotationType.fromInt(type)
                updateRotationListeners()
            }
        }
        launch { settings.notificationRingEnabled.collect { notificationRingEnabled = it } }
        launch { settings.notificationScrollEnabled.collect {
            notificationScrollEnabled = it
            if (!it)
                notificationTextScrollRenderer.clear()
            }
        }
        launch { settings.notificationBodyEnabled.collect { notificationBodyEnabled = it } }
        launch { settings.notificationScrollCooldown.collect { notificationScrollCooldown = it } }
    }

    private suspend fun CoroutineScope.runAnimationLoop(gmm: GlyphMatrixManager) {
        Log.i(LOG_TAG, "Starting animation loop")
        while (isActive) {
            val currentTimeMillis = System.currentTimeMillis()
            updateCurrentFrameRenderer()

            // Determine active renderer based on priority
            var activeRenderer = currentFrameRenderer

            // Audio priority
            val audioPresent = audioVisualizerRenderer.canPlay()

            if (audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)) {
                if (audioPresent) lastAudioTime = currentTimeMillis
                activeRenderer = audioVisualizerRenderer
            }
            if (audioPresent)
                lastAudioTime = System.currentTimeMillis()

            // Notification priority
            handleNotifications(currentTimeMillis)
            if (notificationTextScrollRenderer.canPlay()) {
                activeRenderer = notificationTextScrollRenderer
            }

            // Render Frame
            val modifier = if (notificationRingEnabled && lastNotificationCount > 0) getNotificationFrame() else null
            
            try {
                val frameData = activeRenderer.getFrameData(modifier).build(applicationContext).render()
                uiScope.launch {
                    gmm.setMatrixFrame(frameData)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error rendering frame", e)
            }

            delay(activeRenderer.getFrameTime())
        }
    }

    private fun handleNotifications(currentTimeMillis: Long) {
        val currentCount = NotificationListener.notifications.value.size
        val isNew = lastNotificationCount < currentCount
        val wasRemoved = lastNotificationCount > currentCount
        lastNotificationCount = currentCount

        if (notificationScrollEnabled) {
            if (isNew || (notificationScrollCooldown > 0 && isNotificationScrollOffCooldown(currentTimeMillis) && currentCount > 0)) {
                val str = NotificationListener.mostRecentNotificationString(notificationBodyEnabled)
                if (str != null)
                    notificationTextScrollRenderer.tryStartScroll(str, {
                        lastNotificationScrollFinishTime = System.currentTimeMillis() })
            } else if (wasRemoved) {
                notificationTextScrollRenderer.clear()
            }
        }
    }

    private fun isNotificationScrollOffCooldown(currentTimeMillis: Long): Boolean {
        return  notificationScrollCooldown > 0 &&
                !notificationTextScrollRenderer.canPlay() &&
                currentTimeMillis - lastNotificationScrollFinishTime > notificationScrollCooldown * 1000
    }

    private fun setupOrientationListeners() {
        orientationListenerUI = orientationListenerUI ?: OrientationListener(this, SENSOR_DELAY_UI, { _currentRotation.value = it }, { _currentAngle.value = it })
        orientationListenerGame = orientationListenerGame ?: OrientationListener(this, SENSOR_DELAY_NORMAL, { _currentRotation.value = it }, { _currentAngle.value = it })
    }

    private fun updateRotationListeners() {
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

    override fun performOnServiceDisconnected(context: Context) {
        Log.d(LOG_TAG, "performOnServiceDisconnected")
        connectionJob?.cancel() // Cancels settings observation AND animation loop
        orientationListenerUI?.disable()
        orientationListenerGame?.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundScope.cancel()
        uiScope.cancel()
    }

    private fun updateCurrentFrameRenderer() {
        currentFrameRenderer = when (currentPrimaryToy) {
            PrimaryToy.Clock -> clockRenderer
            PrimaryToy.GameOfLife -> gameOfLifeRenderer
            else -> clockRenderer
        }
    }
}
