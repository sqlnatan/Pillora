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

object AlarmScheduler {

    fun scheduleAlarm(context: Context, lembrete: Lembrete) {
        if (!lembrete.ativo) {
            Log.d("AlarmScheduler", "Lembrete ${lembrete.id} está inativo, não agendando.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("LEMBRETE_ID", lembrete.id)
            putExtra("NOTIFICATION_TITLE", "Hora de: ${lembrete.nomeMedicamento}")
            putExtra("NOTIFICATION_MESSAGE", "Dose: ${lembrete.dose}")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(), // Usar ID do lembrete como request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Garantir que o alarme não seja agendado para o passado
        var triggerAtMillis = lembrete.proximaOcorrenciaMillis
        if (triggerAtMillis < System.currentTimeMillis()) {
            Log.w("AlarmScheduler", "Lembrete ${lembrete.id} com proximaOcorrenciaMillis no passado. Tentando ajustar...")
            // Lógica de ajuste (simplificada: se for hoje mas passou a hora, agenda para amanhã na mesma hora)
            // TODO: Melhorar esta lógica de ajuste para ser mais robusta com diasDaSemana
            val calendar = Calendar.getInstance().apply {
                timeInMillis = triggerAtMillis
                set(Calendar.HOUR_OF_DAY, lembrete.hora)
                set(Calendar.MINUTE, lembrete.minuto)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                while(before(Calendar.getInstance())){
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            triggerAtMillis = calendar.timeInMillis
            // Atualizar no banco de dados também seria bom aqui, mas pode causar loop se chamado de um worker que já atualiza
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Não pode agendar alarmes exatos. O usuário precisa conceder permissão.")
                // TODO: Guiar usuário para as configurações para conceder a permissão SCHEDULE_EXACT_ALARM
                // Por enquanto, tentaremos um alarme inexato como fallback, ou falhar.
                // alarmManager.setWindow() // Exemplo de alarme menos exato
                // Ou notificar o usuário que o alarme não pode ser preciso.
                return // Ou lançar uma exceção/informar o usuário
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Alarme agendado para Lembrete ID: ${lembrete.id} em $triggerAtMillis")
        } catch (se: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException ao agendar alarme. Verifique permissões.", se)
            // TODO: Lidar com a falta de permissão (USE_EXACT_ALARM ou SCHEDULE_EXACT_ALARM)
        }
    }

    fun cancelAlarm(context: Context, lembreteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembreteId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE // FLAG_NO_CREATE para apenas cancelar se existir
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Alarme cancelado para Lembrete ID: $lembreteId")
        }
    }
}
