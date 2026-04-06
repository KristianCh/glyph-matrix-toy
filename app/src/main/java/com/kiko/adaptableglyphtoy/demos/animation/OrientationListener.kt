package com.kiko.adaptableglyphtoy.demos.animation

import android.content.Context
import android.view.OrientationEventListener

enum class Orientation(val value: Int) {
    PORTRAIT_UP(0),
    PORTRAIT_DOWN(180),
    LANDSCAPE_LEFT(90),
    LANDSCAPE_RIGHT(270)
}

class OrientationListener(
    context: Context,
    rate: Int,
    private val onOrientationChangedCallback: (Orientation) -> Unit,
    private val onAngleChangedCallback: (Int) -> Unit
) : OrientationEventListener(context, rate) {

    private var lastRotation = Orientation.PORTRAIT_UP
    private var lastAngle = -1

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return

        val rotation = when (orientation) {
            in 45..134 -> Orientation.LANDSCAPE_RIGHT
            in 135..224 -> Orientation.PORTRAIT_DOWN
            in 225..314 -> Orientation.LANDSCAPE_LEFT
            else -> Orientation.PORTRAIT_UP
        }



        if (orientation != lastAngle) {
            lastAngle = orientation
            onAngleChangedCallback(orientation)
        }

        if (rotation != lastRotation) {
            lastRotation = rotation
            onOrientationChangedCallback(rotation)
        }
    }
}