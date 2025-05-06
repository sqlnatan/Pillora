package com.pillora.pillora.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.PrescribedMedication
import com.pillora.pillora.model.Recipe
import com.pillora.pillora.repository.RecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest // Import flatMapLatest
import kotlinx.coroutines.flow.flowOf // Import flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Represents the different states for the Recipe List screen
sealed class RecipeListUiState {
    data object Loading : RecipeListUiState()
    data class Success(val recipes: List<Recipe>) : RecipeListUiState()
    data class Error(val message: String) : RecipeListUiState()
}

// Represents the different states for the Recipe Form/Detail screen
sealed class RecipeDetailUiState {
    data object Loading : RecipeDetailUiState()
    data class Success(val recipe: Recipe) : RecipeDetailUiState() // State for when data is loaded successfully
    data class Error(val message: String) : RecipeDetailUiState()
    data object OperationSuccess : RecipeDetailUiState() // State for when save/update/delete is successful
    data object Idle : RecipeDetailUiState() // Initial state or after an operation
}

// *** MODIFIED: Added AppViewModel dependency ***
class RecipeViewModel(private val appViewModel: AppViewModel) : ViewModel() {

    private val _recipeListState = MutableStateFlow<RecipeListUiState>(RecipeListUiState.Loading)
    val recipeListState: StateFlow<RecipeListUiState> = _recipeListState.asStateFlow()

    // Default to Idle state
    private val _recipeDetailState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Idle)
    val recipeDetailState: StateFlow<RecipeDetailUiState> = _recipeDetailState.asStateFlow()

    // Form state for adding/editing recipes
    var recipeUiState by mutableStateOf(RecipeFormData())
        private set

    // State for managing prescribed medications within the form
    var prescribedMedicationsState by mutableStateOf<List<PrescribedMedication>>(emptyList())
        private set

    // State for the currently edited/added prescribed medication
    var currentPrescribedMedicationState by mutableStateOf(PrescribedMedication())
        private set

    // State to track the index of the medication being edited (-1 means adding new)
    var editingMedicationIndex by mutableIntStateOf(-1)
        private set

    init {
        // *** MODIFIED: Load recipes based on active profile ***
        loadRecipesBasedOnActiveProfile()
    }

    // *** MODIFIED: Renamed and updated to use activeProfileId ***
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadRecipesBasedOnActiveProfile() {
        viewModelScope.launch {
            appViewModel.activeProfileId.flatMapLatest { profileId ->
                if (profileId.isNullOrBlank()) {
                    // No profile selected, return empty list flow
                    _recipeListState.value = RecipeListUiState.Success(emptyList()) // Update state directly
                    flowOf(emptyList()) // Return empty flow to satisfy flatMapLatest
                } else {
                    // Profile selected, fetch recipes for this profile
                    _recipeListState.value = RecipeListUiState.Loading // Set loading before fetching
                    RecipeRepository.getAllRecipesFlow(profileId)
                }
            }.catch { e ->
                _recipeListState.value = RecipeListUiState.Error("Erro ao carregar receitas: ${e.message}")
            }.collect { recipes ->
                // Update state only if not already handled (e.g., empty list case)
                if (_recipeListState.value !is RecipeListUiState.Error) {
                    _recipeListState.value = RecipeListUiState.Success(recipes)
                }
            }
        }
    }

    // Load details - No change needed regarding profileId, as ID is unique
    fun loadRecipeDetails(recipeId: String?) { // Changed parameter to nullable
        if (recipeId.isNullOrBlank()) {
            resetFormState()
            _recipeDetailState.value = RecipeDetailUiState.Idle // Ensure Idle state for new recipe
            return
        }

        _recipeDetailState.value = RecipeDetailUiState.Loading // Set Loading state before fetching
        RecipeRepository.getRecipeById(
            recipeId = recipeId,
            onSuccess = { recipe ->
                if (recipe != null) {
                    // Check if the loaded recipe belongs to the currently active profile (optional, but good practice)
                    val currentProfileId = appViewModel.activeProfileId.value
                    if (currentProfileId == null || recipe.profileId == currentProfileId) {
                        updateFormState(recipe)
                        _recipeDetailState.value = RecipeDetailUiState.Success(recipe)
                    } else {
                        _recipeDetailState.value = RecipeDetailUiState.Error("Esta receita pertence a outro perfil.")
                        // Optionally, clear the form or navigate back
                        resetFormState()
                    }
                } else {
                    _recipeDetailState.value = RecipeDetailUiState.Error("Receita não encontrada.")
                }
            },
            onError = { exception ->
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao carregar detalhes da receita: ${exception.message}")
            }
        )
    }

    // Function to reset the detail state to Idle
    fun resetDetailState() {
        _recipeDetailState.value = RecipeDetailUiState.Idle
    }

    fun saveOrUpdateRecipe() {
        // *** MODIFIED: Get active profile ID before saving/updating ***
        val currentProfileId = appViewModel.activeProfileId.value
        if (currentProfileId.isNullOrBlank()) {
            _recipeDetailState.value = RecipeDetailUiState.Error("Nenhum perfil ativo selecionado para salvar a receita.")
            return
        }

        // *** MODIFIED: Set profileId in the recipe object ***
        val recipeToSave = createRecipeFromFormState().copy(profileId = currentProfileId)

        if (!validateRecipe(recipeToSave)) return

        _recipeDetailState.value = RecipeDetailUiState.Loading

        if (recipeUiState.id == null) {
            // Saving new recipe
            RecipeRepository.saveRecipe(
                recipe = recipeToSave, // Already has profileId
                onSuccess = {
                    _recipeDetailState.value = RecipeDetailUiState.OperationSuccess
                    resetFormState()
                },
                onError = { exception ->
                    _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao salvar receita: ${exception.message}")
                }
            )
        } else {
            // Updating existing recipe
            val currentRecipeId = recipeUiState.id!!
            // Ensure userId and profileId are preserved/correct in the object being sent
            val updatedRecipe = recipeToSave.copy(userId = recipeUiState.userId ?: "")
            RecipeRepository.updateRecipe(
                recipeId = currentRecipeId,
                recipe = updatedRecipe,
                onSuccess = {
                    _recipeDetailState.value = RecipeDetailUiState.OperationSuccess
                    resetFormState()
                },
                onError = { exception ->
                    _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao atualizar receita: ${exception.message}")
                }
            )
        }
    }

    fun deleteRecipe(recipeId: String) {
        _recipeDetailState.value = RecipeDetailUiState.Loading
        RecipeRepository.deleteRecipe(
            recipeId = recipeId,
            onSuccess = {
                _recipeDetailState.value = RecipeDetailUiState.OperationSuccess
                resetFormState()
            },
            onError = { exception ->
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao deletar receita: ${exception.message}")
            }
        )
    }

    // --- Form State Management ---
    fun updatePatientName(name: String) { recipeUiState = recipeUiState.copy(patientName = name) }
    fun updateDoctorName(name: String) { recipeUiState = recipeUiState.copy(doctorName = name) }
    fun updateCrm(crm: String) { recipeUiState = recipeUiState.copy(crm = crm) }
    fun updatePrescriptionDate(date: String) { recipeUiState = recipeUiState.copy(prescriptionDate = date) }
    fun updateGeneralInstructions(instructions: String) { recipeUiState = recipeUiState.copy(generalInstructions = instructions) }
    fun updateNotes(notes: String) { recipeUiState = recipeUiState.copy(notes = notes) }
    fun updateValidityDate(date: String) { recipeUiState = recipeUiState.copy(validityDate = date) }

    // --- Prescribed Medication Form Management ---
    fun updateCurrentMedName(name: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(name = name) }
    fun updateCurrentMedDose(dose: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(dose = dose) }
    fun updateCurrentMedInstructions(instructions: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(instructions = instructions) }

    fun startEditingPrescribedMedication(index: Int) {
        if (index in prescribedMedicationsState.indices) {
            editingMedicationIndex = index
            currentPrescribedMedicationState = prescribedMedicationsState[index]
        }
    }

    fun cancelEditingPrescribedMedication() {
        editingMedicationIndex = -1
        currentPrescribedMedicationState = PrescribedMedication() // Reset form
    }

    fun saveOrUpdateCurrentPrescribedMedication() {
        if (currentPrescribedMedicationState.name.isBlank()) return // Basic validation

        val currentList = prescribedMedicationsState.toMutableList()
        if (editingMedicationIndex != -1 && editingMedicationIndex in currentList.indices) {
            currentList[editingMedicationIndex] = currentPrescribedMedicationState
        } else {
            currentList.add(currentPrescribedMedicationState)
        }
        prescribedMedicationsState = currentList.toList()
        cancelEditingPrescribedMedication()
    }

    fun removePrescribedMedication(index: Int) {
        if (index in prescribedMedicationsState.indices) {
            val currentList = prescribedMedicationsState.toMutableList()
            currentList.removeAt(index)
            prescribedMedicationsState = currentList.toList()
            if (index == editingMedicationIndex) {
                cancelEditingPrescribedMedication()
            }
        }
    }

    // --- Helper Functions ---
    private fun resetFormState() {
        recipeUiState = RecipeFormData()
        prescribedMedicationsState = emptyList()
        cancelEditingPrescribedMedication()
    }

    private fun updateFormState(recipe: Recipe) {
        recipeUiState = RecipeFormData(
            id = recipe.id,
            userId = recipe.userId,
            profileId = recipe.profileId, // Store profileId in form state
            patientName = recipe.patientName,
            doctorName = recipe.doctorName,
            crm = recipe.crm,
            prescriptionDate = recipe.prescriptionDate,
            validityDate = recipe.validityDate,
            generalInstructions = recipe.generalInstructions,
            notes = recipe.notes,
            imageUri = recipe.imageUri
        )
        prescribedMedicationsState = recipe.prescribedMedications
        cancelEditingPrescribedMedication()
    }

    private fun createRecipeFromFormState(): Recipe {
        // profileId will be set/overwritten before saving
        return Recipe(
            id = recipeUiState.id,
            userId = recipeUiState.userId ?: "", // Use existing userId if updating
            profileId = recipeUiState.profileId ?: "", // Use existing profileId if updating
            patientName = recipeUiState.patientName.trim(),
            doctorName = recipeUiState.doctorName.trim(),
            crm = recipeUiState.crm.trim(),
            prescriptionDate = recipeUiState.prescriptionDate.trim(),
            validityDate = recipeUiState.validityDate.trim(),
            prescribedMedications = prescribedMedicationsState,
            generalInstructions = recipeUiState.generalInstructions.trim(),
            notes = recipeUiState.notes.trim(),
            imageUri = recipeUiState.imageUri
        )
    }

    private fun validateRecipe(recipe: Recipe): Boolean {
        var isValid = true
        var errorMessage = ""

        if (recipe.patientName.isBlank()) {
            errorMessage += "Nome do paciente não pode estar vazio.\n"
            isValid = false
        }
        if (recipe.doctorName.isBlank()) {
            errorMessage += "Nome do médico não pode estar vazio.\n"
            isValid = false
        }
        if (recipe.prescriptionDate.isBlank()) {
            errorMessage += "Data da prescrição não pode estar vazia.\n"
            isValid = false
        } else {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.isLenient = false
                sdf.parse(recipe.prescriptionDate)
            } catch (e: Exception) {
                errorMessage += "Formato da data da prescrição inválido (use DD/MM/YYYY).\n"
                isValid = false
            }
        }
        if (recipe.validityDate.isNotBlank()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.isLenient = false
                sdf.parse(recipe.validityDate)
            } catch (e: Exception) {
                errorMessage += "Formato da data de validade inválido (use DD/MM/YYYY).\n"
                isValid = false
            }
        }
        if (recipe.prescribedMedications.isEmpty()) {
            errorMessage += "Adicione pelo menos um medicamento prescrito.\n"
            isValid = false
        }
        // *** ADDED: Validate profileId ***
        if (recipe.profileId.isBlank()) {
            errorMessage += "Erro interno: ID do Perfil ausente.\n"
            isValid = false
        }

        if (!isValid) {
            _recipeDetailState.value = RecipeDetailUiState.Error(errorMessage.trim())
        }

        return isValid
    }
}

// *** MODIFIED: Added profileId ***
data class RecipeFormData(
    val id: String? = null,
    val userId: String? = null,
    val profileId: String? = null, // Keep track of original profileId when editing
    val patientName: String = "",
    val doctorName: String = "",
    val crm: String = "",
    val prescriptionDate: String = "",
    val validityDate: String = "",
    val generalInstructions: String = "",
    val notes: String = "",
    val imageUri: String? = null
)

