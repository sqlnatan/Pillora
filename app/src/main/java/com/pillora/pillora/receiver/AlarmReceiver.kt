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

        // --- CORREÇÃO: Extrair TODOS os dados do intent, incluindo os de vacina ---
        val lembreteId = intent.getLongExtra(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = intent.getStringExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID) ?: ""
        val consultaId = intent.getStringExtra(NotificationWorker.EXTRA_CONSULTA_ID) ?: medicamentoId
        val vacinaId = intent.getStringExtra(NotificationWorker.EXTRA_VACINA_ID) ?: medicamentoId
        val title = intent.getStringExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val message = intent.getStringExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE) ?: ""
        val recipientName = intent.getStringExtra(NotificationWorker.EXTRA_RECIPIENT_NAME) ?: ""
        val proximaOcorrenciaMillis = intent.getLongExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, 0L)
        val hora = intent.getIntExtra(NotificationWorker.EXTRA_HORA, -1)
        val minuto = intent.getIntExtra(NotificationWorker.EXTRA_MINUTO, -1)
        val horaConsulta = intent.getIntExtra(NotificationWorker.EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = intent.getIntExtra(NotificationWorker.EXTRA_MINUTO_CONSULTA, -1)
        val horaVacina = intent.getStringExtra(NotificationWorker.EXTRA_VACCINE_TIME) ?: ""
        val nomeVacina = intent.getStringExtra(NotificationWorker.EXTRA_VACCINE_NAME) ?: ""
        val isConsultaAlarm = intent.getBooleanExtra(NotificationWorker.EXTRA_IS_CONSULTA, false)
        val isVacinaAlarm = intent.getBooleanExtra(NotificationWorker.EXTRA_IS_VACINA, false)
        val isConfirmacaoAlarm = intent.getBooleanExtra(NotificationWorker.EXTRA_IS_CONFIRMACAO, false) // Geralmente será false aqui
        val tipoLembrete = intent.getStringExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE) ?: "tipo_desconhecido"
        //val isSilencioso = intent.getBooleanExtra(NotificationWorker.EXTRA_IS_SILENCIOSO, false) // NOVO
        //val toqueAlarmeUri = intent.getStringExtra(NotificationWorker.EXTRA_TOQUE_ALARME_URI) // NOVO
        // --------------------------------------------------------------------------

        Log.d("AlarmReceiver", "Recebido: lembreteId=$lembreteId, tipo=$tipoLembrete, title=$title, message=$message")
        Log.d("AlarmReceiver", "Recebido: isConsulta=$isConsultaAlarm, isVacina=$isVacinaAlarm, isConfirmacao=$isConfirmacaoAlarm")
        Log.d("AlarmReceiver", "Recebido: horaConsulta=$horaConsulta:$minutoConsulta, horaVacina=$horaVacina, nomeVacina=$nomeVacina")

        // Verificar se o tipo de lembrete é válido para alarme (não deveria ser confirmação)
        if (isConfirmacaoAlarm || tipoLembrete == DateTimeUtils.TIPO_CONFIRMACAO) {
            Log.e("AlarmReceiver", "ERRO! Recebeu alarme para lembrete de confirmação (ID: $lembreteId, Tipo: $tipoLembrete). Isso deveria ser tratado pelo WorkManager. Ignorando.")
            return
        }
        if (tipoLembrete == "tipo_desconhecido") {
            Log.w("AlarmReceiver", "Tipo de lembrete desconhecido para ID: $lembreteId. Verifique a passagem de parâmetros.")
        }

        // --- CORREÇÃO: Preparar dados para o WorkManager incluindo os de vacina ---
        val workDataBuilder = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, title)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, message)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, minuto)
            .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, tipoLembrete)
            .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, false) // Alarme nunca é de confirmação
            //.putBoolean(NotificationWorker.EXTRA_IS_SILENCIOSO, isSilencioso) // NOVO
            //.putString(NotificationWorker.EXTRA_TOQUE_ALARME_URI, toqueAlarmeUri) // NOVO

        // Adicionar dados específicos de Consulta OU Vacina OU Medicamento
        if (isConsultaAlarm) {
            workDataBuilder.putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, true)
            workDataBuilder.putString(NotificationWorker.EXTRA_CONSULTA_ID, consultaId)
            workDataBuilder.putInt(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsulta)
            workDataBuilder.putInt(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsulta)
        } else if (isVacinaAlarm) {
            workDataBuilder.putBoolean(NotificationWorker.EXTRA_IS_VACINA, true)
            workDataBuilder.putString(NotificationWorker.EXTRA_VACINA_ID, vacinaId)
            workDataBuilder.putString(NotificationWorker.EXTRA_VACCINE_NAME, nomeVacina)
            workDataBuilder.putString(NotificationWorker.EXTRA_VACCINE_TIME, horaVacina)
        } else {
            // Se não for consulta nem vacina, assume que é medicamento
            workDataBuilder.putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, medicamentoId)
            // Flags isConsulta e isVacina serão false por padrão
        }

        val workData = workDataBuilder.build()
        // ----------------------------------------------------------------------

        // Criar e agendar o trabalho
        val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .addTag("alarm_lembrete_${lembreteId}") // Tag genérica para cancelamento
            .build()

        Log.d("AlarmReceiver", "Agendando NotificationWorker para lembreteId: $lembreteId, Tipo: $tipoLembrete, isConsulta=$isConsultaAlarm, isVacina=$isVacinaAlarm")
        try {
            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            Log.d("AlarmReceiver", "NotificationWorker agendado com sucesso via AlarmReceiver")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Erro ao agendar NotificationWorker via AlarmReceiver", e)
        }
    }
}
