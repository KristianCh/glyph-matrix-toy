package com.nothinglondon.sdkdemo.demos.animation

import android.R.attr.value
import java.sql.Types

enum class PrimaryToy(value: Int) {
    Clock(0),
    GameOfLife(1)
}

enum class AudioVisualizerRotationType(val value: Int) {
    Axis(0),
    Full(1),
    None(2);

    companion object {
        fun fromInt(value: Int): AudioVisualizerRotationType {
            return entries.find { it.value == value } ?: None
        }
    }
}