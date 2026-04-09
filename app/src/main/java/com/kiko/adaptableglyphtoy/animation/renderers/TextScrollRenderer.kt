package com.kiko.adaptableglyphtoy.animation.renderers

import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.MID_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.WIDTH
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getMappedText
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getTextLength
import com.kiko.adaptableglyphtoy.animation.ToyAnimationService

class TextScrollRenderer : IFrameRenderer {
    companion object {
        private const val BASE_POSITION = WIDTH + 4
    }

    private var onFinishedCallback: (() -> Unit)? = null
    private var currentDisplayText: String? = null
    private var textPosition = BASE_POSITION
    private var textLength = 0

    override fun initialize(context: ToyAnimationService) {}

    override fun dispose() {
        clear()
    }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {
        val frameToDraw = GlyphMatrixFrame.Builder()
        if (currentDisplayText != null) {
            val textObject = GlyphMatrixObject.Builder().setText(currentDisplayText)
                .setPosition(textPosition, MID_LINE)
                .setBrightness(255)
                .build()

            frameToDraw.addTop(textObject)
        }
        if (textPosition > -textLength) {
            textPosition --
        }
        else {
            Log.i("TextScrollRenderer", "Finished scroll")
            onFinishedCallback?.invoke()
            clear()
        }

        return frameToDraw
    }

    override fun getFrameTime(): Long {
        return 50
    }

    override fun canPlay(): Boolean {
        return currentDisplayText != null
    }

    override fun interact() {
        textPosition = -textLength
    }

    fun tryStartScroll(string: String, onFinished: () -> Unit): Boolean {
        Log.i("TextScrollRenderer", "Try start scroll")

        currentDisplayText = getMappedText(string)
        currentDisplayText?.length?.let {
            if (it > 100)
                currentDisplayText = currentDisplayText?.substring(0, 100)
        }
        textLength = getTextLength(currentDisplayText ?: "")
        onFinishedCallback = onFinished
        textPosition = BASE_POSITION

        Log.i("TextScrollRenderer", "L: $textLength T: $currentDisplayText")

        return true
    }

    fun clear() {
        currentDisplayText = null
        onFinishedCallback = null
    }
}