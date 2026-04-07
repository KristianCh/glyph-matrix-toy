package com.kiko.adaptableglyphtoy.animation

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.AUDIO_VISUALIZER_ENABLED_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.AUDIO_VISUALIZER_ROTATION_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.PRIMARY_TOY_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SETTINGS_PREFERENCES_NAME
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SHOW_MEDIA_SCROLL_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SHOW_NOTIFICATION_RING_SETTING_KEY
import com.kiko.adaptableglyphtoy.animation.SettingsConstants.SHOW_NOTIFICATION_SCROLL_SETTING_KEY
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SettingsRepository(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SETTINGS_PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun <T> preferenceFlow(key: String, defaultValue: T, getter: (String, T) -> T): Flow<T> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (key == changedKey) {
                    trySend(getter(key, defaultValue))
                }
            }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            trySend(getter(key, defaultValue))
            awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    val primaryToy: Flow<Int> = preferenceFlow(PRIMARY_TOY_SETTING_KEY, 0) { k, d ->
        sharedPreferences.getInt(k, d)
    }

    val audioVisualizerEnabled: Flow<Boolean> = preferenceFlow(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, false) { k, d ->
        sharedPreferences.getBoolean(k, d)
    }

    val audioVisualizerRotationType: Flow<Int> = preferenceFlow(AUDIO_VISUALIZER_ROTATION_SETTING_KEY, 0) { k, d ->
        sharedPreferences.getInt(k, d)
    }

    val mediaScrollEnabled: Flow<Boolean> = preferenceFlow(SHOW_MEDIA_SCROLL_SETTING_KEY, false) { k, d ->
        sharedPreferences.getBoolean(k, d)
    }

    val notificationRingEnabled: Flow<Boolean> = preferenceFlow(SHOW_NOTIFICATION_RING_SETTING_KEY, false) { k, d ->
        sharedPreferences.getBoolean(k, d)
    }

    val notificationScrollEnabled: Flow<Boolean> = preferenceFlow(SHOW_NOTIFICATION_SCROLL_SETTING_KEY, false) { k, d ->
        sharedPreferences.getBoolean(k, d)
    }

    val notificationBodyEnabled: Flow<Boolean> = preferenceFlow(NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY, false) { k, d ->
        sharedPreferences.getBoolean(k, d)
    }

    val notificationScrollCooldown: Flow<Int> = preferenceFlow(NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY, 0) { k, d ->
        sharedPreferences.getInt(k, d)
    }

    fun setPrimaryToy(toy: Int) = sharedPreferences.edit { putInt(PRIMARY_TOY_SETTING_KEY, toy) }
    fun setAudioVisualizerEnabled(enabled: Boolean) = sharedPreferences.edit { putBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, enabled) }
    fun setAudioVisualizerRotationType(type: Int) = sharedPreferences.edit { putInt(AUDIO_VISUALIZER_ROTATION_SETTING_KEY, type) }
    fun setMediaScrollEnabled(enabled: Boolean) = sharedPreferences.edit { putBoolean(SHOW_MEDIA_SCROLL_SETTING_KEY, enabled) }
    fun setNotificationRingEnabled(enabled: Boolean) = sharedPreferences.edit { putBoolean(SHOW_NOTIFICATION_RING_SETTING_KEY, enabled) }
    fun setNotificationScrollEnabled(enabled: Boolean) = sharedPreferences.edit { putBoolean(SHOW_NOTIFICATION_SCROLL_SETTING_KEY, enabled) }
    fun setNotificationBodyEnabled(enabled: Boolean) = sharedPreferences.edit { putBoolean(NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY, enabled) }
    fun setNotificationScrollCooldown(seconds: Int) = sharedPreferences.edit { putInt(NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY, seconds) }

    // Sync methods for non-flow access if needed
    fun getPrimaryToy() = sharedPreferences.getInt(PRIMARY_TOY_SETTING_KEY, 0)
    fun isAudioVisualizerEnabled() = sharedPreferences.getBoolean(AUDIO_VISUALIZER_ENABLED_SETTING_KEY, false)
    fun getAudioVisualizerRotationType() = sharedPreferences.getInt(AUDIO_VISUALIZER_ROTATION_SETTING_KEY, 0)
    fun isMediaScrollEnabled() = sharedPreferences.getBoolean(SHOW_MEDIA_SCROLL_SETTING_KEY, false)
    fun isNotificationRingEnabled() = sharedPreferences.getBoolean(SHOW_NOTIFICATION_RING_SETTING_KEY, false)
    fun isNotificationScrollEnabled() = sharedPreferences.getBoolean(SHOW_NOTIFICATION_SCROLL_SETTING_KEY, false)
    fun isNotificationBodyEnabled() = sharedPreferences.getBoolean(NOTIFICATION_SCROLL_INCLUDE_BODY_SETTING_KEY, false)
    fun getNotificationScrollCooldown() = sharedPreferences.getInt(NOTIFICATION_SCROLL_REPEAT_TIME_SETTING_KEY, 0)
}
