package com.tmstoner.silvermeme.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tmstoner.silvermeme.R

class QuickCreateWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TodoWidgetDeepLinks.ACTION_REFRESH_WIDGETS) {
            refreshWidgets(context)
        }
    }

    companion object {
        fun refreshWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val appWidgetIds = manager.getAppWidgetIds(
                ComponentName(context, QuickCreateWidgetProvider::class.java)
            )
            appWidgetIds.forEach { appWidgetId ->
                updateAppWidget(context, manager, appWidgetId)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_create).apply {
                val newTodoPendingIntent = TodoWidgetDeepLinks.activityPendingIntent(
                    context = context,
                    uri = TodoWidgetDeepLinks.newTodoUri(),
                    requestCode = appWidgetId
                )
                setOnClickPendingIntent(R.id.widget_quick_create_root, newTodoPendingIntent)
                setOnClickPendingIntent(R.id.widget_quick_create_button, newTodoPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
