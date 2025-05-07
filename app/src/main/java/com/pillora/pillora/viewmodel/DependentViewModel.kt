package com.pillora.pillora.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.Dependent
import com.pillora.pillora.repository.DependentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow // Ensure asStateFlow is imported
import kotlinx.coroutines.launch

class DependentViewModel(private val repository: DependentRepository = DependentRepository()) : ViewModel() {

    private val _dependents = MutableStateFlow<List<Dependent>>(emptyList())
    val dependents: StateFlow<List<Dependent>> = _dependents.asStateFlow() // Used asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadDependents() // Initial load
    }

    // Made private as it's only called internally
    private fun loadDependents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null // Clear previous errors on new load attempt
            repository.getDependents()
                .onSuccess { fetchedDependents -> // Renamed 'it' for clarity
                    _dependents.value = fetchedDependents
                    // isLoading will be set to false after success or failure block
                }
                .onFailure { exception -> // Renamed 'it' for clarity
                    _error.value = exception.message ?: "Erro desconhecido ao carregar dependentes"
                }
            _isLoading.value = false // Ensure isLoading is set to false after operation completes
        }
    }

    fun addDependent(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // Assuming userId will be set in the repository or is not needed for this simple Dependent model for now
            val dependent = Dependent(name = name, userId = "") // Ensure all required fields are present if any
            repository.addDependent(dependent)
                .onSuccess {
                    loadDependents() // Recarrega a lista após adicionar
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Erro ao adicionar dependente"
                    _isLoading.value = false // Also set isLoading to false on failure here
                }
            // isLoading is set to false by loadDependents() on its completion
        }
    }

    fun updateDependent(dependent: Dependent) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.updateDependent(dependent)
                .onSuccess {
                    loadDependents() // Recarrega a lista após atualizar
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Erro ao atualizar dependente"
                    _isLoading.value = false // Also set isLoading to false on failure here
                }
            // isLoading is set to false by loadDependents() on its completion
        }
    }

    fun deleteDependent(dependentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.deleteDependent(dependentId)
                .onSuccess {
                    loadDependents() // Recarrega a lista após deletar
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Erro ao excluir dependente"
                    _isLoading.value = false // Also set isLoading to false on failure here
                }
            // isLoading is set to false by loadDependents() on its completion
        }
    }
}
