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
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.receiver.AlarmReceiver
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.utils.AlarmScheduler
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted // Import necessário
import kotlinx.coroutines.flow.catch // Import necessário
import kotlinx.coroutines.flow.map // Import necessário
import kotlinx.coroutines.flow.stateIn // Import necessário
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Define os estados possíveis da UI para a lista de consultas (Adicionado para a lista)
sealed interface ConsultationListUiState {
    data object Loading : ConsultationListUiState
    data class Success(val consultations: List<Consultation>) : ConsultationListUiState
    data class Error(val message: String) : ConsultationListUiState
}

class ConsultationViewModel : ViewModel() {

    private val tag = "ConsultationViewModel"
    private val auth = FirebaseAuth.getInstance()

    // Form fields state
    val specialty = mutableStateOf("")
    val doctorName = mutableStateOf("")
    val date = mutableStateOf("")
    val time = mutableStateOf("")
    val location = mutableStateOf("")
    val observations = mutableStateOf("")
    val patientName = mutableStateOf("")

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack

    private var currentConsultationId: String? = null
    private var currentConsultationUserId: String? = null

    // --- StateFlow para a lista de consultas (Adicionado) ---
    val consultationListUiState: StateFlow<ConsultationListUiState> = ConsultationRepository.getAllConsultationsFlow()
        .map<List<Consultation>, ConsultationListUiState> { consultations ->
            Log.d(tag, "Flow (in ViewModel) emitted ${consultations.size} consultations")
            ConsultationListUiState.Success(consultations.sortedBy { it.dateTime }) // Ordena aqui
        }
        .catch { e ->
            Log.e(tag, "Error collecting consultations flow in ViewModel", e)
            emit(ConsultationListUiState.Error(e.localizedMessage ?: "Erro desconhecido ao carregar consultas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Mantém o flow ativo por 5s após o último coletor parar
            initialValue = ConsultationListUiState.Loading // Estado inicial
        )

    // --- Form Input Update Functions ---
    fun onPatientNameChange(newName: String) {
        patientName.value = newName
    }
    fun onSpecialtyChange(newSpecialty: String) {
        specialty.value = newSpecialty
    }

    fun onDoctorNameChange(newName: String) {
        doctorName.value = newName
    }

    fun onLocationChange(newLocation: String) {
        location.value = newLocation
    }

    fun onObservationsChange(newObservations: String) {
        observations.value = newObservations
    }

    // --- Date and Time Picker Logic ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        if (date.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                calendar.time = sdf.parse(date.value) ?: Calendar.getInstance().time
            } catch (e: Exception) {
                Log.w(tag, "Error parsing existing date: ${date.value}", e)
            }
        }
        val datePickerDialog = DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                date.value = sdf.format(selectedDate.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
        if (time.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                calendar.time = sdf.parse(time.value) ?: Calendar.getInstance().time
            } catch (e: Exception) {
                Log.w(tag, "Error parsing existing time: ${time.value}", e)
            }
        }
        TimePickerDialog(
            context,
            { _, hourOfDay: Int, minute: Int ->
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                time.value = sdf.format(selectedTime.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24 hour view
        ).show()
    }

    // --- Load and Save Logic ---
    fun loadConsultation(consultationId: String) {
        if (consultationId.isEmpty()) return
        currentConsultationId = consultationId
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            ConsultationRepository.getConsultationById(
                consultationId = consultationId,
                onSuccess = { consultation ->
                    if (consultation != null) {
                        specialty.value = consultation.specialty
                        doctorName.value = consultation.doctorName
                        patientName.value = consultation.patientName
                        val dateTimeParts = consultation.dateTime.split(" ")
                        date.value = dateTimeParts.getOrElse(0) { "" }
                        time.value = dateTimeParts.getOrElse(1) { "" }
                        location.value = consultation.location
                        observations.value = consultation.observations
                        currentConsultationUserId = consultation.userId
                        Log.d(tag, "Consultation data loaded for ID: $consultationId")
                    } else {
                        _error.value = "Consulta não encontrada ou acesso negado."
                        Log.w(tag, "Consultation not found or access denied for ID: $consultationId")
                    }
                    _isLoading.value = false
                },
                onFailure = {
                    Log.e(tag, "Error loading consultation $consultationId", it)
                    _error.value = "Erro ao carregar consulta: ${it.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun saveConsultation(context: Context, lembreteDao: LembreteDao) {
        val currentUserIdAuth = auth.currentUser?.uid
        if (currentUserIdAuth == null) {
            _error.value = "Usuário não autenticado."
            return
        }
        _isLoading.value = true
        _error.value = null
        val consultation = Consultation(
            id = currentConsultationId ?: "",
            userId = if (currentConsultationId != null) currentConsultationUserId ?: currentUserIdAuth else currentUserIdAuth,
            specialty = specialty.value.trim(),
            doctorName = doctorName.value.trim(),
            patientName = patientName.value.trim(),
            dateTime = "${date.value} ${time.value}".trim(),
            location = location.value.trim(),
            observations = observations.value.trim()
        )
        if (consultation.specialty.isEmpty() || date.value.isEmpty() || time.value.isEmpty()) {
            _error.value = "Especialidade, Data e Hora são obrigatórios."
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            try {
                if (currentConsultationId == null) {
                    ConsultationRepository.addConsultation(
                        consultation = consultation,
                        onSuccess = { newConsultationId: String ->
                            Log.d(tag, "Consultation added successfully with ID: $newConsultationId")
                            agendarLembretesConsulta(context, lembreteDao, newConsultationId, consultation)
                            _isLoading.value = false
                            _navigateBack.value = true
                        },
                        onFailure = {
                            Log.e(tag, "Error adding consultation", it)
                            _error.value = "Erro ao salvar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                } else {
                    if (consultation.userId != currentUserIdAuth) {
                        Log.e(tag, "Mismatch between consultation userId (${consultation.userId}) and auth userId ($currentUserIdAuth)")
                        _error.value = "Erro de autorização ao preparar atualização."
                        _isLoading.value = false
                        return@launch
                    }
                    ConsultationRepository.updateConsultation(
                        consultation = consultation,
                        onSuccess = {
                            Log.d(tag, "Consultation updated successfully")
                            atualizarLembretesConsulta(context, lembreteDao, currentConsultationId!!, consultation)
                            _isLoading.value = false
                            _navigateBack.value = true
                        },
                        onFailure = {
                            Log.e(tag, "Error updating consultation", it)
                            _error.value = "Erro ao atualizar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception during save/update", e)
                _error.value = "Erro inesperado: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Função para agendar lembretes de consulta (refatorada)
    private fun agendarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        viewModelScope.launch {
            try {
                val dateTimeParts = consultation.dateTime.split(" ")
                val consultaDate = dateTimeParts.getOrElse(0) { "" }
                val consultaTime = dateTimeParts.getOrElse(1) { "" }
                if (consultaDate.isEmpty() || consultaTime.isEmpty()) {
                    Log.e(tag, "Data ou hora da consulta inválida: ${consultation.dateTime}")
                    return@launch
                }

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val consultaTimeObj = timeFormat.parse(consultaTime)
                val consultaCalendar = Calendar.getInstance().apply { time = consultaTimeObj ?: Date() }
                val horaConsulta = consultaCalendar.get(Calendar.HOUR_OF_DAY)
                val minutoConsulta = consultaCalendar.get(Calendar.MINUTE)

                // Calcular lembretes com tipo explícito
                val lembretesInfo = DateTimeUtils.calcularLembretesConsulta(
                    consultaDateString = consultaDate,
                    consultaTimeString = consultaTime
                )

                if (lembretesInfo.isEmpty()) {
                    Log.e(tag, "Nenhum lembrete calculado para consulta $consultationId")
                    return@launch
                }
                Log.d(tag, "Calculados ${lembretesInfo.size} lembretes para consulta $consultationId")

                var lembretesProcessadosComSucesso = true
                lembretesInfo.forEach { lembreteInfo ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                    val tipoLembrete = lembreteInfo.tipo // Usar o tipo explícito retornado

                    val lembrete = Lembrete(
                        id = 0,
                        medicamentoId = consultationId,
                        nomeMedicamento = "Consulta: ${consultation.specialty}",
                        recipientName = consultation.patientName,
                        hora = calendar.get(Calendar.HOUR_OF_DAY),
                        minuto = calendar.get(Calendar.MINUTE),
                        dose = tipoLembrete, // Armazenar o tipo explícito no campo dose
                        observacao = "Dr(a). ${consultation.doctorName} em ${consultation.location}. ${if (consultation.observations.isNotEmpty()) "Obs: ${consultation.observations}" else ""}",
                        proximaOcorrenciaMillis = lembreteInfo.timestamp,
                        ativo = true
                    )

                    try {
                        val lembreteId = lembreteDao.insertLembrete(lembrete)
                        val lembreteComId = lembrete.copy(id = lembreteId)
                        Log.d(tag, "Lembrete salvo no DB: ID=$lembreteId, Tipo=${lembreteComId.dose}")

                        // Agendar com base no tipo explícito
                        when (tipoLembrete) {
                            DateTimeUtils.TIPO_3H_DEPOIS -> {
                                agendarNotificacaoPosConsulta(context, lembreteComId, horaConsulta, minutoConsulta, tipoLembrete)
                                Log.d(tag, "Notificação pós-consulta agendada via WorkManager para Lembrete ID: $lembreteId, Tipo: $tipoLembrete")
                            }
                            DateTimeUtils.TIPO_24H_ANTES, DateTimeUtils.TIPO_2H_ANTES -> {
                                agendarAlarmeConsulta(context, lembreteComId, horaConsulta, minutoConsulta, tipoLembrete)
                                Log.d(tag, "Alarme de consulta agendado para Lembrete ID: $lembreteId, Tipo: $tipoLembrete")
                            }
                            else -> {
                                Log.w(tag, "Tipo de lembrete desconhecido ($tipoLembrete) para Lembrete ID: $lembreteId. Nenhum alarme/notificação agendado.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Erro ao salvar/agendar lembrete de consulta (ID: ${lembrete.id}, Tipo: $tipoLembrete)", e)
                        lembretesProcessadosComSucesso = false
                    }
                }

                if (!lembretesProcessadosComSucesso) {
                    Toast.makeText(context, "Consulta salva, mas houve problemas ao agendar alguns lembretes.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao processar lembretes para consulta", e)
                Toast.makeText(context, "Erro ao agendar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Função para agendar notificação pós-consulta (3h depois) usando WorkManager (refatorada)
    private fun agendarNotificacaoPosConsulta(context: Context, lembrete: Lembrete, horaConsulta: Int, minutoConsulta: Int, tipoLembrete: String) {
        try {
            val agora = System.currentTimeMillis()
            val atraso = lembrete.proximaOcorrenciaMillis - agora
            if (atraso <= 0) {
                Log.w(tag, "Atraso para notificação pós-consulta é negativo ou zero ($atraso ms), não agendando. Lembrete ID: ${lembrete.id}")
                return
            }
            Log.d(tag, "Agendando WorkManager para Lembrete ID: ${lembrete.id}, Tipo: $tipoLembrete, Atraso: $atraso ms")

            val workData = Data.Builder()
                .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
                .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
                .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
                .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao) // Passar observação aqui
                .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
                .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
                .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
                .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
                .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, true)
                .putInt(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsulta)
                .putInt(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsulta)
                .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, tipoLembrete) // Passar o tipo explícito
                .build()

            val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workData)
                .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
                .addTag("consulta_${lembrete.medicamentoId}_${lembrete.id}")
                .build()

            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            Log.d(tag, "WorkManager agendado para ${lembrete.id} em ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(lembrete.proximaOcorrenciaMillis))}")
        } catch (e: Exception) {
            Log.e(tag, "Erro ao agendar notificação pós-consulta via WorkManager", e)
        }
    }

    // Função para agendar alarme de consulta com horário real (para lembretes de 24h e 2h antes) (refatorada)
    private fun agendarAlarmeConsulta(context: Context, lembrete: Lembrete, horaConsulta: Int, minutoConsulta: Int, tipoLembrete: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao) // Passar observação aqui
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsulta)
            putExtra(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsulta)
            putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, tipoLembrete) // Passar o tipo explícito
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, true) // Flag para AlarmReceiver
        }
        Log.d(tag, "Agendando AlarmManager para Lembrete ID: ${lembrete.id}, Tipo: $tipoLembrete")

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
                return
            }
            try {
                val showIntent = Intent(context, AlarmReceiver::class.java) // Pode ser MainActivity ou outra tela
                val showPendingIntent = PendingIntent.getBroadcast(context, lembrete.id.toInt() + 100000, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(tag, "Alarme de consulta agendado com setAlarmClock para Lembrete ID: ${lembrete.id} em ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))}")
            } catch (e: Exception) {
                Log.e(tag, "Erro ao agendar com setAlarmClock, tentando fallback", e)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.d(tag, "Alarme de consulta agendado com setExactAndAllowWhileIdle para Lembrete ID: ${lembrete.id} em ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))}")
            }
        } catch (se: SecurityException) {
            Log.e(tag, "SecurityException ao agendar alarme. Verifique permissões.", se)
        } catch (e: Exception) {
            Log.e(tag, "Erro geral ao agendar alarme", e)
        }
    }

    // Função para atualizar lembretes de consulta (refatorada)
    private fun atualizarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        viewModelScope.launch {
            try {
                val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(consultationId)
                if (lembretesAntigos.isNotEmpty()) {
                    Log.d(tag, "Atualizando consulta $consultationId. Cancelando ${lembretesAntigos.size} lembretes antigos.")
                    for (lembreteAntigo in lembretesAntigos) {
                        // Cancelar alarme (AlarmManager)
                        AlarmScheduler.cancelAlarm(context, lembreteAntigo.id)
                        // Cancelar trabalho (WorkManager) - usar tag
                        WorkManager.getInstance(context).cancelAllWorkByTag("consulta_${consultationId}_${lembreteAntigo.id}")
                        Log.d(tag, "Alarme/Trabalho cancelado para lembrete ID: ${lembreteAntigo.id}")
                    }
                    lembreteDao.deleteLembretesByMedicamentoId(consultationId)
                    Log.d(tag, "Excluídos ${lembretesAntigos.size} lembretes antigos do DB para consulta $consultationId")
                }
                Log.d(tag, "Agendando novos lembretes para consulta $consultationId")
                agendarLembretesConsulta(context, lembreteDao, consultationId, consultation)
            } catch (e: Exception) {
                Log.e(tag, "Erro ao atualizar lembretes para consulta", e)
                Toast.makeText(context, "Erro ao atualizar lembretes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun onNavigationHandled() {
        _navigateBack.value = false
    }

    fun onErrorShown() {
        _error.value = null
    }
}

