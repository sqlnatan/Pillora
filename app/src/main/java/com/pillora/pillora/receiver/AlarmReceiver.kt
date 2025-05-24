package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.workers.NotificationWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "AlarmReceiver.onReceive: Alarme recebido!")

        // Extrair dados do intent
        val lembreteId = intent.getLongExtra(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = intent.getStringExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID) ?: ""
        val title = intent.getStringExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val message = intent.getStringExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE) ?: "" // Observação da consulta
        val recipientName = intent.getStringExtra(NotificationWorker.EXTRA_RECIPIENT_NAME) ?: ""
        val proximaOcorrenciaMillis = intent.getLongExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, 0L)
        val hora = intent.getIntExtra(NotificationWorker.EXTRA_HORA, -1)
        val minuto = intent.getIntExtra(NotificationWorker.EXTRA_MINUTO, -1)
        val horaConsulta = intent.getIntExtra(NotificationWorker.EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = intent.getIntExtra(NotificationWorker.EXTRA_MINUTO_CONSULTA, -1)
        val isConsultaAlarm = intent.getBooleanExtra("IS_CONSULTATION_ALARM", false)

        // Obter o tipo de lembrete EXPLICITAMENTE
        val tipoLembrete = intent.getStringExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE) ?: "tipo_desconhecido"

        Log.d("AlarmReceiver", "Recebido: lembreteId=$lembreteId, tipo=$tipoLembrete, title=$title, message=$message")
        Log.d("AlarmReceiver", "Recebido: horaConsulta=$horaConsulta, minutoConsulta=$minutoConsulta, isConsulta=$isConsultaAlarm")

        // Verificar se o tipo de lembrete é válido para alarme (não deveria ser 3h depois)
        if (tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
            Log.e("AlarmReceiver", "ERRO! Recebeu alarme para lembrete '${DateTimeUtils.TIPO_3H_DEPOIS}' (ID: $lembreteId). Isso deveria ser tratado pelo WorkManager. Ignorando.")
            return // Não agendar worker para lembrete pós-consulta via AlarmManager
        }
        if (tipoLembrete == "tipo_desconhecido") {
            Log.w("AlarmReceiver", "Tipo de lembrete desconhecido para ID: $lembreteId. Verifique a passagem de parâmetros.")
            // Continuar mesmo assim, mas logar o aviso
        }

        // Preparar dados para o WorkManager
        val workData = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, medicamentoId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, title)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, message) // Passar a observação
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, minuto)
            .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, isConsultaAlarm)
            .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, tipoLembrete) // Passar o tipo EXPLICITAMENTE
            .putInt(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsulta)
            .putInt(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsulta)
            .build()

        // Criar e agendar o trabalho
        val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .addTag("alarm_consulta_${lembreteId}") // Tag para possível cancelamento futuro
            .build()

        Log.d("AlarmReceiver", "Agendando NotificationWorker para lembreteId: $lembreteId, Tipo: $tipoLembrete")
        try {
            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            Log.d("AlarmReceiver", "NotificationWorker agendado com sucesso via AlarmReceiver")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Erro ao agendar NotificationWorker via AlarmReceiver", e)
        }
    }
}
