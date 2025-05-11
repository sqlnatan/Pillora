package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pillora.pillora.workers.NotificationWorker // Criaremos este worker

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("AlarmReceiver", "Alarme recebido!")
        if (context == null || intent == null) return

        val lembreteId = intent.getLongExtra("LEMBRETE_ID", -1L)
        val notificationTitle = intent.getStringExtra("NOTIFICATION_TITLE") ?: "Lembrete"
        val notificationMessage = intent.getStringExtra("NOTIFICATION_MESSAGE") ?: "Hora de tomar seu medicamento!"
        // Você pode passar mais dados do Lembrete aqui se necessário, ou buscar no Worker

        if (lembreteId != -1L) {
            Log.d("AlarmReceiver", "Agendando NotificationWorker para lembreteId: $lembreteId")
            val workData = Data.Builder()
                .putLong("LEMBRETE_ID", lembreteId)
                .putString("NOTIFICATION_TITLE", notificationTitle)
                .putString("NOTIFICATION_MESSAGE", notificationMessage)
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
