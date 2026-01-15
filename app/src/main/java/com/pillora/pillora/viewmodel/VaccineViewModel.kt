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

        viewModelScope.launch {
            if (isEditing) {
                Log.d(tag, "Attempting to update vaccine: ${vaccine.id} with UserID: ${vaccine.userId}")
                if (vaccine.id.isNotEmpty()) {
                    if (vaccine.userId != currentUserIdAuth) {
                        Log.e(tag, "Mismatch between vaccine userId (${vaccine.userId}) and auth userId ($currentUserIdAuth)")
                        _error.value = "Erro de autorização ao preparar atualização."
                        _isLoading.value = false
                        return@launch
                    }

                    VaccineRepository.updateVaccine(
                        vaccine = vaccine,
                        onSuccess = { vaccineId ->
                            Log.d(tag, "Vaccine updated successfully with ID: $vaccineId")
                            atualizarLembretesVacina(context, lembreteDao, vaccineId, vaccine)
                            _isLoading.value = false
                            resetFormFields()
                            _navigateBack.value = true
                        },
                        onFailure = { exception ->
                            Log.e(tag, "Error updating vaccine", exception)
                            _isLoading.value = false
                            _error.value = exception.message ?: "Erro desconhecido ao atualizar."
                        }
                    )
                } else {
                    Log.e(tag, "Update failed: Vaccine ID is missing.")
                    _error.value = "ID da vacina ausente para atualização."
                    _isLoading.value = false
                }
            } else {
                Log.d(tag, "Attempting to add new vaccine: ${vaccine.name} with UserID: ${vaccine.userId}")

                VaccineRepository.addVaccine(
                    vaccine = vaccine,
                    onSuccess = { newVaccineId ->
                        Log.d(tag, "Vaccine added successfully with ID: $newVaccineId")
                        agendarLembretesVacina(context, lembreteDao, newVaccineId, vaccine)
                        _isLoading.value = false
                        resetFormFields()
                        _navigateBack.value = true
                    },
                    onFailure = { exception ->
                        Log.e(tag, "Error adding vaccine", exception)
                        _isLoading.value = false
                        _error.value = exception.message ?: "Erro desconhecido ao salvar."
                    }
                )
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
                    Toast.makeText(context, "Vacina salva", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao processar lembretes para vacina", e)
                Toast.makeText(context, "Erro ao agendar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Função para atualizar lembretes de vacina
    private fun atualizarLembretesVacina(context: Context, lembreteDao: LembreteDao, vaccineId: String, vaccine: Vaccine) {
        viewModelScope.launch {
            try {
                // Primeiro, cancelar e excluir os lembretes existentes
                val lembretesExistentes = lembreteDao.getLembretesByMedicamentoId(vaccineId)
                Log.d(tag, "Encontrados ${lembretesExistentes.size} lembretes existentes para vacina $vaccineId")

                // Cancelar alarmes e notificações
                lembretesExistentes.forEach { lembrete ->
                    try {
                        // Cancelar alarme se for um lembrete de pré-vacina
                        if (lembrete.dose == DateTimeUtils.TIPO_24H_ANTES || lembrete.dose == DateTimeUtils.TIPO_2H_ANTES) {
                            AlarmScheduler.cancelAlarm(context, lembrete.id) // Correção: Passar Long diretamente
                            Log.d(tag, "Alarme cancelado para Lembrete ID: ${lembrete.id}")
                        }
                        // Cancelar notificação se for um lembrete de pós-vacina
                        else if (lembrete.dose == DateTimeUtils.TIPO_CONFIRMACAO) {
                            WorkManager.getInstance(context).cancelAllWorkByTag("notification_${lembrete.id}")
                            Log.d(tag, "Notificação cancelada para Lembrete ID: ${lembrete.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Erro ao cancelar alarme/notificação para Lembrete ID: ${lembrete.id}", e)
                    }
                }

                // Excluir lembretes do banco de dados
                lembreteDao.deleteLembretesByMedicamentoId(vaccineId)
                Log.d(tag, "Lembretes antigos excluídos para vacina $vaccineId")

                // Agendar novos lembretes
                agendarLembretesVacina(context, lembreteDao, vaccineId, vaccine)
                Log.d(tag, "Novos lembretes agendados para vacina $vaccineId")

            } catch (e: Exception) {
                Log.e(tag, "Erro ao atualizar lembretes para vacina $vaccineId", e)
                Toast.makeText(context, "Erro ao atualizar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
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
                .putBoolean(NotificationWorker.EXTRA_IS_VACINA, true)
                .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, true)
                .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
                .putString(NotificationWorker.EXTRA_VACCINE_TIME, horaVacina)
                .build()

            val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workData)
                .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
                .addTag("notification_${lembrete.id}")
                .build()

            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            Log.d(tag, "WorkManager agendado com sucesso para notificação pós-vacina. Lembrete ID: ${lembrete.id}")

        } catch (e: Exception) {
            Log.e(tag, "Erro ao agendar notificação pós-vacina para Lembrete ID: ${lembrete.id}", e)
        }
    }

    /**
     * Agenda alarme para vacina usando setAndAllowWhileIdle (alarme inexato).
     * Vacinas não precisam de alarmes exatos.
     */
    private fun agendarAlarmeVacina(context: Context, lembrete: Lembrete, horaVacina: String) {
        try {
            val agora = System.currentTimeMillis()
            val atraso = lembrete.proximaOcorrenciaMillis - agora
            if (atraso <= 0) {
                Log.w(tag, "Atraso para alarme de vacina é negativo ou zero ($atraso ms), não agendando. Lembrete ID: ${lembrete.id}")
                return
            }
            Log.d(tag, "Agendando alarme para Lembrete ID: ${lembrete.id}, Tipo: ${lembrete.dose}, Atraso: $atraso ms")

            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
                putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
                putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
                putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
                putExtra(NotificationWorker.EXTRA_IS_VACINA, true)
                putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
                putExtra(NotificationWorker.EXTRA_VACCINE_TIME, horaVacina)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                lembrete.id.toInt(), // Usar ID do lembrete como requestCode
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // VACINAS usam setAndAllowWhileIdle (alarme inexato)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    lembrete.proximaOcorrenciaMillis,
                    pendingIntent
                )
                Log.d(tag, "Alarme INEXATO agendado para VACINA (Lembrete ID: ${lembrete.id})")
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    lembrete.proximaOcorrenciaMillis,
                    pendingIntent
                )
                Log.d(tag, "Alarme agendado para VACINA (API < 23, Lembrete ID: ${lembrete.id})")
            }

        } catch (e: Exception) {
            Log.e(tag, "Erro ao agendar alarme para Lembrete ID: ${lembrete.id}", e)
        }
    }

    // --- Utility Functions ---
    private fun resetFormFields() {
        name.value = ""
        reminderDate.value = ""
        reminderTime.value = ""
        location.value = ""
        notes.value = ""
        patientName.value = ""
        currentVaccineId = null
        originalUserId = null
        isEditing = false
    }

    private fun validateInputs(): Boolean {
        if (name.value.trim().isEmpty()) {
            _error.value = "Nome da vacina é obrigatório."
            return false
        }
        if (reminderDate.value.isEmpty()) {
            _error.value = "Data do lembrete é obrigatória."
            return false
        }
        if (reminderTime.value.isEmpty()) {
            _error.value = "Hora do lembrete é obrigatória."
            return false
        }

        // Validar formato da data
        try {
            if (reminderDate.value.isNotEmpty()) {
                internalDateFormat.parse(reminderDate.value)
            }
        } catch (e: ParseException) {
            _error.value = "Formato de data inválido. Use DD/MM/AAAA."
            return false
        }

        // Validar formato da hora
        try {
            if (reminderTime.value.isNotEmpty()) {
                val timeParts = reminderTime.value.split(":")
                if (timeParts.size != 2 || timeParts[0].toInt() !in 0..23 || timeParts[1].toInt() !in 0..59) {
                    throw ParseException("Invalid time format", 0)
                }
            }
        } catch (e: Exception) {
            _error.value = "Formato de hora inválido. Use HH:MM (24h)."
            return false
        }

        return true
    }

    fun resetNavigationState() {
        _navigateBack.value = false
    }

    fun resetErrorState() {
        _error.value = null
    }
}
