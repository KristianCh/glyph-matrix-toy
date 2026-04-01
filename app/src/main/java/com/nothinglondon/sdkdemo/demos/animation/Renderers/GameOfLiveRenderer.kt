package com.nothinglondon.sdkdemo.demos.animation.Renderers

import android.content.Context
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothinglondon.sdkdemo.demos.animation.ArrayModifierApplyMode
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.HEIGHT
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.MAX_BRIGHTNESS
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.WIDTH
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.applyModifierToArray
import com.nothinglondon.sdkdemo.demos.animation.GlyphMatrixUtils.crossFrame
import kotlin.random.Random

class GameOfLiveRenderer: IFrameRenderer {
    var cells = BooleanArray(WIDTH * HEIGHT) { false }
    val cellsNext = BooleanArray(WIDTH * HEIGHT) { false }
    val cellsDisplay = IntArray(WIDTH * HEIGHT) { 0 }
    var noChangeFrames = 0

    override fun initialize(context: Context) {
        initializeInternal()
    }

    override fun dispose() {
        return
    }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
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
                    if (sum < 2 || sum > 3) {
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
        val frameData = GlyphMatrixFrame.Builder()

        if (noChangeFrames <= 10) {
            for (i in 0..<HEIGHT) {
                for (j in 0..<WIDTH) {
                    cellsDisplay[j * WIDTH + i] = if (cells[j * WIDTH + i]) MAX_BRIGHTNESS else 0
                }
            }
            frameData.addTop(applyModifierToArray(cellsDisplay, modifier, ArrayModifierApplyMode.ADD))
        }
        else {
            frameData.addTop(applyModifierToArray(crossFrame, modifier, ArrayModifierApplyMode.ADD))
        }

        return frameData
    }

    override fun getFrameTime(): Long {
        if (noChangeFrames > 10) {
            initializeInternal()
            return 1000
        }
        return 200
    }

    private fun initializeInternal() {
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

    private fun setCell(c: BooleanArray, x: Int, y: Int, value: Boolean) {
        c[y * WIDTH + x] = value
    }

    private fun getCell(x: Int, y: Int): Boolean {
        return cells[y * WIDTH + x]
    }
}