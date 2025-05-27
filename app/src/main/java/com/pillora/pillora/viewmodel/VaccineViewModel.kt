package com.pillora.pillora.viewmodel

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.receiver.AlarmReceiver
import com.pillora.pillora.repository.VaccineRepository
import com.pillora.pillora.utils.AlarmScheduler
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class VaccineViewModel : ViewModel() {

    private val tag = "VaccineViewModel"
    private val auth = FirebaseAuth.getInstance()

    // Form fields state
    val name = mutableStateOf("")
    val reminderDate = mutableStateOf("") // Format DD/MM/YYYY
    val reminderTime = mutableStateOf("") // Format HH:MM
    val location = mutableStateOf("")
    val notes = mutableStateOf("")
    val patientName = mutableStateOf("")

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private var currentVaccineId: String? = null
    private var originalUserId: String? = null // Store the original userId for updates
    private var isEditing: Boolean = false

    // Date format used throughout this ViewModel
    private val internalDateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            isLenient = false
        }
    }

    // --- Field Change Handlers (Public for UI Binding/Events) ---
    fun onNameChange(newName: String) {
        name.value = newName
    }

    private fun onReminderDateChange(newDate: String) {
        reminderDate.value = newDate
    }

    private fun onReminderTimeChange(newTime: String) {
        reminderTime.value = newTime
    }

    fun onLocationChange(newLocation: String) {
        location.value = newLocation
    }

    fun onNotesChange(newNotes: String) {
        notes.value = newNotes
    }

    fun onPatientNameChange(newName: String) {
        patientName.value = newName
    }

    // --- Date/Time Picker Logic ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        try {
            if (reminderDate.value.isNotEmpty()) {
                calendar.time = internalDateFormat.parse(reminderDate.value) ?: Date()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing date for DatePicker: ${reminderDate.value}", e)
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                onReminderDateChange(internalDateFormat.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
        var initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        var initialMinute = calendar.get(Calendar.MINUTE)
        try {
            if (reminderTime.value.isNotEmpty()) {
                val timeParts = reminderTime.value.split(":")
                if (timeParts.size == 2) {
                    initialHour = timeParts[0].toInt()
                    initialMinute = timeParts[1].toInt()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing time for TimePicker: ${reminderTime.value}", e)
        }

        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onReminderTimeChange(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
            },
            initialHour,
            initialMinute,
            true // Use 24-hour format
        ).show()
    }

    // --- Load Existing Vaccine for Editing ---
    fun loadVaccine(vaccineId: String) {
        if (vaccineId == currentVaccineId && name.value.isNotEmpty()) return
        resetFormFields()
        currentVaccineId = vaccineId
        isEditing = true
        _isLoading.value = true
        _error.value = null
        Log.d(tag, "Loading vaccine with ID: $vaccineId")
        VaccineRepository.getVaccineById(vaccineId, {
                vaccine ->
            if (vaccine != null) {
                Log.d(tag, "Vaccine loaded: ${vaccine.name}, UserID: ${vaccine.userId}")
                name.value = vaccine.name
                reminderDate.value = vaccine.reminderDate
                reminderTime.value = vaccine.reminderTime
                location.value = vaccine.location
                notes.value = vaccine.notes
                patientName.value = vaccine.patientName
                originalUserId = vaccine.userId // Store the original userId
            } else {
                Log.w(tag, "Vaccine with ID $vaccineId not found")
                _error.value = "Lembrete de vacina não encontrado."
                resetFormFields()
            }
            _isLoading.value = false
        }, {
                exception ->
            Log.e(tag, "Error loading vaccine", exception)
            _error.value = "Erro ao carregar lembrete: ${exception.message}"
            _isLoading.value = false
            resetFormFields()
        })
    }

    // --- Save/Update Vaccine ---
    // Modificado para aceitar LembreteDao
    fun saveVaccine(context: Context, lembreteDao: LembreteDao) {
        if (!validateInputs()) {
            return
        }

        _isLoading.value = true
        _error.value = null

        val currentUserIdAuth = auth.currentUser?.uid
        if (currentUserIdAuth == null) {
            _error.value = "Usuário não autenticado."
            _isLoading.value = false
            return
        }

        // Use the stored originalUserId if editing, otherwise get current user ID for new vaccine
        val userIdToSave = if (isEditing) {
            originalUserId ?: run {
                Log.e(tag, "Update failed: Original User ID is missing.")
                _error.value = "Erro interno: ID do usuário original ausente."
                _isLoading.value = false
                return
            }
        } else {
            currentUserIdAuth
        }

        val vaccine = Vaccine(
            id = currentVaccineId ?: "",
            userId = userIdToSave, // Use the determined userId
            name = name.value.trim(),
            patientName = patientName.value.trim(),
            reminderDate = reminderDate.value,
            reminderTime = reminderTime.value,
            location = location.value.trim(),
            notes = notes.value.trim()
        )

        val onSuccess = { newVaccineId: String ->
            Log.d(tag, "Vaccine ${if (isEditing) "updated" else "added"} successfully with ID: $newVaccineId")

            // Agendar ou atualizar lembretes para a vacina
            if (isEditing) {
                atualizarLembretesVacina(context, lembreteDao, currentVaccineId!!, vaccine)
            } else {
                agendarLembretesVacina(context, lembreteDao, newVaccineId, vaccine)
            }

            _isLoading.value = false
            resetFormFields()
            _navigateBack.value = true
        }

        val onFailure = { exception: Exception ->
            Log.e(tag, "Error ${if (isEditing) "updating" else "adding"} vaccine", exception)
            _isLoading.value = false
            // Use the specific error message from the repository if available
            _error.value = exception.message ?: "Erro desconhecido ao salvar."
        }

        viewModelScope.launch {
            if (isEditing) {
                Log.d(tag, "Attempting to update vaccine: ${vaccine.id} with UserID: ${vaccine.userId}")
                if (vaccine.id.isNotEmpty()) {
                    if (vaccine.userId != currentUserIdAuth) {
                        Log.e(tag, "Mismatch between vaccine userId (${vaccine.userId}) and auth userId ($currentUserIdAuth)")
                        onFailure(SecurityException("Erro de autorização ao preparar atualização."))
                        return@launch
                    }
                    VaccineRepository.updateVaccine(vaccine, { onSuccess(vaccine.id) }, onFailure)
                } else {
                    Log.e(tag, "Update failed: Vaccine ID is missing.")
                    onFailure(IllegalStateException("ID da vacina ausente para atualização."))
                }
            } else {
                Log.d(tag, "Attempting to add new vaccine: ${vaccine.name} with UserID: ${vaccine.userId}")
                VaccineRepository.addVaccine(vaccine, onSuccess, onFailure)
            }
        }
    }

    // --- Lembrete Scheduling Logic (Similar to ConsultationViewModel) ---

    // Função para agendar lembretes de vacina
    private fun agendarLembretesVacina(context: Context, lembreteDao: LembreteDao, vaccineId: String, vaccine: Vaccine) {
        viewModelScope.launch {
            try {
                val vacinaDate = vaccine.reminderDate
                val vacinaTime = vaccine.reminderTime

                if (vacinaDate.isEmpty() || vacinaTime.isEmpty()) {
                    Log.e(tag, "Data ou hora da vacina inválida: $vacinaDate $vacinaTime")
                    return@launch
                }

                // Calcular timestamps e tipos para os lembretes
                val lembretesInfo = DateTimeUtils.calcularLembretesVacina(
                    vacinaDateString = vacinaDate,
                    vacinaTimeString = vacinaTime
                )

                if (lembretesInfo.isEmpty()) {
                    Log.e(tag, "Nenhum lembrete calculado para vacina $vaccineId")
                    return@launch
                }
                Log.d(tag, "Calculados ${lembretesInfo.size} lembretes para vacina $vaccineId")

                var lembretesProcessadosComSucesso = true
                lembretesInfo.forEach { lembreteInfo ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                    val tipoLembrete = lembreteInfo.tipo
                    val isConfirmacao = tipoLembrete == DateTimeUtils.TIPO_CONFIRMACAO

                    val lembrete = Lembrete(
                        id = 0, // Será gerado automaticamente
                        medicamentoId = vaccineId, // Usando medicamentoId para armazenar o ID da vacina
                        nomeMedicamento = "Vacina: ${vaccine.name}", // Prefixo para identificar que é vacina
                        recipientName = vaccine.patientName,
                        hora = calendar.get(Calendar.HOUR_OF_DAY),
                        minuto = calendar.get(Calendar.MINUTE),
                        dose = tipoLembrete, // Usando dose para armazenar o tipo de lembrete
                        observacao = "Local: ${vaccine.location}. ${if (vaccine.notes.isNotEmpty()) "Obs: ${vaccine.notes}" else ""}",
                        proximaOcorrenciaMillis = lembreteInfo.timestamp,
                        ativo = true,
                        isVacina = true, // Flag para identificar que é uma vacina
                        isConfirmacao = isConfirmacao // Flag para identificar se é um lembrete de confirmação
                    )

                    try {
                        // Salvar lembrete no banco de dados
                        val lembreteId = lembreteDao.insertLembrete(lembrete)
                        val lembreteComId = lembrete.copy(id = lembreteId)
                        Log.d(tag, "Lembrete de vacina salvo no DB: ID=$lembreteId, Tipo=${lembreteComId.dose}")

                        // Agendar com base no tipo explícito
                        when (tipoLembrete) {
                            DateTimeUtils.TIPO_CONFIRMACAO -> {
                                agendarNotificacaoPosVacina(context, lembreteComId, vaccine.reminderTime)
                                Log.d(tag, "Notificação pós-vacina agendada via WorkManager para Lembrete ID: $lembreteId, Tipo: $tipoLembrete")
                            }
                            DateTimeUtils.TIPO_24H_ANTES, DateTimeUtils.TIPO_2H_ANTES -> {
                                agendarAlarmeVacina(context, lembreteComId, vaccine.reminderTime)
                                Log.d(tag, "Alarme de vacina agendado para Lembrete ID: $lembreteId, Tipo: $tipoLembrete")
                            }
                            else -> {
                                Log.w(tag, "Tipo de lembrete desconhecido ($tipoLembrete) para Lembrete ID: $lembreteId. Nenhum alarme/notificação agendado.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Erro ao salvar/agendar lembrete de vacina (ID: ${lembrete.id}, Tipo: $tipoLembrete)", e)
                        lembretesProcessadosComSucesso = false
                    }
                }

                if (!lembretesProcessadosComSucesso) {
                    Toast.makeText(context, "Vacina salva, mas houve problemas ao agendar alguns lembretes.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao processar lembretes para vacina", e)
                Toast.makeText(context, "Erro ao agendar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Função para agendar notificação pós-vacina (3h depois) usando WorkManager
    private fun agendarNotificacaoPosVacina(context: Context, lembrete: Lembrete, horaVacina: String) {
        try {
            val agora = System.currentTimeMillis()
            val atraso = lembrete.proximaOcorrenciaMillis - agora
            if (atraso <= 0) {
                Log.w(tag, "Atraso para notificação pós-vacina é negativo ou zero ($atraso ms), não agendando. Lembrete ID: ${lembrete.id}")
                return
            }
            Log.d(tag, "Agendando WorkManager para Lembrete ID: ${lembrete.id}, Tipo: ${lembrete.dose}, Atraso: $atraso ms")

            val workData = Data.Builder()
                .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
                .putString(NotificationWorker.EXTRA_VACINA_ID, lembrete.medicamentoId) // Passar ID da vacina
                .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
                .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
                .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
                .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
                .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
                .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
                .putBoolean(NotificationWorker.EXTRA_IS_VACINA, true)
                .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, true)
                .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
                .putString(NotificationWorker.EXTRA_NOME_VACINA, lembrete.nomeMedicamento.removePrefix("Vacina: ").trim()) // Extrair nome da vacina
                .putString(NotificationWorker.EXTRA_HORA_VACINA, horaVacina)
                .build()

            val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workData)
                .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
                .addTag("vacina_${lembrete.medicamentoId}_${lembrete.id}") // Tag específica para vacina
                .build()

            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            Log.d(tag, "WorkManager agendado para ${lembrete.id} em ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(lembrete.proximaOcorrenciaMillis))}")
        } catch (e: Exception) {
            Log.e(tag, "Erro ao agendar notificação pós-vacina via WorkManager", e)
        }
    }

    // Função para agendar alarme de vacina com horário real (para lembretes de 24h e 2h antes)
    private fun agendarAlarmeVacina(context: Context, lembrete: Lembrete, horaVacina: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_VACINA_ID, lembrete.medicamentoId) // Passar ID da vacina
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
            putExtra(NotificationWorker.EXTRA_NOME_VACINA, lembrete.nomeMedicamento.removePrefix("Vacina: ").trim())
            putExtra(NotificationWorker.EXTRA_HORA_VACINA, horaVacina)
            putExtra("IS_VACCINE_ALARM", true) // Flag para AlarmReceiver
        }
        Log.d(tag, "Agendando AlarmManager para Lembrete ID: ${lembrete.id}, Tipo: ${lembrete.dose}")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = lembrete.proximaOcorrenciaMillis
        val now = System.currentTimeMillis()
        if (triggerAtMillis < now) {
            Log.w(tag, "Lembrete ${lembrete.id} com proximaOcorrenciaMillis ($triggerAtMillis) no passado (agora: $now). Alarme não será agendado.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e(tag, "Não pode agendar alarmes exatos. O usuário precisa conceder permissão.")
                Toast.makeText(context, "Permissão para alarmes exatos não concedida.", Toast.LENGTH_LONG).show()
                return
            }
            try {
                val showIntent = Intent(context, AlarmReceiver::class.java) // Pode ser MainActivity ou outra tela
                val showPendingIntent = PendingIntent.getBroadcast(context, lembrete.id.toInt() + 200000, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(tag, "Alarme de vacina agendado com setAlarmClock para Lembrete ID: ${lembrete.id} em ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))}")
            } catch (e: Exception) {
                Log.e(tag, "Erro ao agendar com setAlarmClock, tentando fallback", e)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(tag, "Alarme de vacina agendado com setExactAndAllowWhileIdle para Lembrete ID: ${lembrete.id} em ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))}")
            }
        } catch (se: SecurityException) {
            Log.e(tag, "SecurityException ao agendar alarme de vacina. Verifique permissões.", se)
            Toast.makeText(context, "Erro de permissão ao agendar alarme.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(tag, "Erro geral ao agendar alarme de vacina", e)
            Toast.makeText(context, "Erro ao agendar alarme: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Função para atualizar lembretes de vacina
    private fun atualizarLembretesVacina(context: Context, lembreteDao: LembreteDao, vaccineId: String, vaccine: Vaccine) {
        viewModelScope.launch {
            try {
                val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(vaccineId).filter { it.isVacina } // Filtrar apenas lembretes de vacina
                if (lembretesAntigos.isNotEmpty()) {
                    Log.d(tag, "Atualizando vacina $vaccineId. Cancelando ${lembretesAntigos.size} lembretes antigos.")
                    for (lembreteAntigo in lembretesAntigos) {
                        // Cancelar alarme (AlarmManager)
                        AlarmScheduler.cancelAlarm(context, lembreteAntigo.id)
                        // Cancelar trabalho (WorkManager) - usar tag
                        WorkManager.getInstance(context).cancelAllWorkByTag("vacina_${vaccineId}_${lembreteAntigo.id}")
                        Log.d(tag, "Alarme/Trabalho cancelado para lembrete ID: ${lembreteAntigo.id}")
                    }
                    lembreteDao.deleteLembretesByMedicamentoIdAndType(vaccineId, isVacina = true)
                    Log.d(tag, "Excluídos ${lembretesAntigos.size} lembretes antigos de vacina do DB para vacina $vaccineId")
                }
                Log.d(tag, "Agendando novos lembretes para vacina $vaccineId")
                agendarLembretesVacina(context, lembreteDao, vaccineId, vaccine)
            } catch (e: Exception) {
                Log.e(tag, "Erro ao atualizar lembretes para vacina", e)
                Toast.makeText(context, "Erro ao atualizar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Input Validation (Internal) ---
    private fun validateInputs(): Boolean {
        var errorMessage: String? = null
        if (name.value.isBlank()) {
            errorMessage = "Nome da vacina/lembrete é obrigatório."
        } else if (reminderDate.value.isBlank()) {
            errorMessage = "Data do lembrete é obrigatória."
        } else {
            try {
                internalDateFormat.parse(reminderDate.value)
            } catch (e: ParseException) {
                Log.w(tag, "Validation failed: Invalid date format or value: ${reminderDate.value}", e)
                errorMessage = "Data inválida. Use o formato DD/MM/AAAA."
            }
        }

        if (reminderTime.value.isBlank()) {
            errorMessage = "Hora do lembrete é obrigatória."
        } else if (!reminderTime.value.matches(Regex("^\\d{2}:\\d{2}$"))) {
            Log.w(tag, "Validation failed: Invalid time format: ${reminderTime.value}")
            errorMessage = "Formato de hora inválido (HH:MM)."
        }

        _error.value = errorMessage
        if (errorMessage != null) {
            Log.w(tag, "Validation failed: $errorMessage")
        }
        return errorMessage == null
    }

    // --- State Reset Handlers ---
    fun onErrorShown() {
        _error.value = null
    }

    fun onNavigationHandled() {
        _navigateBack.value = false
    }

    private fun resetFormFields() {
        name.value = ""
        reminderDate.value = ""
        reminderTime.value = ""
        location.value = ""
        notes.value = ""
        patientName.value = ""
        currentVaccineId = null
        originalUserId = null // Reset originalUserId
        isEditing = false
        Log.d(tag, "Form fields reset.")
    }
}
