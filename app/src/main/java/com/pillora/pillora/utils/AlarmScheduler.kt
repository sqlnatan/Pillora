package com.pillora.pillora.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.receiver.AlarmReceiver
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {

    fun scheduleAlarm(context: Context, lembrete: Lembrete) {
        if (!lembrete.ativo) {
            Log.d("AlarmScheduler", "Lembrete ${lembrete.id} está inativo, não agendando.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            // Passar todos os dados necessários para o AlarmReceiver
            putExtra("LEMBRETE_ID", lembrete.id)
            putExtra("MEDICAMENTO_ID", lembrete.medicamentoId) // Adicionado para o HandleNotificationActionWorker
            putExtra("NOTIFICATION_TITLE", "Hora de: ${lembrete.nomeMedicamento}")
            putExtra("NOTIFICATION_MESSAGE", "Dose: ${lembrete.dose}") // A dose já vem formatada de MedicineFormScreen
            putExtra("RECIPIENT_NAME", lembrete.recipientName) // Adicionado
            putExtra("PROXIMA_OCORRENCIA_MILLIS", lembrete.proximaOcorrenciaMillis) // Adicionado para NotificationWorker
            putExtra("HORA", lembrete.hora) // Adicionado para NotificationWorker
            putExtra("MINUTO", lembrete.minuto) // Adicionado para NotificationWorker
            // Adicionar flag para indicar que este é um alarme de medicamento
            putExtra("IS_MEDICINE_ALARM", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(), // Usar ID do lembrete como request code único
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var triggerAtMillis = lembrete.proximaOcorrenciaMillis
        val now = System.currentTimeMillis()

        // Se a próxima ocorrência já passou, tenta ajustar para a próxima válida.
        if (triggerAtMillis < now) {
            Log.w("AlarmScheduler", "Lembrete ${lembrete.id} com proximaOcorrenciaMillis ($triggerAtMillis) no passado (agora: $now). Ajustando...")
            val calendar = Calendar.getInstance().apply {
                timeInMillis = triggerAtMillis
                // Mantém a hora e minuto originais do lembrete
                set(Calendar.HOUR_OF_DAY, lembrete.hora)
                set(Calendar.MINUTE, lembrete.minuto)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // Avança dia a dia até encontrar uma data/hora futura
                while (before(Calendar.getInstance().apply { timeInMillis = now })) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            triggerAtMillis = calendar.timeInMillis
            Log.d("AlarmScheduler", "ProximaOcorrenciaMillis ajustada para: $triggerAtMillis")
            // Seria ideal atualizar o lembrete no banco de dados com este novo triggerAtMillis,
            // mas isso deve ser feito com cuidado para evitar loops se o AlarmScheduler for chamado por um Worker
            // que também atualiza o lembrete.
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Não pode agendar alarmes exatos. O usuário precisa conceder permissão.")
                // A MainActivity já deve ter guiado o usuário para as configurações.
                // Se chegou aqui sem permissão, o alarme não será agendado.
                return
            }

            // Usar setAlarmClock para garantir que o alarme toque mesmo em modo de economia de bateria
            if (Build.VERSION.SDK_INT >= 23) { // Android 6.0 (M) ou superior
                try {
                    // Criar um PendingIntent para o showIntent (apenas para o AlarmClockInfo)
                    val showIntent = Intent(context, AlarmReceiver::class.java)
                    val showPendingIntent = PendingIntent.getBroadcast(
                        context,
                        lembrete.id.toInt() + 100000, // Usar um request code diferente do alarme principal
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Usar setAlarmClock para maior prioridade
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

                    Log.d("AlarmScheduler", "Alarme agendado com setAlarmClock para Lembrete ID: ${lembrete.id}")
                } catch (e: Exception) {
                    Log.e("AlarmScheduler", "Erro ao agendar com setAlarmClock, tentando fallback", e)
                    // Fallback para setExactAndAllowWhileIdle
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d("AlarmScheduler", "Alarme agendado com setExactAndAllowWhileIdle para Lembrete ID: ${lembrete.id}")
                }
            } else {
                // Para versões mais antigas do Android
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d("AlarmScheduler", "Alarme agendado com setExact para Lembrete ID: ${lembrete.id}")
            }

            Log.d("AlarmScheduler", "Alarme agendado para Lembrete ID: ${lembrete.id} em $triggerAtMillis (${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(triggerAtMillis))})")
        } catch (se: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException ao agendar alarme. Verifique permissões (SCHEDULE_EXACT_ALARM ou USE_EXACT_ALARM).", se)

            // Último fallback para set normal
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d("AlarmScheduler", "Último fallback: Alarme agendado com set para Lembrete ID: ${lembrete.id}")
            } catch (e2: Exception) {
                Log.e("AlarmScheduler", "Erro no último fallback", e2)
            }
        }
    }

    fun cancelAlarm(context: Context, lembreteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        // Para cancelar, o PendingIntent deve ser o mesmo que foi usado para agendar (mesmo request code e intent filter)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembreteId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // É importante cancelar o PendingIntent também
            Log.d("AlarmScheduler", "Alarme cancelado para Lembrete ID: $lembreteId")
        } else {
            Log.d("AlarmScheduler", "PendingIntent não encontrado para cancelar o alarme do Lembrete ID: $lembreteId. Pode já ter sido cancelado ou nunca agendado com este ID.")
        }
    }
}
