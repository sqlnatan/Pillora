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
import com.pillora.pillora.PilloraApplication // Importação necessária
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
        // Ações existentes
        const val ACTION_MEDICAMENTO_TOMADO = "com.pillora.pillora.ACTION_MEDICAMENTO_TOMADO"
        const val ACTION_CONSULTA_COMPARECEU = "com.pillora.pillora.ACTION_CONSULTA_COMPARECEU"
        const val ACTION_CONSULTA_REMARCAR = "com.pillora.pillora.ACTION_CONSULTA_REMARCAR"
        // Novas ações para vacina
        const val ACTION_VACINA_TOMADA = "com.pillora.pillora.ACTION_VACINA_TOMADA"
        const val ACTION_VACINA_REMARCAR = "com.pillora.pillora.ACTION_VACINA_REMARCAR"

        // Extras existentes
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
        const val EXTRA_HORA_CONSULTA = "EXTRA_HORA_CONSULTA"
        const val EXTRA_MINUTO_CONSULTA = "EXTRA_MINUTO_CONSULTA"

        // Novos extras para vacina
        const val EXTRA_VACINA_ID = "EXTRA_VACINA_ID"
        const val EXTRA_IS_VACINA = "EXTRA_IS_VACINA"
        const val EXTRA_IS_CONFIRMACAO = "EXTRA_IS_CONFIRMACAO"
        const val EXTRA_NOME_VACINA = "EXTRA_NOME_VACINA"
        const val EXTRA_HORA_VACINA = "EXTRA_HORA_VACINA"
    }

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "NotificationWorker.doWork: Iniciando execução...")

        // Obter parâmetros da notificação
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val notificationId = lembreteId.toInt() // Usar ID do lembrete como ID da notificação
        val notificationTitle = inputData.getString(EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val notificationMessage = inputData.getString(EXTRA_NOTIFICATION_MESSAGE) ?: "" // Observação ou dose
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID) ?: ""
        val consultaId = inputData.getString(EXTRA_CONSULTA_ID) ?: medicamentoId // Consulta usa medicamentoId
        val vacinaId = inputData.getString(EXTRA_VACINA_ID) ?: medicamentoId // Vacina usa medicamentoId
        val recipientName = inputData.getString(EXTRA_RECIPIENT_NAME) ?: ""
        val proximaOcorrenciaMillis = inputData.getLong(EXTRA_PROXIMA_OCORRENCIA_MILLIS, 0L)
        val horaConsulta = inputData.getInt(EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = inputData.getInt(EXTRA_MINUTO_CONSULTA, -1)
        val isConsulta = inputData.getBoolean(EXTRA_IS_CONSULTA, false)
        val isVacina = inputData.getBoolean(EXTRA_IS_VACINA, false)
        val isConfirmacao = inputData.getBoolean(EXTRA_IS_CONFIRMACAO, false)
        val tipoLembrete = inputData.getString(EXTRA_TIPO_LEMBRETE) ?: "tipo_desconhecido"
        val nomeVacina = inputData.getString(EXTRA_NOME_VACINA) ?: ""
        val horaVacina = inputData.getString(EXTRA_HORA_VACINA) ?: ""

        Log.d("NotificationWorker", "Recebido: lembreteId=$lembreteId, tipo=$tipoLembrete, isConsulta=$isConsulta, isVacina=$isVacina, isConfirmacao=$isConfirmacao")
        Log.d("NotificationWorker", "Recebido: title=$notificationTitle, message(obs/dose)=$notificationMessage, recipient=$recipientName")
        Log.d("NotificationWorker", "Recebido: medId=$medicamentoId, consultaId=$consultaId, vacinaId=$vacinaId")
        Log.d("NotificationWorker", "Recebido: horaConsulta=$horaConsulta:$minutoConsulta, horaVacina=$horaVacina")

        if (lembreteId == -1L) {
            Log.e("NotificationWorker", "ERRO! ID do Lembrete inválido. Abortando.")
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

        // Determinar título, mensagem e CANAL com base no tipo (consulta, vacina ou medicamento)
        val finalTitle: String
        val finalMessage: String
        val channelId: String

        if (isConsulta) {
            // ===== FLUXO DE CONSULTA =====
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
                DateTimeUtils.TIPO_2H_ANTES -> "Hoje às $horarioRealConsultaStr"
                else -> {
                    Log.w("NotificationWorker", "Tipo de lembrete de consulta inesperado: $tipoLembrete. Usando observação.")
                    notificationMessage // Fallback para a observação
                }
            }

            // Canal de CONSULTAS para pós-consulta (som padrão), MEDICAMENTOS para pré-consulta (alarme)
            channelId = if (tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
                PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID
            } else {
                PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID
            }

            Log.d("NotificationWorker", "Mensagem final para consulta ($tipoLembrete) = $finalMessage, canal: $channelId")

        } else if (isVacina) {
            // ===== FLUXO DE VACINA =====
            Log.d("NotificationWorker", "Processando como VACINA")
            val nome = recipientName.ifBlank { "Você" }
            val nomeVacinaReal = nomeVacina.ifBlank { notificationTitle.replace("Vacina:", "").trim() }

            finalTitle = if (isConfirmacao) {
                "$nome, você tomou a vacina $nomeVacinaReal?"
            } else {
                "$nome, lembrete de vacina: $nomeVacinaReal"
            }

            finalMessage = when (tipoLembrete) {
                DateTimeUtils.TIPO_CONFIRMACAO -> "Confirme se você tomou a vacina para remover o lembrete."
                DateTimeUtils.TIPO_24H_ANTES -> "Amanhã às $horaVacina"
                DateTimeUtils.TIPO_2H_ANTES -> "Hoje às $horaVacina"
                else -> {
                    Log.w("NotificationWorker", "Tipo de lembrete de vacina inesperado: $tipoLembrete. Usando observação.")
                    notificationMessage // Fallback para a observação
                }
            }

            // Canal de CONSULTAS para confirmação (som padrão), MEDICAMENTOS para pré-vacina (alarme)
            channelId = if (isConfirmacao) {
                PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID // Usar canal sem som de alarme para confirmação
            } else {
                PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID // Usar canal com som de alarme para lembretes prévios
            }

            Log.d("NotificationWorker", "Mensagem final para vacina ($tipoLembrete) = $finalMessage, canal: $channelId")

        } else {
            // ===== FLUXO DE MEDICAMENTO =====
            Log.d("NotificationWorker", "Processando como MEDICAMENTO")
            finalTitle = if (recipientName.isNotBlank()) {
                "${recipientName}, hora de tomar ${notificationTitle.replace("Hora de:", "").trim()}"
            } else {
                notificationTitle
            }
            finalMessage = notificationMessage
            channelId = PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID
            Log.d("NotificationWorker", "Mensagem final para medicamento = $finalMessage, canal: $channelId")
        }

        Log.d("NotificationWorker", "Preparando notificação final: title=$finalTitle, message=$finalMessage, canal=$channelId")

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(finalMessage)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Configurar prioridade, vibração e fullScreenIntent (som é definido pelo CANAL)
        if ((isConsulta && tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) || (isVacina && isConfirmacao)) {
            Log.d("NotificationWorker", "Configurando notificação como PADRÃO (pós-consulta/pós-vacina)")
            builder
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVibrate(longArrayOf(0, 300, 200, 300))
        } else {
            Log.d("NotificationWorker", "Configurando notificação como ALARME (pré-consulta/pré-vacina ou medicamento)")
            builder
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setLights(0xFF0000FF.toInt(), 1000, 500)
                .setFullScreenIntent(pendingIntent, true)
        }

        // Adicionar botões de ação
        if (isConsulta && tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
            Log.d("NotificationWorker", "Adicionando botões para notificação PÓS-CONSULTA")
            // Ação "Sim, excluir consulta"
            val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_CONSULTA_COMPARECEU
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CONSULTA_ID, consultaId)
            }
            val excluirPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 1, excluirIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            // Ação "Remarcar consulta"
            val remarcarIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_CONSULTA_REMARCAR
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_CONSULTATION_EDIT", true)
                putExtra("CONSULTATION_ID", consultaId)
            }
            val remarcarPendingIntent = PendingIntent.getActivity(applicationContext, notificationId * 10 + 2, remarcarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            builder.addAction(R.drawable.ic_action_check, "Sim, excluir consulta", excluirPendingIntent)
            builder.addAction(R.drawable.ic_action_edit, "Remarcar consulta", remarcarPendingIntent)

        } else if (isVacina && isConfirmacao) {
            Log.d("NotificationWorker", "Adicionando botões para notificação PÓS-VACINA")
            // Ação "Sim, excluir vacina"
            val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_VACINA_TOMADA
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_VACINA_ID, vacinaId)
                Log.d("NotificationWorker", "Intent EXCLUIR VACINA para Lembrete ID: $lembreteId, Vacina ID: $vacinaId")
            }
            val excluirPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 3, excluirIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            // Ação "Remarcar vacina"
            val remarcarIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_VACINA_REMARCAR
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_VACCINE_EDIT", true) // Usar chave específica para vacina
                putExtra("VACCINE_ID", vacinaId) // Usar chave específica para vacina
                Log.d("NotificationWorker", "Intent REMARCAR VACINA (Activity) para Vacina ID: $vacinaId")
            }
            val remarcarPendingIntent = PendingIntent.getActivity(applicationContext, notificationId * 10 + 4, remarcarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            builder.addAction(R.drawable.ic_action_check, "Sim, excluir vacina", excluirPendingIntent)
            builder.addAction(R.drawable.ic_action_edit, "Remarcar vacina", remarcarPendingIntent)

        } else if (!isConsulta && !isVacina) {
            Log.d("NotificationWorker", "Adicionando botão 'Tomei' para MEDICAMENTO")
            val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_MEDICAMENTO_TOMADO
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            }
            val tomarPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 5, tomarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent)
        } else {
            Log.d("NotificationWorker", "Nenhum botão adicionado (lembrete pré-consulta/pré-vacina)")
        }

        // Verificar permissão antes de notificar
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NotificationWorker", "ERRO! Permissão POST_NOTIFICATIONS não concedida. A notificação não será exibida.")
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
