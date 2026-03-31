package com.nothinglondon.sdkdemo.demos.animation

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.media.audiofx.Visualizer
import android.os.IBinder
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothinglondon.sdkdemo.MainActivity
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
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class AnimationDemoService : GlyphMatrixService("Animation-Demo") {

    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    private val uiScope = CoroutineScope(Dispatchers.Main)


    // Enhanced audio detection with Visualizer API
    private var visualizer: Visualizer? = null
    private var isVisualizerEnabled = false
    private var lastFft: ByteArray? = null

    // 25-band spectrum from FFT (one per grid column), smoothed
    private val spectrumBands = FloatArray(AUDIO_BANDS) { 0f }
    private var lastUpdateTime: Long = System.currentTimeMillis()

    private var debugText = "NO"
    private var audioPresent = false

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
        initializeVisualizer()

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
                audioPresent = spectrumBands.max() > 0.1
                val currentTimeMillis = System.currentTimeMillis()
                val showVisualizer = audioVisualizerEnabled && (audioPresent || currentTimeMillis - lastUpdateTime < 2000)
                if (showVisualizer) {
                    if (audioPresent)
                        lastUpdateTime = currentTimeMillis
                    frameData = GlyphMatrixFrame.Builder()
                        .addTop(generateAudioWave())
                        .build(applicationContext).render()
                }

                uiScope.launch {
                    glyphMatrixManager.setMatrixFrame(frameData)
                }

                // wait a bit
                //delay((60 - currentTime.second.toLong()) * 1000)
                delay((if (showVisualizer) AUDIO_VISUALIZER_ANIMATION_SPEED else CLOCK_ANIMATION_SPEED).toLong())

                for (i in 0..<spectrumBands.size) {
                    spectrumBands[i] = spectrumBands[i] * 0.999f
                }

            }
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        backgroundScope.cancel()

        try {
            unbindService(connection)
            visualizer?.apply {
                enabled = false
                release()
            }
            visualizer = null
            isVisualizerEnabled = false
        } catch (e: Exception) {
            Log.w("Visualizer", "Error cleaning up visualizer: ${e.message}")
        }
    }

    private fun formatTime(time: Int): String {
        if (time < 10)
            return "0$time"
        return time.toString()
    }

    private fun generateAudioWave(): IntArray {
        val grid = Array(HEIGHT * WIDTH) { 0 }

        for (i in 0..< WIDTH) {
            val targetHeight = spectrumBands[i] * 6
            var range = min((targetHeight).roundToInt(), 6)
            val lastOpacity = (targetHeight) % 1.0
            if (range < targetHeight && range < 6) {
                range++
            }
            grid[6 * WIDTH + i] = 255
            for (j in 0..< range) {
                val op = (if (j == range - 1) lastOpacity else 1.0) * 4096.0
                grid[(6+j) * WIDTH + i] = op.toInt()
                grid[(6-j) * WIDTH + i] = op.toInt()
            }
        }
        return grid.toIntArray()
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

    private fun initializeVisualizer() {
        try {
            // Release any previous instance first
            visualizer?.apply {
                try {
                    enabled = false
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
            visualizer = null
            isVisualizerEnabled = false

            if (Visualizer.getMaxCaptureRate() > 0) {
                val vis = Visualizer(0)
                // Must disable before configuring capture size
                vis.enabled = false
                vis.captureSize = Visualizer.getCaptureSizeRange()[1]
                debugText = "D"

                vis.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {}

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        fft?.let {
                            lastFft = it.clone()
                            processFftData(it)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)

                vis.enabled = true
                visualizer = vis
                isVisualizerEnabled = true
                Log.i("Visualizer", "Visualizer initialized successfully")
            }
        } catch (e: Exception) {
            Log.w("Visualizer", "Failed to initialize Visualizer: ${e.message}")
            isVisualizerEnabled = false
        }
    }

    /**
     * Process FFT data for frequency band analysis and 25-band spectrum.
     */
    private fun processFftData(fft: ByteArray) {
        val numBins = fft.size / 2  // Number of frequency bins (real/imaginary pairs)
        if (numBins < 4) return

        // Compute magnitude for each FFT bin
        val magnitudes = FloatArray(numBins)
        var maxMag = 1f
        for (i in 0 until numBins) {
            val real = fft[i * 2].toFloat()
            val imaginary = fft[i * 2 + 1].toFloat()
            magnitudes[i] = sqrt(real * real + imaginary * imaginary)
            if (magnitudes[i] > maxMag) maxMag = magnitudes[i]
        }

        if (maxMag < 70) {

            for (band in 0 until AUDIO_BANDS) {
                spectrumBands[band] = spectrumBands[band] * 0.75f
            }
            return
        }
        val minBin = 1  // Skip DC component
        val maxBin = numBins - 1
        val logMin = ln(minBin.toFloat())
        val logMax = ln(maxBin.toFloat())

        for (band in 0 until AUDIO_BANDS) {
            val logStart = logMin + (logMax - logMin) * band / AUDIO_BANDS
            val logEnd = logMin + (logMax - logMin) * (band + 1) / AUDIO_BANDS
            val binStart = exp(logStart).toInt().coerceIn(minBin, maxBin)
            val binEnd = exp(logEnd).toInt().coerceIn(binStart + 1, maxBin + 1)

            var bandTypeMult = 0.8f
            if (band >= 5)
                bandTypeMult = 2.5f
            if (band >= 9)
                bandTypeMult = 7f

            var sum = 0f
            var count = 0
            for (bin in binStart until binEnd) {
                sum += magnitudes[bin]
                count++
            }

            val avg = if (count > 0) sum / count else 0f
            val normalized = if (maxMag > 1f) (avg / maxMag).coerceIn(0f, 1f) else avg

            // Smooth with previous value for fluid animation
            if (normalized < 0.1f)
                spectrumBands[band] = spectrumBands[band] * 0.75f + clamp(normalized * bandTypeMult, 0f, 1f) * 0.25f
            else
                spectrumBands[band] = spectrumBands[band] * 0.1f + clamp(normalized * bandTypeMult, 0f, 1f) * 0.9f
        }
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