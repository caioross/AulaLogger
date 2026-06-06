package com.aulalogger.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.aulalogger.MainActivity
import com.aulalogger.R
import com.aulalogger.recording.RecordingController
import com.aulalogger.recording.RecordingService
import java.util.UUID

class AulaLoggerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (RecordingController.activeSessionId != null) {
                    RecordingController.stop(context)
                } else {
                    // NEW-004: sem RECORD_AUDIO concedida, iniciar FGS tipo
                    // microphone causa SecurityException em Android 14+. Abre
                    // a Activity para o usuário conceder permissão em vez de
                    // crashar silenciosamente em background.
                    val micGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!micGranted) {
                        try {
                            Toast.makeText(
                                context,
                                "Permita o microfone antes de gravar pelo widget",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (_: Throwable) {}
                        val open = Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(open) } catch (_: Throwable) {}
                    } else {
                        val id = UUID.randomUUID().toString()
                        RecordingController.start(context, id)
                    }
                }
                refreshAll(context)
            }
            ACTION_PAUSE -> {
                if (RecordingController.isPaused) RecordingController.resume(context)
                else RecordingController.pause(context)
                refreshAll(context)
            }
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_aulalogger)

        val isRecording = RecordingController.activeSessionId != null
        val isPaused = RecordingController.isPaused

        val statusText = context.getString(
            when {
                !isRecording -> R.string.widget_status_ready
                isPaused -> R.string.widget_status_paused
                else -> R.string.widget_status_recording
            }
        )
        views.setTextViewText(R.id.widget_status, statusText)
        views.setTextViewText(R.id.widget_button_main,
            context.getString(if (isRecording) R.string.widget_action_stop else R.string.widget_action_record)
        )
        views.setTextViewText(R.id.widget_button_pause,
            context.getString(if (isPaused) R.string.widget_action_resume else R.string.widget_action_pause)
        )
        views.setViewVisibility(
            R.id.widget_button_pause,
            if (isRecording) android.view.View.VISIBLE else android.view.View.GONE
        )

        // Ações
        val toggleIntent = Intent(context, AulaLoggerWidget::class.java).setAction(ACTION_TOGGLE)
        val togglePI = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_button_main, togglePI)

        val pauseIntent = Intent(context, AulaLoggerWidget::class.java).setAction(ACTION_PAUSE)
        val pausePI = PendingIntent.getBroadcast(
            context, 1, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_button_pause, pausePI)

        // Tap no widget abre o app
        val openApp = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_TOGGLE = "com.aulalogger.widget.TOGGLE"
        const val ACTION_PAUSE = "com.aulalogger.widget.PAUSE"
        const val ACTION_REFRESH = "com.aulalogger.widget.REFRESH"

        /** Chamado pelo serviço quando o estado muda, para refletir no widget. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AulaLoggerWidget::class.java))
            if (ids.isEmpty()) return
            val provider = AulaLoggerWidget()
            ids.forEach { id -> provider.updateAppWidget(context, mgr, id) }
        }
    }
}
