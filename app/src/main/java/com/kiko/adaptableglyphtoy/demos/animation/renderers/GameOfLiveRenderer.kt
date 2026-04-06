package com.kiko.adaptableglyphtoy.demos.animation.renderers

import android.content.Context
import com.kiko.adaptableglyphtoy.demos.animation.ArrayModifierApplyMode
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.HEIGHT
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.MAX_BRIGHTNESS
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.WIDTH
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.applyModifierToArray
import com.kiko.adaptableglyphtoy.demos.animation.GlyphMatrixUtils.crossFrame
import com.nothing.ketchum.GlyphMatrixFrame
import kotlin.collections.toIntArray
import kotlin.random.Random

class GameOfLiveRenderer: IFrameRenderer {
    var cells = BooleanArray(WIDTH * HEIGHT) { false }
    val cellsNext = BooleanArray(WIDTH * HEIGHT) { false }
    var cellsDisplay = IntArray(WIDTH * HEIGHT)
    var noChangeFrames = 0
    var failed = false

    override fun initialize(context: Context) {
        initializeInternal()
    }

    override fun dispose() {
        return
    }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
        val frameData = GlyphMatrixFrame.Builder()
        if (noChangeFrames > 10) {
            failed = true
            frameData.addTop(applyModifierToArray(crossFrame, modifier, ArrayModifierApplyMode.ADD))
            return frameData
        }

        noChangeFrames++
        for (i in 0..<HEIGHT) {
            for (j in 0..<WIDTH) {
                var sum = 0
                for (nY in -1..<2) {
                    for (nX in -1..<2) {
                        if (nY == 0 && nX == 0)
                            continue
                        var x = j + nX
                        if (x < 0) x += WIDTH
                        if (x >= WIDTH) x -= WIDTH
                        var y = i + nY
                        if (y < 0) y += HEIGHT
                        if (y >= HEIGHT) y -= HEIGHT
                        sum += if (getCell(x, y)) 1 else 0
                    }
                }
                val current = getCell(j, i)
                if (getCell(j, i)) {
                    if (sum !in 2..3) {
                        setCell(cellsNext, j, i, false)
                        noChangeFrames = 0
                    }
                    else {
                        setCell(cellsNext, j, i, current)
                    }
                }
                else if (sum == 3) {
                    setCell(cellsNext, j, i, true)
                    noChangeFrames = 0
                }
            }
        }
        cells = cellsNext.clone()
        cellsDisplay = cells.map { c -> if (c) MAX_BRIGHTNESS else 0 }.toIntArray()
        frameData.addTop(applyModifierToArray(cellsDisplay, modifier, ArrayModifierApplyMode.ADD))

        return frameData
    }

    override fun getFrameTime(): Long {
        if (failed) {
            initializeInternal()
            return 1000
        }
        return 200
    }

    private fun initializeInternal() {
        failed = false
        noChangeFrames = 0
        for (i in 0 ..< HEIGHT) {
            for (j in 0 ..< WIDTH) {
                setCell(cells, j, i, Random.nextBoolean())
            }
        }
    }

    override fun canPlay(): Boolean {
        return true
    }

    override fun interact() {
        noChangeFrames = 11
    }

    private fun setCell(c: BooleanArray, x: Int, y: Int, value: Boolean) {
        c[y * WIDTH + x] = value
    }

    private fun getCell(x: Int, y: Int): Boolean {
        return cells[y * WIDTH + x]
    }
}