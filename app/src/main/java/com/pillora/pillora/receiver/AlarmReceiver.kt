package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.workers.NotificationWorker
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: Alarme recebido!")
        if (context == null || intent == null) {
            Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: context ou intent nulos")
            return
        }

        val lembreteId = intent.getLongExtra("LEMBRETE_ID", -1L)
        val medicamentoId = intent.getStringExtra("MEDICAMENTO_ID") // Importante para atualização de estoque
        val notificationTitle = intent.getStringExtra("NOTIFICATION_TITLE") ?: "Lembrete"
        val notificationMessage = intent.getStringExtra("NOTIFICATION_MESSAGE") ?: "Hora de tomar seu medicamento!"
        val recipientName = intent.getStringExtra("RECIPIENT_NAME") // Para exibir o nome na notificação
        val proximaOcorrenciaMillis = intent.getLongExtra("PROXIMA_OCORRENCIA_MILLIS", -1L)
        val hora = intent.getIntExtra("HORA", -1)
        val minuto = intent.getIntExtra("MINUTO", -1)

        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: lembreteId=$lembreteId, medicamentoId=$medicamentoId, title=$notificationTitle, message=$notificationMessage")
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: proximaOcorrencia=${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(proximaOcorrenciaMillis))}")


        if (lembreteId != -1L) {
            Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: Agendando NotificationWorker para lembreteId: $lembreteId")
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
            Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: NotificationWorker agendado com sucesso")
        }
    }
}
