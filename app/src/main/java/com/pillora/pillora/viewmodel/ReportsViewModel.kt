package com.pillora.pillora.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportFile(
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long
)

class ReportsViewModel(
    application: Application,
    userPreferences: UserPreferences
) : AndroidViewModel(application) {

    // ✅ Contexto seguro (sem memory leak)
    private val context get() = getApplication<Application>().applicationContext

    private val _reportFiles = MutableStateFlow<List<ReportFile>>(emptyList())
    val reportFiles: StateFlow<List<ReportFile>> = _reportFiles

    // ✅ Converte Flow<Boolean> para StateFlow<Boolean>
    val isPremium: StateFlow<Boolean> = userPreferences.isPremium.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val reportsDir: File by lazy {
        // Usar o diretório de arquivos interno para armazenar os relatórios
        context.getDir("reports", Application.MODE_PRIVATE)
    }

    init {
        // Garantir que o diretório exista
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        loadReportFiles()
    }

    fun loadReportFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = reportsDir.listFiles { _, name -> name.endsWith(".pdf") }
                ?.map { file ->
                    ReportFile(
                        name = file.name,
                        path = file.absolutePath,
                        lastModified = file.lastModified(),
                        size = file.length()
                    )
                }
                ?.sortedByDescending { it.lastModified }
                ?: emptyList()

            _reportFiles.value = files
            Log.d("ReportsViewModel", "Loaded ${files.size} report files.")
        }
    }

    fun deleteReport(reportFile: ReportFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(reportFile.path)
            if (file.exists() && file.delete()) {
                Log.d("ReportsViewModel", "Deleted file: ${reportFile.name}")
                loadReportFiles() // Recarregar após exclusão
            } else {
                Log.e("ReportsViewModel", "Failed to delete file: ${reportFile.name}")
            }
        }
    }

    fun generateReport(reportName: String) {
        if (!isPremium.value) {
            Log.w("ReportsViewModel", "Attempted to generate report without Premium status.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${reportName}_$timestamp.pdf"
            val newFile = File(reportsDir, fileName)

            try {
                // *** SIMULAÇÃO DE GERAÇÃO DE PDF ***
                newFile.createNewFile()
                newFile.writeText("Relatório gerado em $timestamp. Conteúdo real do PDF viria aqui.")
                // *** FIM DA SIMULAÇÃO ***

                Log.d("ReportsViewModel", "Simulated PDF file created: $fileName")
                loadReportFiles()
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Error generating PDF", e)
            }
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            userPreferences: UserPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportsViewModel(application, userPreferences) as T
            }
        }
    }
}
