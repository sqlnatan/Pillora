package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.MainActivity // Keep this import if needed elsewhere, though not directly used here for navigation anymore
import com.pillora.pillora.workers.HandleNotificationActionWorker
import com.pillora.pillora.workers.NotificationWorker

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null || intent.action == null) return

        // Extract all relevant IDs from the intent
        val lembreteId = intent.getLongExtra(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationWorker.EXTRA_NOTIFICATION_ID, -1)
        val medicamentoId = intent.getStringExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID)
        val consultaId = intent.getStringExtra(NotificationWorker.EXTRA_CONSULTA_ID)
        // *** CORREÇÃO: Extrair o ID da vacina ***
        val vacinaId = intent.getStringExtra(NotificationWorker.EXTRA_VACINA_ID)

        // *** CORREÇÃO: Incluir vacinaId no log ***
        Log.d("ActionReceiver", "Ação '	${intent.action}	' recebida para lembreteId: $lembreteId, medId: $medicamentoId, consultaId: $consultaId, vacinaId: $vacinaId")

        if (lembreteId == -1L) {
            Log.e("ActionReceiver", "ID do Lembrete inválido na ação.")
            return
        }

        // Remover a notificação
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        // A navegação para edição (consulta/vacina) agora é feita pelo PendingIntent.getActivity na notificação

        // Delegar para um Worker
        val workDataBuilder = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
            .putString("ACTION_TYPE", intent.action)

        // *** CORREÇÃO: Incluir IDs específicos apenas se não forem nulos ***
        medicamentoId?.let { workDataBuilder.putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, it) }
        consultaId?.let { workDataBuilder.putString(NotificationWorker.EXTRA_CONSULTA_ID, it) }
        vacinaId?.let { workDataBuilder.putString(NotificationWorker.EXTRA_VACINA_ID, it) } // Passar vacinaId

        val workData = workDataBuilder.build()

        val actionWorkRequest = OneTimeWorkRequestBuilder<HandleNotificationActionWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(actionWorkRequest)
        // *** CORREÇÃO: Incluir vacinaId no log de agendamento ***
        Log.d("ActionReceiver", "HandleNotificationActionWorker agendado para lembreteId: $lembreteId, medId: $medicamentoId, consultaId: $consultaId, vacinaId: $vacinaId, ação: 	${intent.action}	")
    }
}

