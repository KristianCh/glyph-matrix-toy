package com.nothinglondon.sdkdemo.demos.animation

interface IFrameProvider {
    fun initialize()
    fun dispose()
    fun getFrameData(modifier: IntArray?): IntArray
    fun getFrameTime(): Long
    fun canPlay(): Boolean
}