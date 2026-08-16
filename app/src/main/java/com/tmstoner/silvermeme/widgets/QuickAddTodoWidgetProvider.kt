package com.tmstoner.silvermeme.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.tmstoner.silvermeme.R

class QuickAddTodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_quick_add).apply {
                    setOnClickPendingIntent(
                        R.id.widget_quick_add_button,
                        PendingIntent.getActivity(
                            context,
                            1000 + appWidgetId,
                            TodoWidgetDestination.NewTodo.toIntent(context),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
