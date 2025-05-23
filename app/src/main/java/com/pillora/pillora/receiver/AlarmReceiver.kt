package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.workers.NotificationWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: Alarme recebido!")

        // Extrair dados do intent
        val lembreteId = intent.getLongExtra("LEMBRETE_ID", -1L)
        val medicamentoId = intent.getStringExtra("MEDICAMENTO_ID") ?: ""
        val title = intent.getStringExtra("NOTIFICATION_TITLE") ?: "Lembrete"
        val message = intent.getStringExtra("NOTIFICATION_MESSAGE") ?: ""
        val recipientName = intent.getStringExtra("RECIPIENT_NAME") ?: ""
        val proximaOcorrenciaMillis = intent.getLongExtra("PROXIMA_OCORRENCIA_MILLIS", 0L)
        val hora = intent.getIntExtra("HORA", -1)
        val minuto = intent.getIntExtra("MINUTO", -1)
        val observacao = intent.getStringExtra("OBSERVACAO") ?: ""

        // Obter o horário real da consulta (se for uma consulta)
        val horaConsulta = intent.getIntExtra("HORA_CONSULTA", -1)
        val minutoConsulta = intent.getIntExtra("MINUTO_CONSULTA", -1)
        val isConsultaAlarm = intent.getBooleanExtra("IS_CONSULTATION_ALARM", false)

        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: lembreteId=$lembreteId, medicamentoId=$medicamentoId, title=$title, message=$message")
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: hora=$hora, minuto=$minuto, observacao=$observacao")
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: horaConsulta=$horaConsulta, minutoConsulta=$minutoConsulta")

        // Formatar a próxima ocorrência para log
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val proximaOcorrenciaFormatada = dateFormat.format(Date(proximaOcorrenciaMillis))
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: proximaOcorrencia=$proximaOcorrenciaFormatada")

        // Preparar dados para o WorkManager
        val workData = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, medicamentoId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, title)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, message)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, minuto)
            .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, isConsultaAlarm)

        // Passar o horário real da consulta se disponível
        if (horaConsulta >= 0 && minutoConsulta >= 0) {
            workData.putInt(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsulta)
            workData.putInt(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsulta)
        }

        // Criar e agendar o trabalho
        val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData.build())
            .build()

        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: Agendando NotificationWorker para lembreteId: $lembreteId")
        WorkManager.getInstance(context).enqueue(notificationWorkRequest)
        Log.e("PILLORA_DEBUG", "AlarmReceiver.onReceive: NotificationWorker agendado com sucesso")
    }
}
