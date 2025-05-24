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
import com.pillora.pillora.utils.DateTimeUtils
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
        const val EXTRA_CONSULTA_ID = "EXTRA_CONSULTA_ID" // Pode ser o mesmo que medicamentoId para consultas
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE"
        const val EXTRA_NOTIFICATION_MESSAGE = "EXTRA_NOTIFICATION_MESSAGE" // Usado para observação da consulta
        const val EXTRA_RECIPIENT_NAME = "EXTRA_RECIPIENT_NAME"
        const val EXTRA_PROXIMA_OCORRENCIA_MILLIS = "EXTRA_PROXIMA_OCORRENCIA_MILLIS"
        const val EXTRA_HORA = "EXTRA_HORA" // Hora do lembrete
        const val EXTRA_MINUTO = "EXTRA_MINUTO" // Minuto do lembrete
        const val EXTRA_IS_CONSULTA = "EXTRA_IS_CONSULTA"
        const val EXTRA_TIPO_LEMBRETE = "EXTRA_TIPO_LEMBRETE" // Chave para o tipo explícito
        const val EXTRA_HORA_CONSULTA = "EXTRA_HORA_CONSULTA" // Hora real da consulta
        const val EXTRA_MINUTO_CONSULTA = "EXTRA_MINUTO_CONSULTA" // Minuto real da consulta
    }

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "NotificationWorker.doWork: Iniciando execução...")

        // Obter parâmetros da notificação
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val notificationId = lembreteId.toInt() // Usar ID do lembrete como ID da notificação
        val notificationTitle = inputData.getString(EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val notificationMessage = inputData.getString(EXTRA_NOTIFICATION_MESSAGE) ?: "" // Observação da consulta
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID) ?: ""
        val recipientName = inputData.getString(EXTRA_RECIPIENT_NAME) ?: ""
        val proximaOcorrenciaMillis = inputData.getLong(EXTRA_PROXIMA_OCORRENCIA_MILLIS, 0L)
        val horaConsulta = inputData.getInt(EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = inputData.getInt(EXTRA_MINUTO_CONSULTA, -1)
        val isConsulta = inputData.getBoolean(EXTRA_IS_CONSULTA, false)
        val tipoLembrete = inputData.getString(EXTRA_TIPO_LEMBRETE) ?: "tipo_desconhecido"

        Log.d("NotificationWorker", "Recebido: lembreteId=$lembreteId, tipo=$tipoLembrete, title=$notificationTitle, message(obs)=$notificationMessage")
        Log.d("NotificationWorker", "Recebido: horaConsulta=$horaConsulta, minutoConsulta=$minutoConsulta, isConsulta=$isConsulta")

        if (tipoLembrete == "tipo_desconhecido" && isConsulta) {
            Log.e("NotificationWorker", "ERRO! Tipo de lembrete de consulta desconhecido para Lembrete ID: $lembreteId. Abortando.")
            return Result.failure()
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
            Log.d("NotificationWorker", "Processando como CONSULTA")
            val nome = recipientName.ifBlank { "Você" }
            val especialidade = notificationTitle.replace("Consulta:", "", ignoreCase = true).trim()
            finalTitle = "$nome tem consulta com $especialidade"

            val horarioRealConsultaStr = if (horaConsulta >= 0 && minutoConsulta >= 0) {
                String.format(Locale.getDefault(), "%02d:%02d", horaConsulta, minutoConsulta)
            } else {
                Log.w("NotificationWorker", "Horário real da consulta inválido ($horaConsulta:$minutoConsulta).")
                "" // Não exibir horário se inválido
            }

            finalMessage = when (tipoLembrete) {
                DateTimeUtils.TIPO_3H_DEPOIS -> "Você compareceu à consulta?"
                DateTimeUtils.TIPO_24H_ANTES -> "Amanhã às $horarioRealConsultaStr"
                DateTimeUtils.TIPO_2H_ANTES -> {
                    // Para 2h antes, sempre consideramos "Hoje"
                    "Hoje às $horarioRealConsultaStr"
                }
                else -> {
                    Log.w("NotificationWorker", "Tipo de lembrete de consulta inesperado: $tipoLembrete. Usando observação.")
                    notificationMessage // Fallback para a observação
                }
            }
            Log.d("NotificationWorker", "Mensagem final para consulta ($tipoLembrete) = $finalMessage")
        } else {
            Log.d("NotificationWorker", "Processando como MEDICAMENTO")
            finalTitle = if (recipientName.isNotBlank()) {
                "${recipientName}, hora de tomar ${notificationTitle.replace("Hora de:", "").trim()}"
            } else {
                notificationTitle // Já vem formatado como "Hora de: ..."
            }
            finalMessage = notificationMessage // Mensagem original (dose/observação do medicamento)
            Log.d("NotificationWorker", "Mensagem final para medicamento = $finalMessage")
        }

        Log.d("NotificationWorker", "Preparando notificação final: title=$finalTitle, message=$finalMessage")

        // Configurar builder da notificação
        val builder = NotificationCompat.Builder(applicationContext, "lembretes_medicamentos_channel")
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(finalMessage)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Configurar som, vibração e prioridade com base no tipo
        if (isConsulta && tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
            Log.d("NotificationWorker", "Configurando notificação como SILENCIOSA (pós-consulta)")
            builder
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 300, 200, 300))
        } else {
            Log.d("NotificationWorker", "Configurando notificação como ALARME (pré-consulta ou medicamento)")
            builder
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setLights(0xFF0000FF.toInt(), 1000, 500)
                .setFullScreenIntent(pendingIntent, true) // Apenas para alarmes
        }

        // Adicionar botões de ação
        if (isConsulta && tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
            Log.d("NotificationWorker", "Adicionando botões para notificação PÓS-CONSULTA")
            val consultaId = medicamentoId // Usar medicamentoId como consultaId

            // Ação "Sim, excluir consulta"
            val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_CONSULTA_COMPARECEU
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CONSULTA_ID, consultaId)
                Log.d("NotificationWorker", "Intent EXCLUIR para Lembrete ID: $lembreteId, Consulta ID: $consultaId")
            }
            val excluirPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                notificationId * 10 + 1, // Request code único
                excluirIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Ação "Remarcar consulta"
            val remarcarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_CONSULTA_REMARCAR
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CONSULTA_ID, consultaId)
                Log.d("NotificationWorker", "Intent REMARCAR para Lembrete ID: $lembreteId, Consulta ID: $consultaId")
            }
            val remarcarPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                notificationId * 10 + 2, // Request code único
                remarcarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(R.drawable.ic_action_check, "Sim, excluir consulta", excluirPendingIntent)
            builder.addAction(R.drawable.ic_action_edit, "Remarcar consulta", remarcarPendingIntent) // Ícone de edição

        } else if (!isConsulta) {
            Log.d("NotificationWorker", "Adicionando botão 'Tomei' para MEDICAMENTO")
            val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_MEDICAMENTO_TOMADO
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            }
            val tomarPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                notificationId * 10 + 1, // Request code único
                tomarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent)
        } else {
            Log.d("NotificationWorker", "Nenhum botão adicionado (lembrete pré-consulta)")
        }

        // Verificar permissão antes de notificar
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NotificationWorker", "ERRO! Permissão POST_NOTIFICATIONS não concedida. A notificação não será exibida.")
            // Considerar solicitar permissão ou notificar o usuário de outra forma
            return Result.failure()
        }

        Log.d("NotificationWorker", "Exibindo notificação final para lembreteId=$lembreteId, title=$finalTitle, message=$finalMessage")

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
            Log.d("NotificationWorker", "Notificação exibida com sucesso para lembreteId=$lembreteId")
        } catch (e: Exception) {
            Log.e("NotificationWorker", "ERRO ao exibir notificação", e)
            return Result.failure()
        }

        Log.d("NotificationWorker", "Execução concluída com sucesso.")
        return Result.success()
    }
}

