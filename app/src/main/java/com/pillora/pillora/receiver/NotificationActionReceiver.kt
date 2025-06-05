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

        val action = intent.action
        val lembreteId = intent.getLongExtra(NotificationWorker.EXTRA_LEMBRETE_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationWorker.EXTRA_NOTIFICATION_ID, -1)
        val medicamentoId = intent.getStringExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID)
        val consultaId = intent.getStringExtra(NotificationWorker.EXTRA_CONSULTA_ID)
        val vacinaId = intent.getStringExtra(NotificationWorker.EXTRA_VACINA_ID)
        // Recipe ID is passed via EXTRA_MEDICAMENTO_ID for recipe actions
        val recipeId = if (action == NotificationWorker.ACTION_RECEITA_CONFIRMADA_EXCLUIR) medicamentoId else null

        Log.d("ActionReceiver", "Ação 	'$action'	 recebida para lembreteId: $lembreteId, medId: $medicamentoId, consultaId: $consultaId, vacinaId: $vacinaId, recipeId: $recipeId")

        if (lembreteId == -1L) {
            Log.e("ActionReceiver", "ID do Lembrete inválido na ação.")
            return
        }

        // Remover a notificação
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
            Log.d("ActionReceiver", "Notificação ID $notificationId cancelada.")
        }

        // Delegar para um Worker
        val workDataBuilder = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembreteId)
            .putString("ACTION_TYPE", action)

        // Incluir IDs específicos apenas se não forem nulos
        medicamentoId?.let { workDataBuilder.putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, it) }
        consultaId?.let { workDataBuilder.putString(NotificationWorker.EXTRA_CONSULTA_ID, it) }
        vacinaId?.let { workDataBuilder.putString(NotificationWorker.EXTRA_VACINA_ID, it) }
        // Passar recipeId explicitamente se a ação for de receita
        recipeId?.let { workDataBuilder.putString("EXTRA_RECIPE_ID", it) } // Usando uma chave diferente para clareza no worker

        val workData = workDataBuilder.build()

        val actionWorkRequest = OneTimeWorkRequestBuilder<HandleNotificationActionWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(actionWorkRequest)
        Log.d("ActionReceiver", "HandleNotificationActionWorker agendado para lembreteId: $lembreteId, medId: $medicamentoId, consultaId: $consultaId, vacinaId: $vacinaId, recipeId: $recipeId, ação: 	'$action'	")
    }
}

