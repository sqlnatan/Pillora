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
import com.pillora.pillora.R
import com.pillora.pillora.receiver.NotificationActionReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        const val EXTRA_MINUTO_CONSULTA = "EXTRA_MINUTO_CONSULTA"
    }

    override suspend fun doWork(): Result {
        // Obter parâmetros da notificação
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val notificationId = lembreteId.toInt() // Usar ID do lembrete como ID da notificação para unicidade
        val notificationTitle = inputData.getString(EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val notificationMessage = inputData.getString(EXTRA_NOTIFICATION_MESSAGE) ?: ""
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID) ?: ""
        val recipientName = inputData.getString(EXTRA_RECIPIENT_NAME) ?: ""
        val hora = inputData.getInt(EXTRA_HORA, -1)
        val minuto = inputData.getInt(EXTRA_MINUTO, -1)

        // Obter o horário real da consulta
        val horaConsulta = inputData.getInt(EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = inputData.getInt(EXTRA_MINUTO_CONSULTA, -1)

        Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: lembreteId=$lembreteId, title=$notificationTitle, message=$notificationMessage, hora=$hora, minuto=$minuto")
        Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: horaConsulta=$horaConsulta, minutoConsulta=$minutoConsulta")

        // Verificar se é uma consulta pelo título
        val isConsulta = inputData.getBoolean(EXTRA_IS_CONSULTA, false) ||
                notificationTitle.contains("Consulta:", ignoreCase = true)

        // Verificar se é uma notificação pós-consulta (3 horas depois)
        val isPosConsulta = notificationMessage.contains("3 horas depois", ignoreCase = true)

        // Extrair especialidade da consulta (se for consulta)
        val especialidade = if (isConsulta) {
            notificationTitle.replace("Hora de: Consulta:", "", ignoreCase = true).trim()
        } else {
            ""
        }

        // Formatar o horário real da consulta
        val horarioRealConsulta = if (horaConsulta >= 0 && minutoConsulta >= 0) {
            String.format(Locale.getDefault(), "%02d:%02d", horaConsulta, minutoConsulta)
        } else {
            // Fallback para o horário do lembrete se não tiver o horário real
            String.format(Locale.getDefault(), "%02d:%02d", hora, minuto)
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
        val finalTitle: String
        val finalMessage: String

        if (isConsulta) {
            // Formatação para notificações de consulta
            val nome = recipientName.ifBlank { "Você" }

            // Título sem repetição do "você"
            finalTitle = "$nome tem consulta com $especialidade"

            // Verificar se é hoje ou amanhã
            val hoje = Calendar.getInstance()
            val amanha = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

            // Criar calendário para a data da consulta
            val dataConsulta = Calendar.getInstance()

            // Se temos o horário real da consulta, usamos ele
            if (horaConsulta >= 0 && minutoConsulta >= 0) {
                // Precisamos determinar a data da consulta com base no tipo de lembrete
                if (notificationMessage.contains("24 horas antes", ignoreCase = true)) {
                    // Se é 24h antes, a consulta é amanhã
                    dataConsulta.add(Calendar.DAY_OF_YEAR, 1)
                } else if (notificationMessage.contains("2 horas antes", ignoreCase = true)) {
                    // Se é 2h antes, a consulta é hoje
                    // Não precisamos ajustar a data
                } else if (isPosConsulta) {
                    // Se é 3h depois, a consulta já aconteceu hoje
                    // Não precisamos ajustar a data
                }

                // Definir a hora e minuto da consulta
                dataConsulta.set(Calendar.HOUR_OF_DAY, horaConsulta)
                dataConsulta.set(Calendar.MINUTE, minutoConsulta)
            }

            val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dataHoje = formatoData.format(hoje.time)
            val dataAmanha = formatoData.format(amanha.time)
            val dataConsultaStr = formatoData.format(dataConsulta.time)

            // Mensagem baseada no tipo de lembrete
            finalMessage = when {
                isPosConsulta -> "Você compareceu à consulta?"

                notificationMessage.contains("24 horas antes", ignoreCase = true) ->
                    "Amanhã às $horarioRealConsulta"

                notificationMessage.contains("2 horas antes", ignoreCase = true) -> {
                    if (dataConsultaStr == dataHoje) {
                        "Hoje às $horarioRealConsulta"
                    } else if (dataConsultaStr == dataAmanha) {
                        "Amanhã às $horarioRealConsulta"
                    } else {
                        "Em ${formatoData.format(dataConsulta.time)} às $horarioRealConsulta"
                    }
                }

                else -> notificationMessage
            }

            Log.e("PILLORA_DEBUG", "Consulta detectada: especialidade=$especialidade, tipo=${notificationMessage}, mensagem final=$finalMessage")
        } else {
            // Formatação para notificações de medicamento
            finalTitle = if (recipientName.isNotBlank()) {
                "${recipientName}, hora de tomar $notificationTitle"
            } else {
                "Hora de: $notificationTitle"
            }
            finalMessage = notificationMessage
        }

        Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Preparando notificação: title=$finalTitle, message=$finalMessage, isConsulta=$isConsulta, isPosConsulta=$isPosConsulta")

        // Configurar ações com base no tipo (consulta ou medicamento)
        val builder = NotificationCompat.Builder(applicationContext, "lembretes_medicamentos_channel")
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(finalMessage)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isPosConsulta) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Configurar som e vibração apenas para lembretes que não são pós-consulta
        if (!isPosConsulta) {
            builder
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setLights(0xFF0000FF.toInt(), 1000, 500)
                .setFullScreenIntent(pendingIntent, true)
        } else {
            // Para notificação pós-consulta, usar som de notificação normal
            builder
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 300, 200, 300))
        }

        if (isConsulta) {
            if (isPosConsulta) {
                // Ação "Sim, excluir consulta"
                val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_CONSULTA_COMPARECEU
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId) // Usar medicamentoId como consultaId
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
                    putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId) // Usar medicamentoId como consultaId
                }
                val remarcarPendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    notificationId * 10 + 2,
                    remarcarIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(R.drawable.ic_action_check, "Sim, excluir consulta", excluirPendingIntent)
                builder.addAction(R.drawable.ic_action_check, "Remarcar consulta", remarcarPendingIntent)

                Log.e("PILLORA_DEBUG", "Adicionados botões para notificação pós-consulta")
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
            return Result.failure()
        }

        Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Exibindo notificação para lembreteId=$lembreteId, title=$finalTitle, message=$finalMessage")

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
            Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Notificação exibida com sucesso para lembreteId=$lembreteId")
        } catch (e: Exception) {
            Log.e("PILLORA_DEBUG", "NotificationWorker.doWork: Erro ao exibir notificação", e)
            return Result.failure()
        }

        return Result.success()
    }
}
