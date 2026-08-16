package com.tmstoner.silvermeme.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tmstoner.silvermeme.R

class TodoAgendaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_list)
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
                ComponentName(context, TodoAgendaWidgetProvider::class.java)
            )
            if (appWidgetIds.isEmpty()) return

            appWidgetIds.forEach { appWidgetId ->
                updateAppWidget(context, manager, appWidgetId)
            }
            manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_todo_list)
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val serviceIntent = Intent(context, TodoAgendaRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = TodoWidgetDeepLinks.todoListUri().buildUpon()
                    .appendQueryParameter("appWidgetId", appWidgetId.toString())
                    .build()
            }

            val templateIntent = TodoWidgetDeepLinks.launchIntent(context, TodoWidgetDeepLinks.todoListUri())
            val templatePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TodoAgendaWidgetProvider::class.java).apply {
                    action = TodoWidgetDeepLinks.ACTION_REFRESH_WIDGETS
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val newTodoPendingIntent = TodoWidgetDeepLinks.activityPendingIntent(
                context = context,
                uri = TodoWidgetDeepLinks.newTodoUri(),
                requestCode = appWidgetId + 10_000
            )

            val views = RemoteViews(context.packageName, R.layout.widget_todo_agenda).apply {
                setRemoteAdapter(R.id.widget_todo_list, serviceIntent)
                setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
                setPendingIntentTemplate(R.id.widget_todo_list, templatePendingIntent)
                setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
                setOnClickPendingIntent(R.id.widget_add_button, newTodoPendingIntent)
                setOnClickPendingIntent(
                    R.id.widget_agenda_header,
                    TodoWidgetDeepLinks.activityPendingIntent(
                        context = context,
                        uri = TodoWidgetDeepLinks.todoListUri(),
                        requestCode = appWidgetId + 20_000
                    )
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
