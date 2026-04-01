package com.nothinglondon.sdkdemo.demos.animation.Renderers

import android.content.Context
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import com.nothinglondon.sdkdemo.demos.animation.ArrayModifierApplyMode
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.BOTTOM_LINE
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.HEIGHT
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.MID_POINT
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.TOP_LINE
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.WIDTH
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.applyModifierToArray
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.getCenteredTextX
import com.nothinglondon.sdkdemo.demos.animation.NotificationListener
import java.lang.Math.clamp
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ClockRenderer: IFrameRenderer {
    private companion object {
        private const val CLOCK_ANIMATION_SPEED: Long = 200
    }

    override fun initialize(context: Context) { }

    override fun dispose() { }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
        val currentTime = LocalDateTime.now()
        val hourText = formatTime(currentTime.hour)
        val minuteText = formatTime(currentTime.minute)
        val secondsArray = applyModifierToArray(getSecondsArray(currentTime.second + currentTime.nano / 1000000000.0), modifier, ArrayModifierApplyMode.ADD)

        val textObject = GlyphMatrixObject.Builder().setText(hourText)
            .setPosition(getCenteredTextX(hourText), TOP_LINE)
            .setBrightness(255)
            .build()

        val textObject2 = GlyphMatrixObject.Builder().setText(minuteText)
            .setPosition( getCenteredTextX(minuteText), BOTTOM_LINE)
            .build()

        val frameToDraw = GlyphMatrixFrame.Builder()
            .addTop(textObject)
            .addMid(textObject2)
            .addLow(secondsArray)

        return frameToDraw
    }

    override fun getFrameTime(): Long {
        return CLOCK_ANIMATION_SPEED
    }

    override fun canPlay(): Boolean {
        return true
    }

    private fun formatTime(time: Int): String {
        if (time < 10)
            return "0$time"
        return time.toString()
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
}