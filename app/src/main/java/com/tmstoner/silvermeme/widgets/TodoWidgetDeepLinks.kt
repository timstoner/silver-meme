package com.tmstoner.silvermeme.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tmstoner.silvermeme.MainActivity

object TodoWidgetDeepLinks {
    const val ACTION_REFRESH_WIDGETS = "com.tmstoner.silvermeme.widgets.action.REFRESH_WIDGETS"

    private const val SCHEME = "silvermeme"
    private const val HOST = "todo"

    fun todoListUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendPath("list")
        .build()

    fun newTodoUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendPath("new")
        .build()

    fun viewTodoUri(todoId: String): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendPath("view")
        .appendQueryParameter("todoId", todoId)
        .build()

    fun launchIntent(context: Context, uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun activityPendingIntent(
        context: Context,
        uri: Uri,
        requestCode: Int,
        mutable: Boolean = false
    ): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent(context, uri),
            flags
        )
    }

    fun refreshWidgets(context: Context) {
        context.sendBroadcast(
            Intent(context, QuickCreateWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGETS
            }
        )
        context.sendBroadcast(
            Intent(context, TodoAgendaWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGETS
            }
        )
    }
}
