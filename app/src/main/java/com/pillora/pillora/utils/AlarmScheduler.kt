package com.pillora.pillora.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.receiver.AlarmReceiver
import com.pillora.pillora.PilloraApplication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    /**
     * Agenda um alarme para um lembrete.
     * Para MEDICAMENTOS: usa setExactAndAllowWhileIdle (alarme exato)
     * Para CONSULTAS, VACINAS, RECEITAS: usa setAndAllowWhileIdle (alarme inexato)
     */
    fun scheduleAlarm(context: Context, lembrete: Lembrete) {
        if (!lembrete.ativo) {
            Log.d(TAG, "Lembrete ${lembrete.id} está inativo, não agendando.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.dose)
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, lembrete.isConsulta)
            putExtra(NotificationWorker.EXTRA_IS_VACINA, lembrete.isVacina)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var triggerAtMillis = lembrete.proximaOcorrenciaMillis
        val now = System.currentTimeMillis()

        // Se o alarme está no passado, verificar se é por uma margem pequena (processamento)
        if (triggerAtMillis < now) {
            val diffSeconds = (now - triggerAtMillis) / 1000
            if (diffSeconds > 60) {
                // Se passou mais de 1 minuto, realmente está no passado e não deve agendar
                Log.w(TAG, "AVISO: Alarme muito antigo para lembrete ${lembrete.id}. Passou $diffSeconds segundos. Ignorando agendamento.")
                return
            } else {
                // Se passou menos de 1 minuto, é provavelmente devido ao tempo de processamento
                // Agendar para daqui a 5 segundos
                triggerAtMillis = now + 5000
                Log.d(TAG, "Alarme estava $diffSeconds segundos no passado. Ajustando para daqui a 5 segundos. Lembrete ID: ${lembrete.id}")
            }
        }

        try {
            // Determinar se é medicamento ou não (consulta, vacina, receita)
            val isMedicamento = !lembrete.isConsulta && !lembrete.isVacina && !lembrete.isReceita

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                if (isMedicamento) {
                    // Para medicamentos: usar alarme exato
                    // Verificar se temos permissão para alarmes exatos
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "Alarme EXATO agendado para MEDICAMENTO (Lembrete ID: ${lembrete.id}) em $triggerAtMillis")
                    } else {
                        // Fallback: usar alarme inexato se não tiver permissão
                        Log.w(TAG, "Permissão SCHEDULE_EXACT_ALARM não concedida. Usando alarme inexato para medicamento.")
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else {
                    // Para consultas, vacinas, receitas: usar alarme inexato
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    val tipo = when {
                        lembrete.isConsulta -> "CONSULTA"
                        lembrete.isVacina -> "VACINA"
                        lembrete.isReceita -> "RECEITA"
                        else -> "OUTRO"
                    }
                    Log.d(TAG, "Alarme INEXATO agendado para $tipo (Lembrete ID: ${lembrete.id}) em $triggerAtMillis")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0 - 11 (API 23-30)
                if (isMedicamento) {
                    // Para medicamentos: usar alarme exato
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarme EXATO agendado para MEDICAMENTO (Lembrete ID: ${lembrete.id}) em $triggerAtMillis")
                } else {
                    // Para consultas, vacinas, receitas: usar alarme inexato
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    val tipo = when {
                        lembrete.isConsulta -> "CONSULTA"
                        lembrete.isVacina -> "VACINA"
                        lembrete.isReceita -> "RECEITA"
                        else -> "OUTRO"
                    }
                    Log.d(TAG, "Alarme INEXATO agendado para $tipo (Lembrete ID: ${lembrete.id}) em $triggerAtMillis")
                }
            } else {
                // Android < 6.0 (API < 23)
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Alarme agendado (API < 23) para Lembrete ID: ${lembrete.id} em $triggerAtMillis")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro de permissão ao agendar alarme. Verifique se SCHEDULE_EXACT_ALARM foi concedida.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar alarme", e)
        }
    }

    fun cancelAlarm(context: Context, lembreteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembreteId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarme cancelado para Lembrete ID: $lembreteId")
        }
    }

    /**
     * Reagenda um alarme de medicamento para a próxima ocorrência.
     * Calcula automaticamente baseado no tipo de frequência (vezes_dia ou a_cada_x_horas).
     *
     * @param context Contexto da aplicação
     * @param lembrete Lembrete atual que precisa ser reagendado
     * @param medicine Medicamento associado (para verificar duração e tipo)
     * @return true se reagendou com sucesso, false se o tratamento acabou
     */
    suspend fun rescheduleNextOccurrence(
        context: Context,
        lembrete: Lembrete,
        medicine: Medicine
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando reagendamento para lembrete ${lembrete.id}")

        // Verificar se o medicamento tem alarmes habilitados
        if (!medicine.alarmsEnabled) {
            Log.d(TAG, "Alarmes desabilitados para medicamento ${medicine.id}. Não reagendando.")
            return@withContext false
        }

        val lembreteDao = (context.applicationContext as PilloraApplication).database.lembreteDao()

        // Calcular próxima ocorrência baseado no tipo de frequência
        val proximaOcorrencia = when (medicine.frequencyType) {
            "vezes_dia" -> calcularProximaOcorrenciaVezesDia(lembrete)
            "a_cada_x_horas" -> calcularProximaOcorrenciaIntervalo(lembrete, medicine)
            else -> {
                Log.e(TAG, "Tipo de frequência desconhecido: ${medicine.frequencyType}")
                return@withContext false
            }
        }

        if (proximaOcorrencia == null) {
            Log.d(TAG, "Não há próxima ocorrência para lembrete ${lembrete.id}.")
            lembreteDao.updateLembrete(lembrete.copy(ativo = false))
            return@withContext false
        }

        // Verificar se a próxima ocorrência está dentro do período de tratamento
        if (medicine.duration > 0) {
            val endDateCal = try {
                // CORRIGIDO: Aceitar formato com ou sem barras (igual ao DateTimeUtils)
                var formattedStartDate = medicine.startDate
                if (!medicine.startDate.contains("/") && medicine.startDate.length == 8 && medicine.startDate.all { it.isDigit() }) {
                    formattedStartDate = "${medicine.startDate.substring(0, 2)}/${medicine.startDate.substring(2, 4)}/${medicine.startDate.substring(4)}"
                }
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                val startDateCal = Calendar.getInstance().apply {
                    time = sdf.parse(formattedStartDate) ?: throw IllegalArgumentException("Invalid start date")
                }
                (startDateCal.clone() as Calendar).apply {
                    // Adicionar a duração completa (sem subtrair 1 dia)
                    // Isso garante que o último horário calculado seja respeitado
                    add(Calendar.DAY_OF_YEAR, medicine.duration)
                    // Ajustar para o fim do dia da data final
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao calcular data de fim: ${e.message}")
                return@withContext false
            }

            if (proximaOcorrencia > endDateCal.timeInMillis) {
                Log.d(TAG, "Próxima ocorrência está após o fim do tratamento. Desativando lembrete ${lembrete.id}.")
                lembreteDao.updateLembrete(lembrete.copy(ativo = false))
                return@withContext false
            }
        }

        // Atualizar o lembrete com a nova próxima ocorrência
        val lembreteAtualizado = lembrete.copy(proximaOcorrenciaMillis = proximaOcorrencia)
        lembreteDao.updateLembrete(lembreteAtualizado)

        // Reagendar o alarme
        scheduleAlarm(context, lembreteAtualizado)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        Log.d(TAG, "Lembrete ${lembrete.id} reagendado para ${sdf.format(proximaOcorrencia)}")
        return@withContext true
    }

    /**
     * Calcula a próxima ocorrência para medicamentos do tipo "vezes_dia".
     * Exemplo: 3x ao dia às 8h, 13h, 18h → próxima ocorrência é no mesmo horário do dia seguinte
     */
    private fun calcularProximaOcorrenciaVezesDia(lembrete: Lembrete): Long {
        val now = Calendar.getInstance()
        val proximaOcorrenciaCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, lembrete.hora)
            set(Calendar.MINUTE, lembrete.minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Se o horário de hoje já passou, agendar para amanhã
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return proximaOcorrenciaCalendar.timeInMillis
    }

    /**
     * Calcula a próxima ocorrência para medicamentos do tipo "a_cada_x_horas".
     * Exemplo: A cada 8 horas → adiciona 8 horas à última ocorrência
     */
    private fun calcularProximaOcorrenciaIntervalo(lembrete: Lembrete, medicine: Medicine): Long? {
        val intervalHours = medicine.intervalHours ?: return null
        if (intervalHours <= 0) return null

        val now = Calendar.getInstance()
        val proximaOcorrenciaCalendar = Calendar.getInstance().apply {
            timeInMillis = lembrete.proximaOcorrenciaMillis
            // Adicionar o intervalo
            add(Calendar.HOUR_OF_DAY, intervalHours)
        }

        // Se ainda estiver no passado (improvável, mas por segurança)
        while (proximaOcorrenciaCalendar.timeInMillis <= now.timeInMillis) {
            proximaOcorrenciaCalendar.add(Calendar.HOUR_OF_DAY, intervalHours)
        }

        return proximaOcorrenciaCalendar.timeInMillis
    }
}
