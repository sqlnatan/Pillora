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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.receiver.AlarmReceiver
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.utils.AlarmScheduler
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface ConsultationListUiState {
    data object Loading : ConsultationListUiState
    data class Success(val consultations: List<Consultation>) : ConsultationListUiState
    data class Error(val message: String) : ConsultationListUiState
}

class ConsultationViewModel : ViewModel() {

    private val tag = "ConsultationViewModel"
    private val auth = FirebaseAuth.getInstance()

    // Campos do formulário
    val specialty = mutableStateOf("")
    val doctorName = mutableStateOf("")
    val date = mutableStateOf("")
    val time = mutableStateOf("")
    val location = mutableStateOf("")
    val observations = mutableStateOf("")
    val patientName = mutableStateOf("")

    // NOVOS: toque personalizado e modo silencioso
    val isSilencioso = mutableStateOf(false)
    val toqueAlarmeUri = mutableStateOf<String?>(null)

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack

    private var currentConsultationId: String? = null
    private var currentConsultationUserId: String? = null

    val consultationListUiState: StateFlow<ConsultationListUiState> = ConsultationRepository.getAllConsultationsFlow()
        .map<List<Consultation>, ConsultationListUiState> { consultations ->
            ConsultationListUiState.Success(consultations.sortedBy { it.dateTime })
        }
        .catch { e ->
            emit(ConsultationListUiState.Error(e.localizedMessage ?: "Erro desconhecido ao carregar consultas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConsultationListUiState.Loading
        )

    // --- Form Input Update Functions ---
    fun onPatientNameChange(newName: String) { patientName.value = newName }
    fun onSpecialtyChange(newSpecialty: String) { specialty.value = newSpecialty }
    fun onDoctorNameChange(newName: String) { doctorName.value = newName }
    fun onLocationChange(newLocation: String) { location.value = newLocation }
    fun onObservationsChange(newObservations: String) { observations.value = newObservations }
    fun setSilencioso(valor: Boolean) { isSilencioso.value = valor }

    fun setToqueAlarmeUri(uri: String?) { toqueAlarmeUri.value = uri }


    // --- Date and Time Picker ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        if (date.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                calendar.time = sdf.parse(date.value) ?: Calendar.getInstance().time
            } catch (e: Exception) { Log.w(tag, "Error parsing existing date: ${date.value}", e) }
        }
        DatePickerDialog(
            context,
            { _: DatePicker, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                date.value = sdf.format(selectedDate.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
        if (time.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                calendar.time = sdf.parse(time.value) ?: Calendar.getInstance().time
            } catch (e: Exception) { Log.w(tag, "Error parsing existing time: ${time.value}", e) }
        }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                time.value = sdf.format(selectedTime.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    // --- Load Consultation ---
    fun loadConsultation(consultationId: String) {
        if (consultationId.isEmpty()) return
        currentConsultationId = consultationId
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            ConsultationRepository.getConsultationById(
                consultationId,
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
                        isSilencioso.value = consultation.isSilencioso
                        toqueAlarmeUri.value = consultation.toqueAlarmeUri
                    } else {
                        _error.value = "Consulta não encontrada ou acesso negado."
                    }
                    _isLoading.value = false
                },
                onFailure = {
                    _error.value = "Erro ao carregar consulta: ${it.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    // --- Save / Update Consultation ---
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
            observations = observations.value.trim(),
            isSilencioso = isSilencioso.value,
            toqueAlarmeUri = toqueAlarmeUri.value
        )

        if (consultation.specialty.isEmpty() || date.value.isEmpty() || time.value.isEmpty()) {
            _error.value = "Especialidade, Data e Hora são obrigatórios."
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                if (currentConsultationId == null) {
                    ConsultationRepository.addConsultation(consultation,
                        onSuccess = { newConsultationId ->
                            agendarLembretesConsulta(context, lembreteDao, newConsultationId, consultation)
                            _isLoading.value = false
                            _navigateBack.value = true
                        },
                        onFailure = {
                            _error.value = "Erro ao salvar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                } else {
                    if (consultation.userId != currentUserIdAuth) {
                        _error.value = "Erro de autorização ao preparar atualização."
                        _isLoading.value = false
                        return@launch
                    }
                    ConsultationRepository.updateConsultation(consultation,
                        onSuccess = {
                            atualizarLembretesConsulta(context, lembreteDao, currentConsultationId!!, consultation)
                            _isLoading.value = false
                            _navigateBack.value = true
                        },
                        onFailure = {
                            _error.value = "Erro ao atualizar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                }
            } catch (e: Exception) {
                _error.value = "Erro inesperado: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // --- Agendamento de Lembretes ---
    private fun agendarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        viewModelScope.launch {
            val dateTimeParts = consultation.dateTime.split(" ")
            val consultaDate = dateTimeParts.getOrElse(0) { "" }
            val consultaTime = dateTimeParts.getOrElse(1) { "" }
            if (consultaDate.isEmpty() || consultaTime.isEmpty()) return@launch

            val lembretesInfo = DateTimeUtils.calcularLembretesConsulta(consultaDate, consultaTime)
            lembretesInfo.forEach { lembreteInfo ->
                val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                val lembrete = Lembrete(
                    id = 0,
                    medicamentoId = consultationId,
                    nomeMedicamento = "Consulta: ${consultation.specialty}",
                    recipientName = consultation.patientName,
                    hora = calendar.get(Calendar.HOUR_OF_DAY),
                    minuto = calendar.get(Calendar.MINUTE),
                    dose = lembreteInfo.tipo,
                    observacao = "Dr(a). ${consultation.doctorName} em ${consultation.location}. ${if (consultation.observations.isNotEmpty()) "Obs: ${consultation.observations}" else ""}",
                    proximaOcorrenciaMillis = lembreteInfo.timestamp,
                    ativo = true,
                    isSilencioso = consultation.isSilencioso,
                    toqueAlarmeUri = consultation.toqueAlarmeUri
                )
                try {
                    val lembreteId = lembreteDao.insertLembrete(lembrete)
                    val lembreteComId = lembrete.copy(id = lembreteId)

                    when (lembreteInfo.tipo) {
                        DateTimeUtils.TIPO_3H_DEPOIS -> agendarNotificacaoPosConsulta(context, lembreteComId)
                        DateTimeUtils.TIPO_24H_ANTES, DateTimeUtils.TIPO_2H_ANTES -> agendarAlarmeConsulta(context, lembreteComId)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Erro ao salvar/agendar lembrete", e)
                }
            }
        }
    }

    private fun agendarNotificacaoPosConsulta(context: Context, lembrete: Lembrete) {
        val workData = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, true)
            .putBoolean(NotificationWorker.EXTRA_IS_SILENCIOSO, lembrete.isSilencioso)
            .putString(NotificationWorker.EXTRA_TOQUE_ALARME_URI, lembrete.toqueAlarmeUri)
            .build()

        val delay = lembrete.proximaOcorrenciaMillis - System.currentTimeMillis()
        if (delay <= 0) return

        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("consulta_${lembrete.medicamentoId}_${lembrete.id}")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun agendarAlarmeConsulta(context: Context, lembrete: Lembrete) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, true)
            putExtra(NotificationWorker.EXTRA_IS_SILENCIOSO, lembrete.isSilencioso)
            putExtra(NotificationWorker.EXTRA_TOQUE_ALARME_URI, lembrete.toqueAlarmeUri)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = lembrete.proximaOcorrenciaMillis
        if (triggerAtMillis < System.currentTimeMillis()) return

        try {
            val showIntent = Intent(context, AlarmReceiver::class.java)
            val showPendingIntent = PendingIntent.getBroadcast(context, lembrete.id.toInt() + 100000, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun atualizarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        viewModelScope.launch {
            val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(consultationId)
            lembretesAntigos.forEach { lembreteAntigo ->
                AlarmScheduler.cancelAlarm(context, lembreteAntigo.id)
                WorkManager.getInstance(context).cancelAllWorkByTag("consulta_${consultationId}_${lembreteAntigo.id}")
            }
            lembreteDao.deleteLembretesByMedicamentoId(consultationId)
            agendarLembretesConsulta(context, lembreteDao, consultationId, consultation)
        }
    }

    fun onNavigationHandled() { _navigateBack.value = false }
    fun onErrorShown() { _error.value = null }
}
