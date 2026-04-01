package com.nothinglondon.sdkdemo.demos.animation

object GlyphMatrixUtils {
    const val WIDTH = 13
    const val HEIGHT = 13
    const val HALF_HEIGHT = HEIGHT.toDouble() / 2
    const val MID_POINT = HEIGHT / 2

    const val CHARACTER_WIDTH = 4
    const val TOP_LINE = 0
    const val BOTTOM_LINE = 6
    const val CHARACTER_SEPARATOR_WIDTH = 2
    const val MAX_BRIGHTNESS = 4096
    private val I = 255

    val notificationFrame = arrayOf(
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

    fun getCenteredTextX(text: String): Int {
        return MID_POINT - getTextLength(text) / 2 + 1
    }
}