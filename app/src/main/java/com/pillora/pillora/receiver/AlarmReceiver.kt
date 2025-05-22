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
        val observacao = intent.getStringExtra("OBSERVACAO") ?: ""

        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: lembreteId=$lembreteId, medicamentoId=$medicamentoId, title=$notificationTitle, message=$notificationMessage")
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: hora=$hora, minuto=$minuto, observacao=$observacao")
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: proximaOcorrencia=${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(proximaOcorrenciaMillis))}")

        // Verificar se é uma consulta pelo título
        val isConsulta = notificationTitle.contains("Consulta:", ignoreCase = true)

        // Extrair especialidade da consulta (se for consulta)
        val especialidade = if (isConsulta) {
            notificationTitle.replace("Hora de: Consulta:", "", ignoreCase = true).trim()
        } else {
            ""
        }

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
                .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, isConsulta)
                .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, notificationMessage)
                .putString(NotificationWorker.EXTRA_ESPECIALIDADE, especialidade)
                .putString("EXTRA_OBSERVACAO", observacao)
                .build()

            val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workData)
                .build()

            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: NotificationWorker agendado com sucesso")
        } else {
            Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: Erro - lembreteId inválido")
        }
    }
}
