package com.kiko.adaptableglyphtoy.demos.animation

import java.lang.Character.toLowerCase
import java.time.LocalDateTime
import kotlin.math.abs

object GlyphMatrixUtils {
    const val WIDTH = 13
    const val HEIGHT = 13
    const val HALF_HEIGHT = HEIGHT.toDouble() / 2
    const val MID_POINT = HEIGHT / 2

    const val CHARACTER_WIDTH = 4
    const val TOP_LINE = 0
    const val MID_LINE = 3
    const val BOTTOM_LINE = 6
    const val CHARACTER_SEPARATOR_WIDTH = 2
    const val MAX_BRIGHTNESS = 4096
    private val I = 255
    private val J = I * 6

    private val notificationFrame = arrayOf(
        0, 0, 0, 0, I, I, I, I, I, 0, 0, 0, 0,
        0, 0, I, I, 0, 0, 0, 0, 0, I, I, 0, 0,
        0, I, 0, 0, 0, 0, 0, 0, 0, 0, 0, I, 0,
        0, I, 0, 0, 0, 0, 0, 0, 0, 0, 0, I, 0,
        I, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, I,
        I, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, I,
        I, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, I,
        I, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, I,
        I, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, I,
        0, I, 0, 0, 0, 0, 0, 0, 0, 0, 0, I, 0,
        0, I, 0, 0, 0, 0, 0, 0, 0, 0, 0, I, 0,
        0, 0, I, I, 0, 0, 0, 0, 0, I, I, 0, 0,
        0, 0, 0, 0, I, I, I, I, I, 0, 0, 0, 0,
    ).toIntArray()

    val notificationFrame2 = arrayOf(
        0, 0, 0, 0, J, J, J, J, J, 0, 0, 0, 0,
        0, 0, J, J, 0, 0, 0, 0, 0, J, J, 0, 0,
        0, J, 0, 0, 0, 0, 0, 0, 0, 0, 0, J, 0,
        0, J, 0, 0, 0, 0, 0, 0, 0, 0, 0, J, 0,
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
        0, J, 0, 0, 0, 0, 0, 0, 0, 0, 0, J, 0,
        0, J, 0, 0, 0, 0, 0, 0, 0, 0, 0, J, 0,
        0, 0, J, J, 0, 0, 0, 0, 0, J, J, 0, 0,
        0, 0, 0, 0, J, J, J, J, J, 0, 0, 0, 0,
    ).toIntArray()

    val crossFrame = arrayOf(
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
        0, J, 0, 0, 0, 0, 0, 0, 0, 0, 0, J, 0,
        0, 0, J, 0, 0, 0, 0, 0, 0, 0, J, 0, 0,
        0, 0, 0, J, 0, 0, 0, 0, 0, J, 0, 0, 0,
        0, 0, 0, 0, J, 0, 0, 0, J, 0, 0, 0, 0,
        0, 0, 0, 0, 0, J, 0, J, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, J, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, J, 0, J, 0, 0, 0, 0, 0,
        0, 0, 0, 0, J, 0, 0, 0, J, 0, 0, 0, 0,
        0, 0, 0, J, 0, 0, 0, 0, 0, J, 0, 0, 0,
        0, 0, J, 0, 0, 0, 0, 0, 0, 0, J, 0, 0,
        0, J, 0, 0, 0, 0, 0, 0, 0, 0, 0, J, 0,
        J, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, J,
    ).toIntArray()

    fun getNotificationFrame(): IntArray {
        val second = LocalDateTime.now().second
        return if (second % 2 != 0) notificationFrame else notificationFrame2
    }

    fun drawLine(grid: IntArray, x0: Int, y0: Int, x1: Int, y1: Int, brightness: Int) {
        var x = x0
        var y = y0

        val dx = abs(x1 - x)
        val sx = if (x < x1) 1 else -1
        val dy = -abs(y1 - y)
        val sy = if (y < y1) 1 else -1
        var error = dx + dy

        while (true) {
            grid[y * WIDTH + x] = brightness
            val e2 = 2 * error
            if (e2 >= dy) {
                if (x == x1) break
                error = error + dy
                x = x + sx
            }
            if (e2 <= dx) {
                if (y == y1) break
                error = error + dx
                y = y + sy
            }
        }
    }

    fun applyModifierToArray(array: IntArray, modifier: IntArray?, mode: ArrayModifierApplyMode): IntArray {
        if (modifier == null || array.size != modifier.size) return array
        val result = when (mode) {
            ArrayModifierApplyMode.ADD -> array.zip(modifier).map { (a, m )-> a + m }.toIntArray()
            ArrayModifierApplyMode.MULTIPLY -> array.zip(modifier).map { (a, m) -> a * m }.toIntArray()
            ArrayModifierApplyMode.REPLACE_NON_ZERO -> array.zip(modifier).map { (a, m) -> if (m > 0) m else a }.toIntArray()
        }
        return result
    }

    fun getTextLength(string: String): Int {
        var l = 0
        for (i in 0..<string.length) {
            l += getCharacterPixelWidth(string[i]) + 1
        }
        return l
    }

    fun getCenteredTextX(text: String): Int {
        return MID_POINT - getTextLength(text) / 2 + 1
    }

    fun getMappedText(text: String): String {
        return text
            .replace('á', 'a', true)
            .replace('ä', 'a', true)
            .replace('č', 'c', true)
            .replace('ď', 'd', true)
            .replace('é', 'e', true)
            .replace('í', 'i', true)
            .replace('ľ', 'l', true)
            .replace('ĺ', 'l', true)
            .replace('ň', 'n', true)
            .replace('ó', 'o', true)
            .replace('ô', 'o', true)
            .replace('ŕ', 'r', true)
            .replace('š', 's', true)
            .replace('ť', 't', true)
            .replace('ú', 'u', true)
            .replace('ý', 'y', true)
            .replace('ž', 'z', true)
    }

    fun getCharacterPixelWidth(char: Char): Int {
        val lowerChar = toLowerCase(char)
        return when(lowerChar) {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'l', 'o', 'p', 'q', 'r', 's', 'u', 'x', 'y', 'z', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '%' -> 4
            'i', 't', 'v', '-', '_' -> 3
            'm', 'n', 'w', '+' -> 5
            '.', ',', ':', ' ', '!' -> 1
            else -> 0
        }
    }
}