package com.kiko.adaptableglyphtoy.animation

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log

class MediaControllerCallback: MediaController.Callback() {
    var songInfo: String = ""
    var albumArt: Bitmap? = null
    var playbackState = PlaybackState.STATE_NONE

    private var callback: (() -> Unit)? = null

    private val LOG_TAG = MediaControllerCallback::class.java.simpleName


    override fun onPlaybackStateChanged(state: PlaybackState?) {
        super.onPlaybackStateChanged(state)

        if (state == null || state.state == playbackState) return

        playbackState = state.state
        callback?.invoke()

        Log.i(LOG_TAG, "Playback state changed: $state")
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        super.onMetadataChanged(metadata)
        val newString = "${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)} - ${metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)}"

        if (songInfo == newString) return

        albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        songInfo = newString

        callback?.invoke()

        Log.i(LOG_TAG, "Metadata changed: ${songInfo}")
    }

    fun setCallback(action: () -> Unit) {
        callback = action
    }
}