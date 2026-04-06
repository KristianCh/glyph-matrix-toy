package com.kiko.adaptableglyphtoy.demos.animation.Renderers

import android.content.Context
import com.nothing.ketchum.GlyphMatrixFrame

interface IFrameRenderer {
    fun initialize(context: Context)
    fun dispose()
    fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder
    fun getFrameTime(): Long
    fun canPlay(): Boolean
    fun interact()
}