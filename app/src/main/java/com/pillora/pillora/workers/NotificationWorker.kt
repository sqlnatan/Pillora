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
        const val ACTION_CONSULTA_COMPARECEU = "com.pillora.pillora.ACTION_CONSULTA_COMPARECEU"
        const val ACTION_CONSULTA_REMARCAR = "com.pillora.pillora.ACTION_CONSULTA_REMARCAR"
        const val EXTRA_LEMBRETE_ID = "EXTRA_LEMBRETE_ID"
        const val EXTRA_MEDICAMENTO_ID = "EXTRA_MEDICAMENTO_ID"
        const val EXTRA_CONSULTA_ID = "EXTRA_CONSULTA_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE"
        const val EXTRA_NOTIFICATION_MESSAGE = "EXTRA_NOTIFICATION_MESSAGE"
        const val EXTRA_RECIPIENT_NAME = "EXTRA_RECIPIENT_NAME"
        const val EXTRA_PROXIMA_OCORRENCIA_MILLIS = "EXTRA_PROXIMA_OCORRENCIA_MILLIS"
        const val EXTRA_HORA = "EXTRA_HORA"
        const val EXTRA_MINUTO = "EXTRA_MINUTO"
        const val EXTRA_IS_CONSULTA = "EXTRA_IS_CONSULTA"
        const val EXTRA_TIPO_LEMBRETE = "EXTRA_TIPO_LEMBRETE"
        const val EXTRA_ESPECIALIDADE = "EXTRA_ESPECIALIDADE"
        const val EXTRA_HORA_CONSULTA = "EXTRA_HORA_CONSULTA"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID)
        val consultaId = inputData.getString(EXTRA_CONSULTA_ID)
        val notificationId = lembreteId.toInt() // Usar ID do lembrete como ID da notificação para unicidade

        val titleFromIntent = inputData.getString(EXTRA_NOTIFICATION_TITLE)
        val messageFromIntent = inputData.getString(EXTRA_NOTIFICATION_MESSAGE)
        val recipientNameFromIntent = inputData.getString(EXTRA_RECIPIENT_NAME)
        val isConsulta = inputData.getBoolean(EXTRA_IS_CONSULTA, false)
        val tipoLembrete = inputData.getString(EXTRA_TIPO_LEMBRETE)
        val especialidade = inputData.getString(EXTRA_ESPECIALIDADE)
        val horaConsulta = inputData.getString(EXTRA_HORA_CONSULTA)

        Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Iniciando para lembreteId=$lembreteId, medicamentoId=$medicamentoId, consultaId=$consultaId, isConsulta=$isConsulta")

        if (lembreteId == -1L) {
            Log.e("NotificationWorker", "ID do Lembrete inválido.")
            return@withContext Result.failure()
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

        // Determinar título e mensagem com base no tipo (consulta ou medicamento)
        var finalTitle: String
        val finalMessage: String

        if (isConsulta) {
            // Formatação para notificações de consulta
            val nome = if (!recipientNameFromIntent.isNullOrBlank()) recipientNameFromIntent else "Você"

            finalTitle = "$nome, você tem consulta com $especialidade"

            finalMessage = when (tipoLembrete) {
                "24 horas antes" -> "Amanhã às $horaConsulta"
                "2 horas antes" -> "Hoje às $horaConsulta"
                "3 horas depois" -> "Você compareceu à consulta?"
                else -> messageFromIntent ?: ""
            }
        } else {
            // Formatação para notificações de medicamento (mantém o formato atual)
            finalTitle = if (!recipientNameFromIntent.isNullOrBlank()) {
                "$recipientNameFromIntent, ${titleFromIntent?.replaceFirst("Hora de: ", "hora de tomar ") ?: ""}"
            } else {
                titleFromIntent ?: ""
            }
            finalTitle = finalTitle.replace("Consulta:", "")
            finalMessage = messageFromIntent ?: ""
        }

        // Configurar ações com base no tipo (consulta ou medicamento)
        val builder = NotificationCompat.Builder(applicationContext, PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(finalMessage)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setLights(0xFF0000FF.toInt(), 1000, 500)
            .setFullScreenIntent(pendingIntent, true)

        if (isConsulta) {
            if (tipoLembrete == "3 horas depois") {
                // Ação "Sim, excluir consulta"
                val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_CONSULTA_COMPARECEU
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_CONSULTA_ID, consultaId)
                }
                val excluirPendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    notificationId * 10 + 1,
                    excluirIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Ação "Remarcar consulta"
                val remarcarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_CONSULTA_REMARCAR
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_CONSULTA_ID, consultaId)
                }
                val remarcarPendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    notificationId * 10 + 2,
                    remarcarIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(R.drawable.ic_action_check, "Sim, excluir consulta", excluirPendingIntent)
                builder.addAction(R.drawable.ic_action_check, "Remarcar consulta", remarcarPendingIntent)
            }
            // Para lembretes de 24h e 2h antes, não adicionamos botões
        } else {
            // Ação "Tomei" para medicamentos
            val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_MEDICAMENTO_TOMADO
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            }
            val tomarPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                notificationId * 10 + 1,
                tomarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent)
        }

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NotificationWorker", "Permissão POST_NOTIFICATIONS não concedida. A notificação não será exibida.")
            return@withContext Result.failure()
        }

        Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Exibindo notificação para lembreteId=$lembreteId, title=$finalTitle")

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
            Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Notificação exibida com sucesso para lembreteId=$lembreteId")
        } catch (e: Exception) {
            Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Erro ao exibir notificação", e)
            return@withContext Result.failure()
        }

        return@withContext Result.success()
    }
}
