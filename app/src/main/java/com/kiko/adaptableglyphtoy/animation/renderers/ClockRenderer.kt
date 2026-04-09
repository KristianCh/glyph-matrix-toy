package com.kiko.adaptableglyphtoy.animation.renderers

import android.content.Context
import com.kiko.adaptableglyphtoy.animation.ArrayModifierApplyMode
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.BOTTOM_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.HEIGHT
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.MAX_BRIGHTNESS
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.TOP_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.WIDTH
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.applyModifierToArray
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.drawLine
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.formatTime
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getCenteredTextX
import com.kiko.adaptableglyphtoy.animation.SettingsRepository
import com.kiko.adaptableglyphtoy.animation.ToyAnimationService
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import java.lang.Math.toRadians
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

class ClockRenderer: IFrameRenderer {
    private var clockFace = 0
    private lateinit var settings: SettingsRepository
    override fun initialize(context: ToyAnimationService) {
        settings = SettingsRepository(context)
        clockFace = settings.getClockFace()
    }

    override fun dispose() { }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
        val currentTime = LocalDateTime.now()
        val hourText = formatTime(currentTime.hour)
        val minuteText = formatTime(currentTime.minute)
        val frameToDraw = GlyphMatrixFrame.Builder()

        val grid = Array(HEIGHT * WIDTH) { 0 }.toIntArray()

        drawHand(grid, currentTime.second / 60.0, 6, 1024)

        val secondsArray = applyModifierToArray(grid, modifier, ArrayModifierApplyMode.ADD)

        if (clockFace == 0) {
            val textObject = GlyphMatrixObject.Builder().setText(hourText)
                .setPosition(getCenteredTextX(hourText), TOP_LINE)
                .setBrightness(255)
                .build()

            val textObject2 = GlyphMatrixObject.Builder().setText(minuteText)
                .setPosition(getCenteredTextX(minuteText), BOTTOM_LINE)
                .build()
            frameToDraw
                .addTop(textObject)
                .addMid(textObject2)
        }
        else {
            drawHand(secondsArray, currentTime.minute / 60.0, 5, 2048)
            drawHand(secondsArray, (currentTime.hour % 12) / 12.0, 4, MAX_BRIGHTNESS)
        }
        frameToDraw.addLow(secondsArray)

        return frameToDraw
    }

    override fun getFrameTime(): Long {
        val currentTime = LocalDateTime.now()
        return (1000.0 - currentTime.nano / 1000000.0).roundToLong()
    }

    override fun canPlay(): Boolean {
        return true
    }

    override fun interact() {
        updateClockFace()
    }

    fun updateClockFace() {
        clockFace = (clockFace + 1) % 2
        settings.setClockFace(clockFace)
    }

    private fun drawHand(grid: IntArray, progress: Double, handLength: Int, brightness: Int): IntArray {
        val targetAngle = progress * 360

        val targetX = sin(-toRadians(targetAngle + 180)) * handLength
        val targetY = cos(toRadians(targetAngle + 180)) * handLength

        val x = targetX.roundToInt() + 6
        val y = targetY.roundToInt() + 6
        drawLine(grid, 6, 6, x, y, brightness)

        return grid
    }
}