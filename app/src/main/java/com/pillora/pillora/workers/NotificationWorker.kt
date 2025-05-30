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
// import java.text.SimpleDateFormat // Import não utilizado
// import java.util.Calendar // Import não utilizado
// import java.util.Date // Import não utilizado
import java.util.Locale

class NotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        // Ações
        const val ACTION_MEDICAMENTO_TOMADO = "com.pillora.pillora.ACTION_MEDICAMENTO_TOMADO"
        const val ACTION_CONSULTA_COMPARECEU = "com.pillora.pillora.ACTION_CONSULTA_COMPARECEU"
        const val ACTION_CONSULTA_REMARCAR = "com.pillora.pillora.ACTION_CONSULTA_REMARCAR"
        const val ACTION_VACINA_TOMADA = "com.pillora.pillora.ACTION_VACINA_TOMADA"
        const val ACTION_VACINA_REMARCAR = "com.pillora.pillora.ACTION_VACINA_REMARCAR"

        // Extras
        const val EXTRA_LEMBRETE_ID = "EXTRA_LEMBRETE_ID"
        const val EXTRA_MEDICAMENTO_ID = "EXTRA_MEDICAMENTO_ID"
        const val EXTRA_CONSULTA_ID = "EXTRA_CONSULTA_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE"
        const val EXTRA_NOTIFICATION_MESSAGE = "EXTRA_NOTIFICATION_MESSAGE"
        const val EXTRA_RECIPIENT_NAME = "EXTRA_RECIPIENT_NAME"
        // *** CORREÇÃO: Restaurar constante usada externamente ***
        const val EXTRA_PROXIMA_OCORRENCIA_MILLIS = "EXTRA_PROXIMA_OCORRENCIA_MILLIS"
        const val EXTRA_HORA = "EXTRA_HORA"
        const val EXTRA_MINUTO = "EXTRA_MINUTO"
        const val EXTRA_IS_CONSULTA = "EXTRA_IS_CONSULTA"
        const val EXTRA_TIPO_LEMBRETE = "EXTRA_TIPO_LEMBRETE"
        const val EXTRA_HORA_CONSULTA = "EXTRA_HORA_CONSULTA"
        const val EXTRA_MINUTO_CONSULTA = "EXTRA_MINUTO_CONSULTA"
        const val EXTRA_VACINA_ID = "EXTRA_VACINA_ID"
        const val EXTRA_IS_VACINA = "EXTRA_IS_VACINA"
        const val EXTRA_IS_CONFIRMACAO = "EXTRA_IS_CONFIRMACAO"
        const val EXTRA_VACCINE_NAME = "EXTRA_VACCINE_NAME"
        const val EXTRA_VACCINE_TIME = "EXTRA_VACCINE_TIME"
    }

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "NotificationWorker.doWork: Iniciando execução...")

        // Obter parâmetros da notificação
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val notificationId = lembreteId.toInt()
        val notificationTitle = inputData.getString(EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val notificationMessage = inputData.getString(EXTRA_NOTIFICATION_MESSAGE) ?: ""
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID) ?: ""
        val consultaId = inputData.getString(EXTRA_CONSULTA_ID) ?: ""
        val vacinaId = inputData.getString(EXTRA_VACINA_ID) ?: ""
        val recipientName = inputData.getString(EXTRA_RECIPIENT_NAME) ?: ""
        // A variável proximaOcorrenciaMillis não é usada neste worker, mas a constante é necessária externamente
        // val proximaOcorrenciaMillis = inputData.getLong(EXTRA_PROXIMA_OCORRENCIA_MILLIS, 0L)
        val horaConsulta = inputData.getInt(EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = inputData.getInt(EXTRA_MINUTO_CONSULTA, -1)
        val isConsulta = inputData.getBoolean(EXTRA_IS_CONSULTA, false)
        val isVacina = inputData.getBoolean(EXTRA_IS_VACINA, false)
        val isConfirmacao = inputData.getBoolean(EXTRA_IS_CONFIRMACAO, false)
        val tipoLembrete = inputData.getString(EXTRA_TIPO_LEMBRETE) ?: "tipo_desconhecido"
        val nomeVacina = inputData.getString(EXTRA_VACCINE_NAME) ?: ""
        val horaVacina = inputData.getString(EXTRA_VACCINE_TIME) ?: ""

        Log.d("NotificationWorker", "Recebido: lembreteId=$lembreteId, tipo=$tipoLembrete, isConsulta=$isConsulta, isVacina=$isVacina, isConfirmacao=$isConfirmacao")
        Log.d("NotificationWorker", "Recebido: title=$notificationTitle, message(obs/dose)=$notificationMessage, recipient=$recipientName")
        Log.d("NotificationWorker", "Recebido: medId=$medicamentoId, consultaId=$consultaId, vacinaId=$vacinaId")
        Log.d("NotificationWorker", "Recebido: horaConsulta=$horaConsulta:$minutoConsulta, horaVacina=$horaVacina")

        if (lembreteId == -1L) {
            Log.e("NotificationWorker", "ERRO! ID do Lembrete inválido. Abortando.")
            return Result.failure()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        var finalTitle: String
        val finalMessage: String
        val channelId: String

        when {
            isConsulta -> {
                Log.d("NotificationWorker", "Processando como CONSULTA")
                val nome = recipientName.ifBlank { "Você" }
                val especialidade = if (notificationTitle.equals("Consulta", ignoreCase = true) || notificationTitle.isBlank()) {
                    notificationMessage // Usa a mensagem (observação/especialidade) se o título for genérico
                } else {
                    notificationTitle.replace("Consulta:", "", ignoreCase = true).trim()
                }
                finalTitle = "$nome tem consulta com $especialidade"
                val horarioRealConsultaStr = if (horaConsulta >= 0 && minutoConsulta >= 0) String.format(Locale.getDefault(), "%02d:%02d", horaConsulta, minutoConsulta) else ""

                finalMessage = when (tipoLembrete) {
                    DateTimeUtils.TIPO_3H_DEPOIS -> "Você compareceu à consulta?"
                    DateTimeUtils.TIPO_24H_ANTES -> "Amanhã às $horarioRealConsultaStr"
                    DateTimeUtils.TIPO_2H_ANTES -> "Hoje às $horarioRealConsultaStr"
                    else -> {
                        Log.w("NotificationWorker", "Tipo de lembrete de consulta inesperado: $tipoLembrete. Usando horário: $horarioRealConsultaStr")
                        "Consulta às $horarioRealConsultaStr" // Fallback mais genérico
                    }
                }
                channelId = if (tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
                    PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID // Canal sem som para confirmação
                } else {
                    PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID // Canal COM som para lembretes prévios
                }
                Log.d("NotificationWorker", "Mensagem final para consulta ($tipoLembrete) = $finalMessage, canal: $channelId")
            }

            isVacina -> {
                Log.d("NotificationWorker", "Processando como VACINA")
                val nome = recipientName.ifBlank { "Você" }
                val nomeVacinaReal = nomeVacina.ifBlank { notificationTitle.replace("Vacina:", "").trim() }

                if (isConfirmacao) {
                    // --- Notificação de Confirmação de Vacina (3h depois) ---
                    finalTitle = "Confirmação de Vacina"
                    finalMessage = "$nome, você compareceu à vacina $nomeVacinaReal?"
                    channelId = PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID // Canal sem som de alarme
                    Log.d("NotificationWorker", "Mensagem final para CONFIRMAÇÃO vacina = $finalMessage, canal: $channelId")
                } else {
                    // --- Lembretes Pré-Vacina (24h e 2h antes) ---
                    finalTitle = if (recipientName.isNotBlank()) "$nome, " else ""
                    finalTitle += "você tem vacina: $nomeVacinaReal"

                    finalMessage = when (tipoLembrete) {
                        DateTimeUtils.TIPO_24H_ANTES -> "Amanhã às $horaVacina"
                        DateTimeUtils.TIPO_2H_ANTES -> "Hoje às $horaVacina"
                        else -> {
                            Log.w("NotificationWorker", "Tipo de lembrete pré-vacina inesperado: $tipoLembrete. Usando horário: $horaVacina")
                            "Vacina às $horaVacina" // Fallback
                        }
                    }
                    channelId = PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID // Canal com som de alarme
                    Log.d("NotificationWorker", "Mensagem final para PRÉ-VACINA ($tipoLembrete) = $finalMessage, canal: $channelId")
                }
            }

            else -> {
                // ===== FLUXO DE MEDICAMENTO =====
                Log.d("NotificationWorker", "Processando como MEDICAMENTO")
                finalTitle = if (recipientName.isNotBlank()) {
                    "${recipientName}, hora de tomar ${notificationTitle.replace("Hora de:", "").trim()}"
                } else {
                    notificationTitle
                }
                finalMessage = notificationMessage // Dose/Observação
                channelId = PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID
                Log.d("NotificationWorker", "Mensagem final para medicamento = $finalMessage, canal: $channelId")
            }
        }

        Log.d("NotificationWorker", "Preparando notificação final: title=$finalTitle, message=$finalMessage, canal=$channelId")

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(finalMessage)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(finalMessage)) // Expandir texto longo

        // Configurar prioridade, vibração e fullScreenIntent
        if (channelId == PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID) {
            // Notificações de confirmação (pós-consulta/pós-vacina) - Som padrão, sem full screen
            Log.d("NotificationWorker", "Configurando notificação como PADRÃO (canal consultas/confirmação)")
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 300, 200, 300))
        } else {
            // Lembretes pré-consulta, pré-vacina ou medicamento - Som de ALARME, com full screen
            Log.d("NotificationWorker", "Configurando notificação como ALARME (canal medicamentos/lembretes)")
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) // Usar som de alarme
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setLights(0xFF0000FF.toInt(), 1000, 500)
                .setFullScreenIntent(pendingIntent, true)
        }

        // *** LÓGICA DE BOTÕES REATORADA PARA CLAREZA E REMOÇÃO DE WARNINGS ***
        if (isConsulta) {
            if (tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) {
                // Botões para confirmação de Consulta
                Log.d("NotificationWorker", "Adicionando botões para notificação PÓS-CONSULTA")
                val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_CONSULTA_COMPARECEU
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_CONSULTA_ID, consultaId)
                }
                val excluirPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 1, excluirIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val remarcarIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    action = ACTION_CONSULTA_REMARCAR
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("OPEN_CONSULTATION_EDIT", true)
                    putExtra("CONSULTATION_ID", consultaId)
                }
                val remarcarPendingIntent = PendingIntent.getActivity(applicationContext, notificationId * 10 + 2, remarcarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                builder.addAction(R.drawable.ic_action_check, "Sim, excluir consulta", excluirPendingIntent)
                builder.addAction(R.drawable.ic_action_edit, "Remarcar consulta", remarcarPendingIntent)
            } else {
                // Lembrete pré-consulta - Sem botões
                Log.d("NotificationWorker", "Nenhum botão adicionado (lembrete pré-consulta)")
            }
        } else if (isVacina) {
            if (isConfirmacao) {
                // Botões para confirmação de Vacina
                Log.d("NotificationWorker", "Adicionando botões para notificação PÓS-VACINA")
                val excluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_VACINA_TOMADA
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_VACINA_ID, vacinaId)
                }
                val excluirPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 3, excluirIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val remarcarIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    action = ACTION_VACINA_REMARCAR
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("OPEN_VACCINE_EDIT", true)
                    putExtra("VACCINE_ID", vacinaId)
                }
                val remarcarPendingIntent = PendingIntent.getActivity(applicationContext, notificationId * 10 + 4, remarcarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                builder.addAction(R.drawable.ic_action_check, "Sim, excluir vacina", excluirPendingIntent)
                builder.addAction(R.drawable.ic_action_edit, "Remarcar vacina", remarcarPendingIntent)
            } else {
                // Lembrete pré-vacina - Sem botões
                Log.d("NotificationWorker", "Nenhum botão adicionado (lembrete pré-vacina)")
            }
        } else {
            // É um medicamento - Adicionar botão "Tomei"
            Log.d("NotificationWorker", "Adicionando botão \'Tomei\' para MEDICAMENTO")
            val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = ACTION_MEDICAMENTO_TOMADO
                putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
            }
            val tomarPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 5, tomarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent)
        }

        // Verificar permissão antes de notificar
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NotificationWorker", "ERRO! Permissão POST_NOTIFICATIONS não concedida. A notificação não será exibida.")
            // Considerar retornar Result.failure() ou lidar de outra forma
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

