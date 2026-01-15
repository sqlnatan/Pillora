package com.pillora.pillora.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillora.pillora.MainActivity
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.PilloraApplication // Importação necessária
import com.pillora.pillora.R
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import com.pillora.pillora.receiver.NotificationActionReceiver
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.utils.AlarmScheduler
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        // Ações existentes
        const val ACTION_MEDICAMENTO_TOMADO = "com.pillora.pillora.ACTION_MEDICAMENTO_TOMADO"
        const val ACTION_CONSULTA_COMPARECEU = "com.pillora.pillora.ACTION_CONSULTA_COMPARECEU"
        const val ACTION_CONSULTA_REMARCAR = "com.pillora.pillora.ACTION_CONSULTA_REMARCAR"
        const val ACTION_VACINA_TOMADA = "com.pillora.pillora.ACTION_VACINA_TOMADA"
        const val ACTION_VACINA_REMARCAR = "com.pillora.pillora.ACTION_VACINA_REMARCAR"
        // *** NOVA AÇÃO PARA RECEITA ***
        const val ACTION_RECEITA_CONFIRMADA_EXCLUIR = "com.pillora.pillora.ACTION_RECEITA_CONFIRMADA_EXCLUIR"

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
        const val EXTRA_VACINA_ID = "EXTRA_VACINA_ID"
        const val EXTRA_IS_VACINA = "EXTRA_IS_VACINA"
        const val EXTRA_IS_CONFIRMACAO = "EXTRA_IS_CONFIRMACAO"
        const val EXTRA_VACCINE_NAME = "EXTRA_VACCINE_NAME"
        const val EXTRA_VACCINE_TIME = "EXTRA_VACCINE_TIME"
        // *** NOVO EXTRA PARA RECEITA ***
        const val EXTRA_IS_RECEITA = "EXTRA_IS_RECEITA"
        // Usaremos EXTRA_MEDICAMENTO_ID para guardar o recipeId
    }

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "NotificationWorker.doWork: Iniciando execução...")

        // Obter parâmetros da notificação
        val lembreteId = inputData.getLong(EXTRA_LEMBRETE_ID, -1L)
        val notificationId = lembreteId.toInt()
        val notificationTitle = inputData.getString(EXTRA_NOTIFICATION_TITLE) ?: "Lembrete"
        val notificationMessage = inputData.getString(EXTRA_NOTIFICATION_MESSAGE) ?: ""
        val medicamentoId = inputData.getString(EXTRA_MEDICAMENTO_ID) ?: ""
        val consultaId = inputData.getString(EXTRA_CONSULTA_ID) ?: medicamentoId
        val vacinaId = inputData.getString(EXTRA_VACINA_ID) ?: medicamentoId
        val recipeId = medicamentoId // Usando medicamentoId para guardar o recipeId
        val recipientName = inputData.getString(EXTRA_RECIPIENT_NAME) ?: ""
        val horaConsulta = inputData.getInt(EXTRA_HORA_CONSULTA, -1)
        val minutoConsulta = inputData.getInt(EXTRA_MINUTO_CONSULTA, -1)
        val isConsulta = inputData.getBoolean(EXTRA_IS_CONSULTA, false)
        val isVacina = inputData.getBoolean(EXTRA_IS_VACINA, false)
        val isReceita = inputData.getBoolean(EXTRA_IS_RECEITA, false) // *** NOVO ***
        val isConfirmacao = inputData.getBoolean(EXTRA_IS_CONFIRMACAO, false)
        val tipoLembrete = inputData.getString(EXTRA_TIPO_LEMBRETE) ?: "tipo_desconhecido"
        val nomeVacina = inputData.getString(EXTRA_VACCINE_NAME) ?: ""
        val horaVacina = inputData.getString(EXTRA_VACCINE_TIME) ?: ""
        val proximaOcorrenciaMillis = inputData.getLong(EXTRA_PROXIMA_OCORRENCIA_MILLIS, 0L) // Usar o extra correto

        Log.d("NotificationWorker", "Recebido: lembreteId=$lembreteId, tipo=$tipoLembrete, isConsulta=$isConsulta, isVacina=$isVacina, isReceita=$isReceita, isConfirmacao=$isConfirmacao")
        Log.d("NotificationWorker", "Recebido: title=$notificationTitle, message(obs/dose)=$notificationMessage, recipient=$recipientName")
        Log.d("NotificationWorker", "Recebido: medId=$medicamentoId, consultaId=$consultaId, vacinaId=$vacinaId, recipeId=$recipeId")
        Log.d("NotificationWorker", "Recebido: horaConsulta=$horaConsulta:$minutoConsulta, horaVacina=$horaVacina")

        if (lembreteId == -1L) {
            Log.e("NotificationWorker", "ERRO! ID do Lembrete inválido. Abortando.")
            return Result.failure()
        }

        // 1. Lógica de cancelamento (Foco principal)
        if (!isConsulta && !isVacina && !isReceita) {
            // Se for um lembrete de medicamento, verificar se a data/hora da ocorrência já passou do limite.
            // O limite é implícito: se o lembrete foi agendado, ele é a próxima ocorrência.
            // A lógica de cancelamento deve ser feita APÓS a exibição da notificação e ANTES do reagendamento.
            // Como não há reagendamento aqui, a lógica é apenas desativar o lembrete no DB.

            val lembreteDao = (applicationContext as PilloraApplication).database.lembreteDao()
            val lembrete = lembreteDao.getLembreteById(lembreteId)

            if (lembrete != null) {
                // Buscar o medicamento de forma assíncrona usando suspendCoroutine
                val medicine = suspendCoroutine { continuation ->
                    MedicineRepository.getMedicineById(
                        medicineId = medicamentoId,
                        onSuccess = { med ->
                            continuation.resume(med)
                        },
                        onError = { error ->
                            Log.e("NotificationWorker", "Erro ao buscar medicamento $medicamentoId para verificação de expiração", error)
                            continuation.resume(null)
                        }
                    )
                }

                if (medicine != null && medicine.duration > 0) {
                    val endDateCal = try {
                        val startDateCal = Calendar.getInstance().apply { time = SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(medicine.startDate) ?: throw IllegalArgumentException("Invalid start date format") }
                        (startDateCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, medicine.duration) }
                    } catch (e: Exception) {
                        Log.e("NotificationWorker", "Erro ao calcular data de fim para medicamento ${medicine.id}: ${e.message}")
                        return Result.success() // Se não conseguir calcular a data de fim, assume que o lembrete é inválido e não prossegue.
                    }

                    // A data de fim do tratamento é o dia *depois* do último dia de duração.
                    // Exemplo: 7 dias a partir de 01/01/2025 termina em 08/01/2025 (07/01 é o último dia).
                    // Se a próxima ocorrência (proximaOcorrenciaMillis) for no dia de fim ou depois, deve ser cancelada.

                    // Ajustar endDateCal para o fim do último dia de tratamento (23:59:59)
                    endDateCal.add(Calendar.DAY_OF_YEAR, -1) // Volta para o último dia de tratamento
                    endDateCal.set(Calendar.HOUR_OF_DAY, 23)
                    endDateCal.set(Calendar.MINUTE, 59)
                    endDateCal.set(Calendar.SECOND, 59)
                    endDateCal.set(Calendar.MILLISECOND, 999)

                    // Se a próxima ocorrência (proximaOcorrenciaMillis) for DEPOIS do fim do último dia de tratamento, desativar.
                    if (proximaOcorrenciaMillis > endDateCal.timeInMillis) {
                        Log.d("NotificationWorker", "Lembrete $lembreteId para medicamento $medicamentoId expirou (fim em ${endDateCal.time}). Desativando.")
                        lembreteDao.updateLembrete(lembrete.copy(ativo = false))
                        // Não exibir notificação e não prosseguir.
                        return Result.success()
                    }
                }
            } else {
                Log.w("NotificationWorker", "Lembrete $lembreteId não encontrado no DB local. Ignorando verificação de expiração.")
            }
        }
        // Fim da Lógica de cancelamento (Foco principal)

        // ==========================================
        // REAGENDAMENTO AUTOMÁTICO DE ALARMES
        // ==========================================

        // Reagendar alarme IMEDIATAMENTE após disparar (APENAS para medicamentos)
        if (!isConsulta && !isVacina && !isReceita) {
            Log.d("NotificationWorker", "Iniciando reagendamento automático para lembrete $lembreteId")

            try {
                val lembreteDao = (applicationContext as PilloraApplication).database.lembreteDao()
                val lembreteAtual = lembreteDao.getLembreteById(lembreteId)

                if (lembreteAtual != null && lembreteAtual.ativo) {
                    // Buscar o medicamento de forma assíncrona usando suspendCoroutine
                    val medicine = suspendCoroutine { continuation ->
                        MedicineRepository.getMedicineById(
                            medicineId = medicamentoId,
                            onSuccess = { medicine ->
                                continuation.resume(medicine)
                            },
                            onError = { error ->
                                Log.e("NotificationWorker", "Erro ao buscar medicamento $medicamentoId", error)
                                continuation.resume(null)
                            }
                        )
                    }

                    if (medicine != null) {
                        // Usar o AlarmScheduler para reagendar
                        val reagendado = AlarmScheduler.rescheduleNextOccurrence(
                            context = applicationContext,
                            lembrete = lembreteAtual,
                            medicine = medicine
                        )

                        if (reagendado) {
                            Log.d("NotificationWorker", "Alarme reagendado com sucesso para lembrete $lembreteId")
                        } else {
                            Log.d("NotificationWorker", "Alarme não reagendado (tratamento finalizado ou alarmes desabilitados) para lembrete $lembreteId")
                        }
                    } else {
                        Log.w("NotificationWorker", "Medicamento $medicamentoId não encontrado. Não foi possível reagendar.")
                    }
                } else {
                    Log.d("NotificationWorker", "Lembrete $lembreteId não encontrado ou inativo. Não reagendando.")
                }
            } catch (e: Exception) {
                Log.e("NotificationWorker", "Erro ao reagendar alarme para lembrete $lembreteId", e)
                // Não falhar o worker por causa de erro no reagendamento
            }
        }

        // ==========================================
        // FIM DO REAGENDAMENTO
        // ==========================================

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // TODO: Adicionar extras para navegar para a tela de receita específica se necessário
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        var finalTitle: String
        var finalMessage: String
        val channelId: String

        when {
            // *** NOVO: Lógica para Receitas ***
            isReceita -> {
                Log.d("NotificationWorker", "Processando como RECEITA")
                val nomeMedico = notificationTitle.replace("Receita Dr(a).", "").trim()
                val validade = notificationMessage.replace("Validade:", "").trim()

                when (tipoLembrete) {
                    DateTimeUtils.TIPO_RECEITA_CONFIRMACAO -> {
                        finalTitle = "Confirmação de Compra da Receita"
                        finalMessage = "Você já comprou os medicamentos da receita do(a) Dr(a). $nomeMedico (validade $validade)?"
                        channelId = PilloraApplication.CHANNEL_LEMBRETES_RECEITAS_ID // Canal silencioso
                    }
                    DateTimeUtils.TIPO_RECEITA_VENCIMENTO -> {
                        finalTitle = "Receita Vence Hoje!"
                        finalMessage = "Sua receita do(a) Dr(a). $nomeMedico vence hoje ($validade). Não esqueça de comprar os medicamentos!"
                        channelId = PilloraApplication.CHANNEL_LEMBRETES_RECEITAS_ID // Canal silencioso
                    }
                    DateTimeUtils.TIPO_RECEITA_1D_ANTES -> {
                        finalTitle = "Receita Vence Amanhã!"
                        finalMessage = "Sua receita do(a) Dr(a). $nomeMedico vence amanhã ($validade)."
                        channelId = PilloraApplication.CHANNEL_LEMBRETES_RECEITAS_ID // Canal silencioso
                    }
                    DateTimeUtils.TIPO_RECEITA_3D_ANTES -> {
                        finalTitle = "Receita Próxima do Vencimento"
                        finalMessage = "Sua receita do(a) Dr(a). $nomeMedico vence em 3 dias ($validade)."
                        channelId = PilloraApplication.CHANNEL_LEMBRETES_RECEITAS_ID // Canal silencioso
                    }
                    else -> {
                        Log.w("NotificationWorker", "Tipo de lembrete de receita inesperado: $tipoLembrete")
                        finalTitle = notificationTitle
                        finalMessage = notificationMessage
                        channelId = PilloraApplication.CHANNEL_LEMBRETES_RECEITAS_ID // Canal silencioso padrão
                    }
                }
                Log.d("NotificationWorker", "Mensagem final para receita ($tipoLembrete) = $finalMessage, canal: $channelId")
            }

            isConsulta -> {
                Log.d("NotificationWorker", "Processando como CONSULTA")
                val nome = recipientName.ifBlank { null }
                val especialidade = notificationTitle.replace("Consulta:", "", ignoreCase = true).trim()
                val horarioRealConsultaStr = if (horaConsulta >= 0 && minutoConsulta >= 0) String.format(Locale.getDefault(), "%02d:%02d", horaConsulta, minutoConsulta) else ""

                if (isConfirmacao) {
                    finalTitle = "Confirmação de Consulta"
                    finalMessage = if (nome != null) {
                        "$nome, você foi na consulta com $especialidade?"
                    } else {
                        "Você foi na consulta com $especialidade?"
                    }
                    channelId = PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID
                } else {
                    finalTitle = if (nome != null) {
                        "$nome, você tem consulta com: $especialidade"
                    } else {
                        "Você tem consulta com: $especialidade"
                    }

                    finalMessage = when (tipoLembrete) {
                        DateTimeUtils.TIPO_24H_ANTES -> "Amanhã às $horarioRealConsultaStr"
                        DateTimeUtils.TIPO_2H_ANTES -> "Hoje às $horarioRealConsultaStr"
                        else -> notificationMessage
                    }

                    channelId = PilloraApplication.CHANNEL_PRE_EVENTOS_ID
                }

                Log.d("NotificationWorker", "Mensagem final para consulta ($tipoLembrete) = $finalMessage, canal: $channelId")
            }

            isVacina -> {
                Log.d("NotificationWorker", "Processando como VACINA")
                val nome = recipientName.ifBlank { null }
                val nomeVacinaReal = nomeVacina.ifBlank { notificationTitle.replace("Vacina:", "").trim() }

                if (isConfirmacao) {
                    finalTitle = "Confirmação de Vacina"
                    finalMessage = if (nome != null) {
                        "$nome, você compareceu à vacina $nomeVacinaReal?"
                    } else {
                        "Você compareceu à vacina $nomeVacinaReal?"
                    }
                    channelId = PilloraApplication.CHANNEL_LEMBRETES_CONSULTAS_ID // Canal sem som de alarme
                    Log.d("NotificationWorker", "Mensagem final para CONFIRMAÇÃO vacina = $finalMessage, canal: $channelId")
                } else {
                    finalTitle = if (nome != null) {
                        "$nome, você tem vacina: $nomeVacinaReal"
                    } else {
                        "Você tem vacina: $nomeVacinaReal"
                    }
                    finalMessage = when (tipoLembrete) {
                        DateTimeUtils.TIPO_24H_ANTES -> "Amanhã às $horaVacina"
                        DateTimeUtils.TIPO_2H_ANTES -> "Hoje às $horaVacina"
                        else -> {
                            Log.w("NotificationWorker", "Tipo de lembrete pré-vacina inesperado: $tipoLembrete. Usando observação.")
                            notificationMessage
                        }
                    }
                    channelId = PilloraApplication.CHANNEL_PRE_EVENTOS_ID // Canal com sem de alarme
                    Log.d("NotificationWorker", "Mensagem final para PRÉ-VACINA ($tipoLembrete) = $finalMessage, canal: $channelId")
                }
            }

            else -> {
                Log.d("NotificationWorker", "Processando como MEDICAMENTO")
                val nome = recipientName.ifBlank { null }
                finalTitle = if (nome != null) {
                    "${nome}, hora de tomar ${notificationTitle.replace("Hora de:", "").trim()}"
                } else {
                    notificationTitle
                }
                finalMessage = notificationMessage
                channelId = PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID
                Log.d("NotificationWorker", "Mensagem final para medicamento = $finalMessage, canal: $channelId")
            }
        }

        Log.d("NotificationWorker", "Preparando notificação final: title=$finalTitle, message=$finalMessage, canal=$channelId")

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_pill)
            .setContentTitle(finalTitle)
            .setContentText(finalMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(finalMessage)) // Para textos mais longos
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        // Configurar prioridade, vibração e fullScreenIntent
        if (isReceita || (isConsulta && tipoLembrete == DateTimeUtils.TIPO_3H_DEPOIS) || (isVacina && isConfirmacao)) {
            Log.d("NotificationWorker", "Configurando notificação como PADRÃO (Receita, pós-consulta ou pós-vacina)")
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVibrate(longArrayOf(0, 300, 200, 300)) // Vibração padrão
        } else {
            Log.d("NotificationWorker", "Configurando notificação como ALARME (pré-consulta/pré-vacina ou medicamento)")
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000)) // Vibração de alarme
                .setLights(0xFF0000FF.toInt(), 1000, 500)
        }

        // Adicionar botões de ação APENAS quando necessário
        when {
            // *** NOVO: Botão para confirmação de Receita ***
            isReceita && tipoLembrete == DateTimeUtils.TIPO_RECEITA_CONFIRMACAO -> {
                Log.d("NotificationWorker", "Adicionando botão para notificação PÓS-RECEITA")
                val confirmarExcluirIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_RECEITA_CONFIRMADA_EXCLUIR
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_MEDICAMENTO_ID, recipeId) // Passa o recipeId
                }
                val confirmarExcluirPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 5, confirmarExcluirIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                builder.addAction(R.drawable.ic_action_check, "Sim, excluir receita", confirmarExcluirPendingIntent)
            }

            // Botões para confirmação de Consulta (APENAS para notificações 3h depois)
            isConsulta && isConfirmacao -> {
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
            }

            // Botões para confirmação de Vacina (APENAS para notificações de confirmação)
            isVacina && isConfirmacao -> {
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
            }

            // Botão para Medicamento (APENAS para medicamentos)
            !isConsulta && !isVacina && !isReceita -> {
                Log.d("NotificationWorker", "Adicionando botão 'Tomei' para MEDICAMENTO")
                val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                    action = ACTION_MEDICAMENTO_TOMADO
                    putExtra(EXTRA_LEMBRETE_ID, lembreteId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(EXTRA_MEDICAMENTO_ID, medicamentoId)
                }
                val tomarPendingIntent = PendingIntent.getBroadcast(applicationContext, notificationId * 10 + 0, tomarIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent)
            }

            else -> {
                Log.d("NotificationWorker", "Nenhum botão de ação adicionado para este tipo de notificação (isConsulta=$isConsulta, isVacina=$isVacina, isReceita=$isReceita, tipo=$tipoLembrete, isConfirmacao=$isConfirmacao)")
            }
        }

        // Exibir a notificação
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
            Log.d("NotificationWorker", "Notificação exibida com ID: $notificationId")
            return Result.success()
        } else {
            Log.e("NotificationWorker", "ERRO! Permissão POST_NOTIFICATIONS não concedida. Não foi possível exibir a notificação.")
            // Mesmo sem permissão, consideramos o trabalho como sucesso para não ficar tentando reenviar
            return Result.success()
        }
    }
}