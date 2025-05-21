package com.pillora.pillora.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillora.pillora.MainActivity
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.R
import com.pillora.pillora.receiver.NotificationActionReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val ACTION_MEDICAMENTO_TOMADO = "com.pillora.pillora.ACTION_MEDICAMENTO_TOMADO"
        const val EXTRA_LEMBRETE_ID = "EXTRA_LEMBRETE_ID"
        const val EXTRA_MEDICAMENTO_ID = "EXTRA_MEDICAMENTO_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE"
        const val EXTRA_NOTIFICATION_MESSAGE = "EXTRA_NOTIFICATION_MESSAGE"
        const val EXTRA_RECIPIENT_NAME = "EXTRA_RECIPIENT_NAME"
        const val EXTRA_PROXIMA_OCORRENCIA_MILLIS = "EXTRA_PROXIMA_OCORRENCIA_MILLIS"
        const val EXTRA_HORA = "EXTRA_HORA"
        const val EXTRA_MINUTO = "EXTRA_MINUTO"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID)
        val notificationId = lembreteId.toInt() // Usar ID do lembrete como ID da notificação para unicidade

        val titleFromIntent = inputData.getString(EXTRA_NOTIFICATION_TITLE)
        val messageFromIntent = inputData.getString(EXTRA_NOTIFICATION_MESSAGE)
        val recipientNameFromIntent = inputData.getString(EXTRA_RECIPIENT_NAME)
        val isMedicineAlarm = inputData.getBoolean("IS_MEDICINE_ALARM", false)

        Log.d("NotificationWorker", "Iniciando trabalho para lembreteId: $lembreteId, medicamentoId: $medicamentoId")

        if (lembreteId == -1L) {
            Log.e("NotificationWorker", "ID do Lembrete inválido.")
            return@withContext Result.failure()
        }

        if (titleFromIntent == null || messageFromIntent == null) {
            Log.e("NotificationWorker", "Título ou mensagem da notificação nulos para lembreteId: $lembreteId")
            return@withContext Result.failure()
        }

        var finalTitle = titleFromIntent
        if (!recipientNameFromIntent.isNullOrBlank()) {
            finalTitle = "$recipientNameFromIntent, ${titleFromIntent.replaceFirst("Hora de: ", "hora de tomar ")}"
        }

        // Intent para abrir o app ao clicar na notificação
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação "Tomei"
        val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
            action = ACTION_MEDICAMENTO_TOMADO
            putExtra(EXTRA_LEMBRETE_ID, lembreteId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId) // Crucial para atualização de estoque
        }
        val tomarPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            notificationId * 10 + 1,
            tomarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Som de alarme para garantir que seja ouvido
        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        // Fallback para som de notificação se o som de alarme não estiver disponível
        val fallbackSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(applicationContext, PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(messageFromIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Prioridade máxima
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Categoria para alarmes
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent)
            .setSound(alarmSoundUri) // Usar som de alarme
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000)) // Vibração mais intensa
            .setLights(0xFF0000FF.toInt(), 1000, 500) // Luz de notificação
            .setFullScreenIntent(pendingIntent, true) // Exibir mesmo em modo "Não perturbe"

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NotificationWorker", "Permissão POST_NOTIFICATIONS não concedida. A notificação não será exibida.")
            return@withContext Result.failure()
        }

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
            Log.d("NotificationWorker", "Notificação exibida para lembreteId: $lembreteId, medicamentoId: $medicamentoId, notificationId: $notificationId. Título: $finalTitle, Mensagem: $messageFromIntent")
        } catch (e: Exception) {
            Log.e("NotificationWorker", "Erro ao exibir notificação para lembreteId: $lembreteId", e)
            return@withContext Result.failure()
        }

        return@withContext Result.success()
    }
}
