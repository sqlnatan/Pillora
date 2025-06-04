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
import com.pillora.pillora.workers.NotificationWorker

object AlarmScheduler {

    fun scheduleAlarm(context: Context, lembrete: Lembrete) {
        Log.e("PILLORA_DEBUG", "Agendando alarme para lembreteId: ${lembrete.id}, medicamentoId: ${lembrete.medicamentoId}")
        Log.e("PILLORA_DEBUG", "Detalhes do lembrete: hora=${lembrete.hora}, minuto=${lembrete.minuto}, proximaOcorrencia=${lembrete.proximaOcorrenciaMillis}")
        Log.e("PILLORA_DEBUG", "Data/hora do alarme: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(lembrete.proximaOcorrenciaMillis))}")


        if (!lembrete.ativo) {
            Log.e("PILLORA_DEBUG", "Lembrete ${lembrete.id} está inativo, não agendando.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            // Passar todos os dados necessários para o AlarmReceiver
            // Usar constantes do NotificationWorker para consistência
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            // CORREÇÃO: Garantir que o título e mensagem corretos sejam passados
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento) // nomeMedicamento já contém "Hora de: ..." ou similar
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.dose) // Passar a dose/observação aqui
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, false) // Explicitar que NÃO é consulta
            putExtra(NotificationWorker.EXTRA_IS_VACINA, false) // Explicitar que NÃO é vacina
            // Passar tipo de lembrete (pode ser útil para diferenciar no futuro, mas não essencial para medicação agora)
            // putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, "MEDICAMENTO") // Exemplo
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

        Log.e("PILLORA_DEBUG", "Verificando permissões para agendar alarme...")


        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e("PILLORA_DEBUG", "Não pode agendar alarmes exatos. O usuário precisa conceder permissão.")
                // A MainActivity já deve ter guiado o usuário para as configurações.
                // Se chegou aqui sem permissão, o alarme não será agendado.
                return
            }

            // Usar setAlarmClock para garantir que o alarme toque mesmo em modo de economia de bateria
            // Android 6.0 (M) ou superior
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

                Log.e("PILLORA_DEBUG", "Alarme agendado com sucesso para lembreteId: ${lembrete.id} em ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(triggerAtMillis))}")


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

            Log.d("AlarmScheduler", "Alarme agendado para Lembrete ID: ${lembrete.id} em $triggerAtMillis (${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(triggerAtMillis))})")
        } catch (se: SecurityException) {
            Log.e("PILLORA_DEBUG", "SecurityException ao agendar alarme. Verifique permissões.", se)

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
