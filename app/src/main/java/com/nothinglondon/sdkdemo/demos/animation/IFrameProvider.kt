package com.nothinglondon.sdkdemo.demos.animation

import com.nothing.ketchum.GlyphMatrixFrame

interface IFrameProvider {
    fun initialize()
    fun dispose()
    fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder
    fun getFrameTime(): Long
    fun canPlay(): Boolean
}