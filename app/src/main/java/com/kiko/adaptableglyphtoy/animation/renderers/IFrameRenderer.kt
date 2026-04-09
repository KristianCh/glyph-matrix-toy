package com.kiko.adaptableglyphtoy.animation.renderers

import android.content.Context
import com.kiko.adaptableglyphtoy.animation.ToyAnimationService
import com.nothing.ketchum.GlyphMatrixFrame

interface IFrameRenderer {
    fun initialize(context: ToyAnimationService)
    fun dispose()
    fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder
    fun getFrameTime(): Long
    fun canPlay(): Boolean
    fun interact()
}