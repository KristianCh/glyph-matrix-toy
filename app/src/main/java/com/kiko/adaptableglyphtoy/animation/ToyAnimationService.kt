package com.kiko.adaptableglyphtoy.animation

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import com.kiko.adaptableglyphtoy.GlyphMatrixService
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.BOTTOM_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getCenteredTextX
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getNotificationFrame
import com.kiko.adaptableglyphtoy.animation.renderers.AudioVisualizerRenderer
import com.kiko.adaptableglyphtoy.animation.renderers.ClockRenderer
import com.kiko.adaptableglyphtoy.animation.renderers.GameOfLifeRenderer
import com.kiko.adaptableglyphtoy.animation.renderers.IFrameRenderer
import com.kiko.adaptableglyphtoy.animation.renderers.TextScrollRenderer
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
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
    private val textScrollRenderer = TextScrollRenderer()

    private var orientationListenerUI: OrientationListener? = null
    private var orientationListenerGame: OrientationListener? = null

    private lateinit var settings: SettingsRepository
    private var currentPrimaryToy = PrimaryToy.Clock
    private var audioVisualizerEnabled = false
    private var mediaScrollEnabled = false
    private var currentFrameRenderer: IFrameRenderer = clockRenderer
    private var notificationRingEnabled = false
    private var notificationScrollEnabled = false
    private var notificationBodyEnabled = false
    private var notificationScrollCooldown = 0

    // Coordination state
    private var lastAudioTime: Long = System.currentTimeMillis()
    private var isScrollingMediaInfo = false
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
            ACTION_INTERACT_TOY -> interactWithToy()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cyclePrimaryToy() {
        val nextToy = currentPrimaryToy.next()
        settings.setPrimaryToy(nextToy.value)
        Log.i(LOG_TAG, "Cycled toy to: $nextToy")
    }

    private fun interactWithToy() {
        currentFrameRenderer.interact()
        Log.i(LOG_TAG, "Interacted with: ${currentFrameRenderer::class.java.simpleName}")
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
        textScrollRenderer.initialize(this)

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
                currentFrameRenderer.initialize(this@ToyAnimationService)
            }
        }
        launch {
            settings.audioVisualizerEnabled.collect { enabled ->
                audioVisualizerEnabled = enabled
                audioVisualizerRenderer.setEnabled(enabled)
            }
        }
        launch { settings.mediaScrollEnabled.collect {
            mediaScrollEnabled = it
            if (!mediaScrollEnabled && isScrollingMediaInfo)
                clearScrollingText()
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
                textScrollRenderer.clear()
            }
        }
        launch { settings.notificationBodyEnabled.collect { notificationBodyEnabled = it } }
        launch { settings.notificationScrollCooldown.collect { notificationScrollCooldown = it } }
        launch {
            NotificationListener.songInfoFlow.collect {
                if (!mediaScrollEnabled) return@collect
                if (NotificationListener.mediaPlaybackStateFlow.value == PlaybackState.STATE_PLAYING) {
                    isScrollingMediaInfo = true
                    textScrollRenderer.tryStartScroll(it, { isScrollingMediaInfo = false })
                }
            }
        }
        launch {
            NotificationListener.mediaPlaybackStateFlow.collect {
                if (!mediaScrollEnabled) return@collect
                if (it == PlaybackState.STATE_PLAYING) {
                    isScrollingMediaInfo = true
                    textScrollRenderer.tryStartScroll(
                        NotificationListener.songInfoFlow.value,
                        { isScrollingMediaInfo = false })
                }
                else if (it == PlaybackState.STATE_PAUSED && isScrollingMediaInfo) {
                    clearScrollingText()
                }
            }
        }
    }

    private suspend fun CoroutineScope.runAnimationLoop(gmm: GlyphMatrixManager) {
        Log.i(LOG_TAG, "Starting animation loop")
        while (isActive) {
            val currentTimeMillis = System.currentTimeMillis()
            currentFrameRenderer = getPrimaryFrameRenderer()

            // Audio priority
            val audioPresent = audioVisualizerRenderer.canPlay()

            if (audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastAudioTime < AUDIO_COOLDOWN_TIME)) {
                if (audioPresent) lastAudioTime = currentTimeMillis
                currentFrameRenderer = audioVisualizerRenderer
            }
            if (audioPresent)
                lastAudioTime = System.currentTimeMillis()

            // Notification priority
            handleNotifications(currentTimeMillis)
            if (textScrollRenderer.canPlay()) {
                currentFrameRenderer = textScrollRenderer
            }

            // Render Frame
            val modifier = if (notificationRingEnabled && lastNotificationCount > 0) getNotificationFrame() else null
            
            try {
                val frameData = currentFrameRenderer.getFrameData(modifier).build(applicationContext).render()
                uiScope.launch {
                    gmm.setMatrixFrame(frameData)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error rendering frame", e)
            }

            delay(currentFrameRenderer.getFrameTime())
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
                if (str != null) {
                    isScrollingMediaInfo = false
                    textScrollRenderer.tryStartScroll(str, {
                        lastNotificationScrollFinishTime = System.currentTimeMillis()
                    })
                }
            } else if (wasRemoved) {
                clearScrollingText()
            }
        }
    }

    private fun clearScrollingText() {
        isScrollingMediaInfo = false
        textScrollRenderer.clear()
    }

    private fun isNotificationScrollOffCooldown(currentTimeMillis: Long): Boolean {
        return  notificationScrollCooldown > 0 &&
                !textScrollRenderer.canPlay() &&
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

    private fun getPrimaryFrameRenderer(): IFrameRenderer {
        return when (currentPrimaryToy) {
            PrimaryToy.Clock -> clockRenderer
            PrimaryToy.GameOfLife -> gameOfLifeRenderer
            else -> clockRenderer
        }
    }
}
