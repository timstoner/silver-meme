package com.tmstoner.silvermeme.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.tmstoner.silvermeme.R
import com.tmstoner.silvermeme.SilverMemeApplication
import com.tmstoner.silvermeme.data.model.TodoItem
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodoAgendaRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodoAgendaRemoteViewsFactory(
            context = applicationContext,
            appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        )
    }
}

private class TodoAgendaRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private val dueFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private var items: List<TodoItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runBlocking {
            (context.applicationContext as SilverMemeApplication)
                .loadWidgetTodosUseCase()
                .all
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val todo = items.getOrNull(position) ?: return null
        val dueDate = todo.dueDate ?: return null
        val today = LocalDate.now()
        val dueStateRes = if (dueDate.isBefore(today)) {
            R.string.widget_due_overdue
        } else {
            R.string.widget_due_today
        }
        val dueSummary = context.getString(
            R.string.widget_due_format,
            context.getString(dueStateRes),
            dueFormatter.format(dueDate)
        )

        return RemoteViews(context.packageName, R.layout.widget_todo_agenda_item).apply {
            setTextViewText(R.id.widget_todo_title, todo.title)
            setTextViewText(R.id.widget_todo_due, dueSummary)
            setOnClickFillInIntent(
                R.id.widget_todo_item_root,
                Intent(Intent.ACTION_VIEW, TodoWidgetDeepLinks.viewTodoUri(todo.id))
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
