package com.kiko.adaptableglyphtoy.animation

enum class PrimaryToy(val value: Int) {
    Clock(0),
    GameOfLife(1);

    companion object {
        fun fromInt(value: Int): PrimaryToy = entries.find { it.value == value } ?: Clock
    }

    fun next(): PrimaryToy {
        val nextValue = (value + 1) % entries.size
        return fromInt(nextValue)
    }
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