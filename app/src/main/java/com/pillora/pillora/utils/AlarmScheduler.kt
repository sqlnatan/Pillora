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

    private const val TAG = "AlarmScheduler"

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
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, false)
            putExtra(NotificationWorker.EXTRA_IS_VACINA, false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var triggerAtMillis = lembrete.proximaOcorrenciaMillis
        val now = System.currentTimeMillis()

        // Ajuste para garantir que o alarme seja no futuro
        if (triggerAtMillis < now) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = triggerAtMillis
                set(Calendar.HOUR_OF_DAY, lembrete.hora)
                set(Calendar.MINUTE, lembrete.minuto)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                while (before(Calendar.getInstance().apply { timeInMillis = now })) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            triggerAtMillis = calendar.timeInMillis
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // setAndAllowWhileIdle garante que o alarme toque mesmo em economia de bateria (Doze Mode)
                // É o método recomendado para apps de saúde/medicamentos na Play Store.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarme agendado com setAndAllowWhileIdle para Lembrete ID: ${lembrete.id} em $triggerAtMillis")
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
}
