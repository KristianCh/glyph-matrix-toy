package com.kiko.adaptableglyphtoy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kiko.adaptableglyphtoy.animation.ToyAnimationService

class CycleToyWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: IntArray
    ) {
        for (id in appWidgetId) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_cycle)
        
        val intent = Intent(context, ToyAnimationService::class.java).apply {
            action = ToyAnimationService.ACTION_CYCLE_TOY
        }
        
        val pendingIntent = PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
