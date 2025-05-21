package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.workers.NotificationWorker

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("AlarmReceiver", "Alarme recebido!")
        if (context == null || intent == null) return

        val lembreteId = intent.getLongExtra("LEMBRETE_ID", -1L)
        val medicamentoId = intent.getStringExtra("MEDICAMENTO_ID") // Importante para atualização de estoque
        val notificationTitle = intent.getStringExtra("NOTIFICATION_TITLE") ?: "Lembrete"
        val notificationMessage = intent.getStringExtra("NOTIFICATION_MESSAGE") ?: "Hora de tomar seu medicamento!"
        val recipientName = intent.getStringExtra("RECIPIENT_NAME") // Para exibir o nome na notificação
        val proximaOcorrenciaMillis = intent.getLongExtra("PROXIMA_OCORRENCIA_MILLIS", -1L)
        val hora = intent.getIntExtra("HORA", -1)
        val minuto = intent.getIntExtra("MINUTO", -1)

        if (lembreteId != -1L) {
            Log.d("AlarmReceiver", "Agendando NotificationWorker para lembreteId: $lembreteId, medicamentoId: $medicamentoId")
            val workData = Data.Builder()
                .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
                .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, medicamentoId) // Crucial para atualização de estoque
                .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, notificationTitle)
                .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, notificationMessage)
                .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, recipientName) // Para personalizar a notificação
                .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, proximaOcorrenciaMillis)
                .putInt(NotificationWorker.EXTRA_HORA, hora)
                .putInt(NotificationWorker.EXTRA_MINUTO, minuto)
                .putBoolean("IS_MEDICINE_ALARM", true) // Flag para indicar que é um alarme de medicamento
                .build()

            val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workData)
                .build()

            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
        } else {
            Log.e("AlarmReceiver", "ID do Lembrete inválido.")
        }
    }
}
