package com.nothinglondon.sdkdemo.demos.animation.Renderers

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothinglondon.sdkdemo.demos.animation.ArrayModifierApplyMode
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.HEIGHT
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.MAX_BRIGHTNESS
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.WIDTH
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.applyModifierToArray
import java.lang.Math.clamp
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AudioVisualizerRenderer: IFrameRenderer {
    private companion object {
        private const val AUDIO_VISUALIZER_ANIMATION_SPEED: Long = 33
        private const val AUDIO_BANDS = 13
        private const val AUDIO_PRESENT_THRESHOLD = 50
        private const val AUDIO_VISUALIZE_THRESHOLD = 90
    }

    private var visualizer: Visualizer? = null
    private var isVisualizerEnabled = false
    private var lastFft: ByteArray? = null

    // 13-band spectrum from FFT (one per grid column), smoothed
    private val spectrumBands = FloatArray(AUDIO_BANDS) { 0f }
    private var audioPresent = false

    override fun initialize(context: Context) {
        initializeVisualizer()
    }

    override fun dispose() {
        try {
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

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
        val grid = Array(HEIGHT * WIDTH) { 0 }

        for (i in 0..<WIDTH) {
            val targetHeight = spectrumBands[i] * 6
            var range = min((targetHeight).roundToInt(), 6)
            val lastOpacity = (targetHeight) % 1.0
            if (range < targetHeight && range < 6) {
                range++
            }
            grid[6 * WIDTH + i] = 255
            for (j in 0..< range) {
                val op = (if (j == range - 1) lastOpacity else 1.0) * MAX_BRIGHTNESS
                grid[(6+j) * WIDTH + i] = op.toInt()
                grid[(6-j) * WIDTH + i] = op.toInt()
            }
        }


        val frameData = GlyphMatrixFrame.Builder()
            .addTop(applyModifierToArray(grid.toIntArray(), modifier, ArrayModifierApplyMode.ADD))
        return frameData
    }

    override fun getFrameTime(): Long {
        return AUDIO_VISUALIZER_ANIMATION_SPEED
    }

    override fun canPlay(): Boolean {
        return audioPresent
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
     * Process FFT data for frequency band analysis and 13-band spectrum.
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

        audioPresent = maxMag > AUDIO_PRESENT_THRESHOLD
        if (maxMag < AUDIO_VISUALIZE_THRESHOLD) {

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
}