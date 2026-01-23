package com.pillora.pillora.utils

import android.content.Context
import android.util.Log
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.model.Recipe
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utilitário para sincronizar dados do Firebase com o banco local e agendar alarmes.
 * Usado após login, reinstalação ou quando os dados são carregados pela primeira vez.
 */
object SyncHelper {
    private const val TAG = "SyncHelper"

    /**
     * Sincroniza medicamentos do Firebase com o banco local e agenda alarmes.
     */
    suspend fun syncMedicines(context: Context, medicines: List<Medicine>) = withContext(Dispatchers.IO) {
        try {
            val lembreteDao = (context.applicationContext as PilloraApplication).database.lembreteDao()
            var syncedCount = 0

            medicines.forEach { medicine ->
                // Verificar se já existem lembretes para este medicamento
                val existingLembretes = lembreteDao.getLembretesByMedicamentoId(medicine.id ?: "")

                // Se não existem lembretes E o medicamento tem alarmes habilitados, criar
                if (existingLembretes.isEmpty() && medicine.alarmsEnabled && !medicine.id.isNullOrBlank()) {
                    createMedicineReminders(context, lembreteDao, medicine)
                    syncedCount++
                }
            }

            if (syncedCount > 0) {
                Log.d(TAG, "Sincronizados $syncedCount medicamentos com lembretes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar medicamentos", e)
        }
    }

    /**
     * Sincroniza consultas do Firebase com o banco local e agenda alarmes.
     */
    suspend fun syncConsultations(context: Context, consultations: List<Consultation>) = withContext(Dispatchers.IO) {
        try {
            val lembreteDao = (context.applicationContext as PilloraApplication).database.lembreteDao()
            var syncedCount = 0

            consultations.forEach { consultation ->
                // Verificar se já existem lembretes para esta consulta
                val existingLembretes = lembreteDao.getLembretesByMedicamentoId(consultation.id)

                // Se não existem lembretes E a consulta está ativa, criar
                if (existingLembretes.isEmpty() && consultation.isActive && consultation.id.isNotBlank()) {
                    createConsultationReminders(context, lembreteDao, consultation)
                    syncedCount++
                }
            }

            if (syncedCount > 0) {
                Log.d(TAG, "Sincronizadas $syncedCount consultas com lembretes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar consultas", e)
        }
    }

    /**
     * Sincroniza vacinas do Firebase com o banco local e agenda alarmes.
     */
    suspend fun syncVaccines(context: Context, vaccines: List<Vaccine>) = withContext(Dispatchers.IO) {
        try {
            val lembreteDao = (context.applicationContext as PilloraApplication).database.lembreteDao()
            var syncedCount = 0

            vaccines.forEach { vaccine ->
                // Verificar se já existem lembretes para esta vacina
                val existingLembretes = lembreteDao.getLembretesByMedicamentoId(vaccine.id)

                // Se não existem lembretes, criar (Vaccine não tem isActive)
                if (existingLembretes.isEmpty() && vaccine.id.isNotBlank()) {
                    createVaccineReminders(context, lembreteDao, vaccine)
                    syncedCount++
                }
            }

            if (syncedCount > 0) {
                Log.d(TAG, "Sincronizadas $syncedCount vacinas com lembretes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar vacinas", e)
        }
    }

    /**
     * Sincroniza receitas do Firebase com o banco local e agenda alarmes.
     */
    suspend fun syncRecipes(context: Context, recipes: List<Recipe>) = withContext(Dispatchers.IO) {
        try {
            val lembreteDao = (context.applicationContext as PilloraApplication).database.lembreteDao()
            var syncedCount = 0

            recipes.forEach { recipe ->
                // Verificar se já existem lembretes para esta receita
                val existingLembretes = lembreteDao.getLembretesByMedicamentoId(recipe.id ?: "")

                // Se não existem lembretes, criar (Recipe não tem isActive)
                if (existingLembretes.isEmpty() && !recipe.id.isNullOrBlank()) {
                    createRecipeReminders(context, lembreteDao, recipe)
                    syncedCount++
                }
            }

            if (syncedCount > 0) {
                Log.d(TAG, "Sincronizadas $syncedCount receitas com lembretes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar receitas", e)
        }
    }

    // ==================== FUNÇÕES PRIVADAS DE CRIAÇÃO DE LEMBRETES ====================

    private suspend fun createMedicineReminders(context: Context, lembreteDao: LembreteDao, medicine: Medicine) {
        try {
            when (medicine.frequencyType) {
                "vezes_dia" -> {
                    // Usar horarios (List<String>) ao invés de timesPerDay
                    val horarios = medicine.horarios ?: emptyList()

                    horarios.forEach { horarioStr ->
                        val parts = horarioStr.split(":")
                        if (parts.size == 2) {
                            val hora = parts[0].toIntOrNull()
                            val minuto = parts[1].toIntOrNull()

                            if (hora != null && minuto != null) {
                                val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                val dataInicioCalendar = Calendar.getInstance()
                                try {
                                    val parsedDate: Date? = sdfDate.parse(medicine.startDate)
                                    dataInicioCalendar.time = parsedDate ?: Date()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erro ao parsear startDate (${medicine.startDate}) para lembrete 'vezes_dia'.", e)
                                    dataInicioCalendar.time = Date()
                                }

                                val lembreteCalendar = Calendar.getInstance().apply {
                                    time = dataInicioCalendar.time
                                    set(Calendar.HOUR_OF_DAY, hora)
                                    set(Calendar.MINUTE, minuto)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }

                                while (lembreteCalendar.timeInMillis < System.currentTimeMillis()) {
                                    lembreteCalendar.add(Calendar.DAY_OF_MONTH, 1)
                                }
                                val proximaOcorrenciaMillis = lembreteCalendar.timeInMillis

                                val doseFormatada = "${medicine.dose} ${medicine.doseUnit ?: ""}".trim()

                                val lembrete = Lembrete(
                                    id = 0,
                                    medicamentoId = medicine.id ?: "",
                                    nomeMedicamento = medicine.name,
                                    recipientName = medicine.recipientName.ifBlank { null },
                                    hora = hora,
                                    minuto = minuto,
                                    dose = doseFormatada,
                                    observacao = medicine.notes,
                                    proximaOcorrenciaMillis = proximaOcorrenciaMillis,
                                    ativo = true
                                )

                                val lembreteId = lembreteDao.insertLembrete(lembrete)
                                AlarmScheduler.scheduleAlarm(context, lembrete.copy(id = lembreteId))
                                Log.d(TAG, "Lembrete criado para medicamento ${medicine.name} às $hora:$minuto")
                            }
                        }
                    }
                }
                "a_cada_x_horas" -> {
                    // Criar apenas um lembrete (o reagendamento é automático)
                    val intervalH = medicine.intervalHours
                    val startTime = medicine.startTime

                    if (intervalH != null && intervalH > 0 && !startTime.isNullOrBlank()) {
                        val ocorrencias = DateTimeUtils.calcularProximasOcorrenciasIntervalo(
                            medicine.startDate,
                            startTime,
                            intervalH,
                            medicine.duration
                        )

                        if (ocorrencias.isNotEmpty()) {
                            val proximaOcorrencia = ocorrencias.first()
                            val calendar = Calendar.getInstance().apply { timeInMillis = proximaOcorrencia }

                            val doseFormatada = "${medicine.dose} ${medicine.doseUnit ?: ""}".trim()

                            val lembrete = Lembrete(
                                id = 0,
                                medicamentoId = medicine.id ?: "",
                                nomeMedicamento = medicine.name,
                                recipientName = medicine.recipientName.ifBlank { null },
                                hora = calendar.get(Calendar.HOUR_OF_DAY),
                                minuto = calendar.get(Calendar.MINUTE),
                                dose = doseFormatada,
                                observacao = medicine.notes,
                                proximaOcorrenciaMillis = proximaOcorrencia,
                                ativo = true
                            )

                            val lembreteId = lembreteDao.insertLembrete(lembrete)
                            AlarmScheduler.scheduleAlarm(context, lembrete.copy(id = lembreteId))
                            Log.d(TAG, "Lembrete criado para medicamento ${medicine.name} (intervalo ${intervalH}h)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar lembretes para medicamento ${medicine.name}", e)
        }
    }

    private suspend fun createConsultationReminders(context: Context, lembreteDao: LembreteDao, consultation: Consultation) {
        try {
            val dateTimeParts = consultation.dateTime.split(" ")
            val consultaDate = dateTimeParts.getOrElse(0) { "" }
            val consultaTime = dateTimeParts.getOrElse(1) { "" }

            if (consultaDate.isEmpty() || consultaTime.isEmpty()) {
                Log.w(TAG, "Data ou hora da consulta inválida: ${consultation.dateTime}")
                return
            }

            // Extrair hora e minuto real da consulta
            val consultaTimeParts = consultaTime.split(":")
            val horaConsultaReal = consultaTimeParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val minutoConsultaReal = consultaTimeParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0

            val lembretesInfo = DateTimeUtils.calcularLembretesConsulta(consultaDate, consultaTime)

            lembretesInfo.forEach { lembreteInfo ->
                val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                val lembrete = Lembrete(
                    medicamentoId = consultation.id,
                    nomeMedicamento = "Consulta: ${consultation.specialty}",
                    recipientName = consultation.patientName.ifBlank { null },
                    hora = calendar.get(Calendar.HOUR_OF_DAY),
                    minuto = calendar.get(Calendar.MINUTE),
                    dose = lembreteInfo.tipo,
                    observacao = "Dr(a). ${consultation.doctorName} em ${consultation.location}. ${if (consultation.observations.isNotEmpty()) "Obs: ${consultation.observations}" else ""}",
                    proximaOcorrenciaMillis = lembreteInfo.timestamp,
                    ativo = true,
                    isConsulta = true,
                    isConfirmacao = lembreteInfo.tipo == DateTimeUtils.TIPO_3H_DEPOIS,
                    isSilencioso = consultation.isSilencioso,
                    toqueAlarmeUri = consultation.toqueAlarmeUri
                )

                val lembreteId = lembreteDao.insertLembrete(lembrete)
                val lembreteComId = lembrete.copy(id = lembreteId)

                // Agendar baseado no tipo
                when (lembreteInfo.tipo) {
                    DateTimeUtils.TIPO_3H_DEPOIS -> {
                        // Usar WorkManager para confirmação
                        agendarNotificacaoPosConsulta(context, lembreteComId, horaConsultaReal, minutoConsultaReal)
                    }
                    DateTimeUtils.TIPO_24H_ANTES, DateTimeUtils.TIPO_2H_ANTES -> {
                        // Usar AlarmManager para lembretes pré-consulta
                        agendarAlarmeConsulta(context, lembreteComId, horaConsultaReal, minutoConsultaReal)
                    }
                }
                Log.d(TAG, "Lembrete criado para consulta ${consultation.specialty} - ${lembreteInfo.tipo}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar lembretes para consulta ${consultation.specialty}", e)
        }
    }

    private suspend fun createVaccineReminders(context: Context, lembreteDao: LembreteDao, vaccine: Vaccine) {
        try {
            val vacinaDate = vaccine.reminderDate
            val vacinaTime = vaccine.reminderTime

            if (vacinaDate.isEmpty() || vacinaTime.isEmpty()) {
                Log.w(TAG, "Data ou hora da vacina inválida: $vacinaDate $vacinaTime")
                return
            }

            val lembretesInfo = DateTimeUtils.calcularLembretesVacina(vacinaDate, vacinaTime)

            lembretesInfo.forEach { lembreteInfo ->
                val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                val tipoLembrete = lembreteInfo.tipo
                val isConfirmacao = tipoLembrete == DateTimeUtils.TIPO_CONFIRMACAO

                val lembrete = Lembrete(
                    medicamentoId = vaccine.id,
                    nomeMedicamento = "Vacina: ${vaccine.name}",
                    recipientName = vaccine.patientName.ifBlank { null },
                    hora = calendar.get(Calendar.HOUR_OF_DAY),
                    minuto = calendar.get(Calendar.MINUTE),
                    dose = tipoLembrete,
                    observacao = "Local: ${vaccine.location}. ${if (vaccine.notes.isNotEmpty()) "Obs: ${vaccine.notes}" else ""}",
                    proximaOcorrenciaMillis = lembreteInfo.timestamp,
                    ativo = true,
                    isVacina = true,
                    isConfirmacao = isConfirmacao
                )

                val lembreteId = lembreteDao.insertLembrete(lembrete)
                val lembreteComId = lembrete.copy(id = lembreteId)

                // Agendar baseado no tipo
                when (tipoLembrete) {
                    DateTimeUtils.TIPO_CONFIRMACAO -> {
                        agendarNotificacaoPosVacina(context, lembreteComId, vaccine.reminderTime)
                    }
                    DateTimeUtils.TIPO_24H_ANTES, DateTimeUtils.TIPO_2H_ANTES -> {
                        agendarAlarmeVacina(context, lembreteComId, vaccine.reminderTime)
                    }
                }
                Log.d(TAG, "Lembrete criado para vacina ${vaccine.name} - $tipoLembrete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar lembretes para vacina ${vaccine.name}", e)
        }
    }

    private suspend fun createRecipeReminders(context: Context, lembreteDao: LembreteDao, recipe: Recipe) {
        try {
            val lembretesInfo = DateTimeUtils.calcularLembretesReceita(recipe.validityDate)

            lembretesInfo.forEach { lembreteInfo ->
                val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                val isConfirmacao = lembreteInfo.tipo == DateTimeUtils.TIPO_RECEITA_CONFIRMACAO

                val lembrete = Lembrete(
                    medicamentoId = recipe.id ?: "",
                    nomeMedicamento = "Receita Dr(a). ${recipe.doctorName}",
                    recipientName = recipe.patientName.ifBlank { null },
                    hora = calendar.get(Calendar.HOUR_OF_DAY),
                    minuto = calendar.get(Calendar.MINUTE),
                    dose = lembreteInfo.tipo,
                    observacao = "Validade: ${recipe.validityDate}",
                    proximaOcorrenciaMillis = lembreteInfo.timestamp,
                    ativo = true,
                    isReceita = true,
                    isConfirmacao = isConfirmacao
                )

                val lembreteId = lembreteDao.insertLembrete(lembrete)
                agendarNotificacaoReceita(context, lembrete.copy(id = lembreteId))
                Log.d(TAG, "Lembrete criado para receita Dr(a). ${recipe.doctorName} - ${lembreteInfo.tipo}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar lembretes para receita Dr(a). ${recipe.doctorName}", e)
        }
    }

    // ==================== FUNÇÕES AUXILIARES DE AGENDAMENTO ====================
    // (Copiadas dos ViewModels correspondentes)

    private fun agendarNotificacaoPosConsulta(context: Context, lembrete: Lembrete, horaConsultaReal: Int, minutoConsultaReal: Int) {
        val workData = androidx.work.Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_CONSULTA_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            .putInt(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsultaReal)
            .putInt(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsultaReal)
            .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, true)
            .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, true)
            .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
            .build()

        var delay = lembrete.proximaOcorrenciaMillis - System.currentTimeMillis()
        if (delay <= 0) delay = 5000

        val request = androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("consulta_${lembrete.medicamentoId}_${lembrete.id}")
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(request)
    }

    private fun agendarAlarmeConsulta(context: Context, lembrete: Lembrete, horaConsultaReal: Int, minutoConsultaReal: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.pillora.pillora.receiver.AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_CONSULTA_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsultaReal)
            putExtra(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsultaReal)
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, true)
            putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
        }

        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = lembrete.proximaOcorrenciaMillis
        if (triggerAtMillis < System.currentTimeMillis()) return

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar alarme de consulta", e)
        }
    }

    private fun agendarNotificacaoPosVacina(context: Context, lembrete: Lembrete, horaVacina: String) {
        val workData = androidx.work.Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_VACINA_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            .putString(NotificationWorker.EXTRA_VACCINE_TIME, horaVacina)
            .putBoolean(NotificationWorker.EXTRA_IS_VACINA, true)
            .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, true)
            .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
            .build()

        var delay = lembrete.proximaOcorrenciaMillis - System.currentTimeMillis()
        if (delay <= 0) delay = 5000

        val request = androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("vacina_${lembrete.medicamentoId}_${lembrete.id}")
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(request)
    }

    private fun agendarAlarmeVacina(context: Context, lembrete: Lembrete, horaVacina: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.pillora.pillora.receiver.AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_VACINA_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_VACCINE_TIME, horaVacina)
            putExtra(NotificationWorker.EXTRA_IS_VACINA, true)
            putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
        }

        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = lembrete.proximaOcorrenciaMillis
        if (triggerAtMillis < System.currentTimeMillis()) return

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar alarme de vacina", e)
        }
    }

    private fun agendarNotificacaoReceita(context: Context, lembrete: Lembrete) {
        val workData = androidx.work.Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            .putBoolean(NotificationWorker.EXTRA_IS_RECEITA, true)
            .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, lembrete.isConfirmacao)
            .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
            .build()

        var delay = lembrete.proximaOcorrenciaMillis - System.currentTimeMillis()
        if (delay <= 0) delay = 5000

        val request = androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .addTag("receita_${lembrete.medicamentoId}_${lembrete.id}")
            .build()

        androidx.work.WorkManager.getInstance(context).enqueue(request)
    }
}
