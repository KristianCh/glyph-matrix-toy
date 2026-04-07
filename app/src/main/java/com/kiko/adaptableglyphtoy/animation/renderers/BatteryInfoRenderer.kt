package com.kiko.adaptableglyphtoy.animation.renderers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.snapshots.toInt
import com.kiko.adaptableglyphtoy.animation.ArrayModifierApplyMode
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.BOTTOM_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.HEIGHT
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.MID_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.TOP_LINE
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.WIDTH
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.applyModifierToArray
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.batteryFrame
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.formatTime
import com.kiko.adaptableglyphtoy.animation.GlyphMatrixUtils.getCenteredTextX
import com.kiko.adaptableglyphtoy.animation.ToyAnimationService.Companion.batteryManager
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject
import java.time.LocalDateTime

class BatteryInfoRenderer: IFrameRenderer {
    private val LOG_TAG = BatteryInfoRenderer::class.java.simpleName
    private var showRemainingTimeForFrames = 0
    private var context: Context? = null
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateChargingStatus(intent)
        }
    }

    override fun initialize(context: Context) {
        this.context = context
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(batteryReceiver, intentFilter)
        updateChargingStatus(stickyIntent)
    }

    override fun dispose() {
        try {
            context?.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore unregister errors
        }
        this.context = null
    }

    private fun updateChargingStatus(intent: Intent?) {
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun getFrameData(modifier: IntArray?): GlyphMatrixFrame.Builder {

        val currentCharge = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val text = currentCharge.toString()

        var array = applyModifierToArray(batteryFrame, modifier, ArrayModifierApplyMode.ADD)
        array = applyModifierToArray(array, getDisplay(currentCharge), ArrayModifierApplyMode.ADD)

        val builder = GlyphMatrixFrame.Builder().addLow(array)
        val res = batteryManager.computeChargeTimeRemaining()

        if (showRemainingTimeForFrames > 0) {
            val resHours = formatTime((res / 3600000).toInt())
            val resMinutes = formatTime(((res % 3600000) / 60000).toInt())
            showRemainingTimeForFrames--

            if (res > 0) {
                val textObjectTop = GlyphMatrixObject.Builder().setText(resHours)
                    .setPosition(getCenteredTextX(resHours), TOP_LINE)
                    .setBrightness(255)
                    .build()
                val textObjectBot = GlyphMatrixObject.Builder().setText(resMinutes)
                    .setPosition(getCenteredTextX(resMinutes), BOTTOM_LINE)
                    .setBrightness(255)
                    .build()
                builder.addTop(textObjectTop).addMid(textObjectBot)
            }
            else {
                val textObject = GlyphMatrixObject.Builder().setText("-")
                    .setPosition(getCenteredTextX("-"), MID_LINE)
                    .setBrightness(255)
                    .build()
                builder.addTop(textObject)
            }
        }
        else {
            val textObject = GlyphMatrixObject.Builder().setText(text)
                .setPosition(getCenteredTextX(text), MID_LINE)
                .setBrightness(255)
                .build()
            builder.addTop(textObject)
        }

        return builder
    }

    override fun getFrameTime(): Long {
        return 1000
    }

    override fun canPlay(): Boolean {
        return isCharging
    }

    override fun interact() {
        showRemainingTimeForFrames = 3
    }

    private fun getDisplay(current: Int): IntArray {
        val steps = 7
        val stepsSize = 100 / steps

        val step = current / 14

        val midStep = (current % 14) < 7
        val display = IntArray(WIDTH * HEIGHT)
        for (s in 10-step..10) {
            for (x in 4 .. 9) {
                display[s * WIDTH + x] = 750
            }
        }
        val second = LocalDateTime.now().second
        if (midStep) {
            display[(9 - step) * WIDTH + 5] = 750
            display[(9 - step) * WIDTH + 7] = 750
        }
        if (second % 2 == 0) {
            display[(9 - step) * WIDTH + 4] = 350
            display[(9 - step) * WIDTH + 6] = 350
            display[(9 - step) * WIDTH + 8] = 350
        }

        return display
    }
}
