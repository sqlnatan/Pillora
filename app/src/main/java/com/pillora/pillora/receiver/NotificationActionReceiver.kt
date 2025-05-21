package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.workers.HandleNotificationActionWorker
import com.pillora.pillora.workers.NotificationWorker

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null || intent.action == null) return

        val lembreteId = intent.getLongExtra(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationWorker.EXTRA_NOTIFICATION_ID, -1)
        val medicamentoId = intent.getStringExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID)

        Log.d("ActionReceiver", "Ação '${intent.action}' recebida para lembreteId: $lembreteId, medicamentoId: $medicamentoId")

        if (lembreteId == -1L) {
            Log.e("ActionReceiver", "ID do Lembrete inválido na ação.")
            return
        }

        // Remover a notificação
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        // Delegar para um Worker
        val workData = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, medicamentoId) // Crucial para atualização de estoque
            .putString("ACTION_TYPE", intent.action)
            .build()

        val actionWorkRequest = OneTimeWorkRequestBuilder<HandleNotificationActionWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(actionWorkRequest)
        Log.d("ActionReceiver", "HandleNotificationActionWorker agendado para lembreteId: $lembreteId, medicamentoId: $medicamentoId, ação: ${intent.action}")
    }
}
