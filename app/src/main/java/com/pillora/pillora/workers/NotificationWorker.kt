package com.pillora.pillora.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillora.pillora.MainActivity // Sua Activity principal
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.R // Importe seu R
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.receiver.NotificationActionReceiver // Criaremos este receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class NotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val ACTION_MEDICAMENTO_TOMADO = "com.pillora.pillora.ACTION_MEDICAMENTO_TOMADO"
        const val ACTION_MEDICAMENTO_NAO_TOMADO = "com.pillora.pillora.ACTION_MEDICAMENTO_NAO_TOMADO"
        const val EXTRA_LEMBRETE_ID = "EXTRA_LEMBRETE_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val lembreteId = inputData.getLong("LEMBRETE_ID", -1L)
        var notificationTitle = inputData.getString("NOTIFICATION_TITLE")
        var notificationMessage = inputData.getString("NOTIFICATION_MESSAGE")
        val notificationId = lembreteId.toInt() // Usar ID do lembrete como ID da notificação

        Log.d("NotificationWorker", "Iniciando trabalho para lembreteId: $lembreteId")

        if (lembreteId == -1L) {
            Log.e("NotificationWorker", "ID do Lembrete inválido.")
            return@withContext Result.failure()
        }

        val lembreteDao = AppDatabase.getDatabase(applicationContext).lembreteDao()
        val lembrete = lembreteDao.getLembreteById(lembreteId)

        if (lembrete == null || !lembrete.ativo) {
            Log.d("NotificationWorker", "Lembrete não encontrado, inativo ou já processado: $lembreteId")
            // Opcional: Cancelar futuros alarmes se o lembrete foi desativado/deletado
            return@withContext Result.success() // Ou failure() se isso for um erro inesperado
        }

        // Atualizar título e mensagem com dados do lembrete se não foram passados ou para ter info mais rica
        notificationTitle = "Hora de tomar: ${lembrete.nomeMedicamento}"
        notificationMessage = "Dose: ${lembrete.dose}" + (lembrete.observacao?.let { " (${it})" } ?: "")

        // Intent para abrir o app ao clicar na notificação
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Você pode adicionar extras para navegar para uma tela específica se desejar
            // putExtra("NAVIGATE_TO", "lembrete_detail")
            // putExtra("LEMBRETE_ID", lembreteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId, // Request code único por notificação
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação "Tomei"
        val tomarIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
            action = ACTION_MEDICAMENTO_TOMADO
            putExtra(EXTRA_LEMBRETE_ID, lembreteId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val tomarPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            notificationId * 10 + 1, // Request code único
            tomarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação "Não Tomei" (ou Adiar, etc. - por simplicidade, vamos apenas dispensar)
        // Se quiser uma ação "Não tomei" que faça algo, crie outro PendingIntent similar ao "tomarIntent"

        val builder = NotificationCompat.Builder(applicationContext, PilloraApplication.CHANNEL_LEMBRETES_MEDICAMENTOS_ID)
            .setSmallIcon(R.drawable.ic_notification_pill) // CRIE ESTE ÍCONE em drawable!
            .setContentTitle(notificationTitle)
            .setContentText(notificationMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Remove a notificação ao ser clicada
            .addAction(R.drawable.ic_action_check, "Tomei", tomarPendingIntent) // CRIE ESTE ÍCONE
        // .addAction(R.drawable.ic_action_snooze, "Adiar", adiarPendingIntent) // Exemplo para adiar

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("NotificationWorker", "Permissão POST_NOTIFICATIONS não concedida.")
            // Idealmente, a permissão já deveria ter sido solicitada antes de agendar alarmes.
            // Se chegar aqui, é uma falha no fluxo de permissões.
            return@withContext Result.failure()
        }

        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
        Log.d("NotificationWorker", "Notificação exibida para lembreteId: $lembreteId, notificationId: $notificationId")

        // Reagendar o próximo alarme se for um lembrete recorrente
        // Esta lógica será mais complexa e ficará no AlarmSchedulerHelper ou ViewModel
        // Por agora, apenas marcamos como sucesso. O reagendamento virá da ação "Tomei" ou de um helper.

        return@withContext Result.success()
    }
}
