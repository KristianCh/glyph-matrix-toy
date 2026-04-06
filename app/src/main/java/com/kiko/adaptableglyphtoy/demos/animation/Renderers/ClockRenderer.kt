package com.kiko.adaptableglyphtoy.demos.animation.Renderers

import android.content.Context
import com.kiko.adaptableglyphtoy.demos.animation.ArrayModifierApplyMode
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.BOTTOM_LINE
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.HEIGHT
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.TOP_LINE
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.WIDTH
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.applyModifierToArray
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.drawLine
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.getCenteredTextX
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

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
        val secondsArray = applyModifierToArray(getSecondsArray(currentTime.second), modifier, ArrayModifierApplyMode.ADD)

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
        val currentTime = LocalDateTime.now()
        return (1000.0 - currentTime.nano / 1000000.0).roundToLong()
    }

    override fun canPlay(): Boolean {
        return true
    }

    private fun formatTime(time: Int): String {
        if (time < 10)
            return "0$time"
        return time.toString()
    }

    private fun getSecondsArray(seconds: Int): IntArray {
        val grid = Array(HEIGHT * WIDTH) { 0 }.toIntArray()
        val targetAngle = seconds / 60.0 * 360

        val targetX = sin(-toRadians(targetAngle + 180)) * 6
        val targetY = cos(toRadians(targetAngle + 180)) * 6

        val x = targetX.roundToInt() + 6
        val y = targetY.roundToInt() + 6
        drawLine(grid, 6, 6, x, y, 1024)

        return grid
    }
}