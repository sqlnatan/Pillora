package com.pillora.pillora.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.PrescribedMedication
import com.pillora.pillora.model.Recipe
import com.pillora.pillora.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Represents the different states for the Recipe List screen
sealed class RecipeListUiState {
    object Loading : RecipeListUiState()
    data class Success(val recipes: List<Recipe>) : RecipeListUiState()
    data class Error(val message: String) : RecipeListUiState()
}

// Represents the different states for the Recipe Form/Detail screen
sealed class RecipeDetailUiState {
    object Loading : RecipeDetailUiState()
    data class Success(val recipe: Recipe) : RecipeDetailUiState()
    data class Error(val message: String) : RecipeDetailUiState()
    object Idle : RecipeDetailUiState() // Initial state or after successful save/delete
}

class RecipeViewModel : ViewModel() {

    private val _recipeListState = MutableStateFlow<RecipeListUiState>(RecipeListUiState.Loading)
    val recipeListState: StateFlow<RecipeListUiState> = _recipeListState.asStateFlow()

    private val _recipeDetailState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Idle)
    val recipeDetailState: StateFlow<RecipeDetailUiState> = _recipeDetailState.asStateFlow()

    // Form state for adding/editing recipes
    var recipeUiState by mutableStateOf(RecipeFormData()) // Holds the current form data
        private set

    // State for managing prescribed medications within the form
    var prescribedMedicationsState by mutableStateOf<List<PrescribedMedication>>(emptyList())
        private set

    // State for the currently edited/added prescribed medication
    var currentPrescribedMedicationState by mutableStateOf(PrescribedMedication())
        private set

    init {
        loadRecipes()
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            RecipeRepository.getAllRecipesFlow()
                .catch { e ->
                    _recipeListState.value = RecipeListUiState.Error("Erro ao carregar receitas: ${e.message}")
                }
                .collect { recipes ->
                    _recipeListState.value = RecipeListUiState.Success(recipes)
                }
        }
    }

    fun loadRecipeDetails(recipeId: String) {
        if (recipeId.isBlank()) {
            // New recipe mode
            resetFormState()
            _recipeDetailState.value = RecipeDetailUiState.Idle // Ready for new input
            return
        }

        _recipeDetailState.value = RecipeDetailUiState.Loading
        RecipeRepository.getRecipeById(
            recipeId = recipeId,
            onSuccess = { recipe ->
                if (recipe != null) {
                    updateFormState(recipe)
                    _recipeDetailState.value = RecipeDetailUiState.Success(recipe)
                } else {
                    _recipeDetailState.value = RecipeDetailUiState.Error("Receita não encontrada.")
                }
            },
            onError = { exception ->
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao carregar detalhes da receita: ${exception.message}")
            }
        )
    }

    fun saveOrUpdateRecipe() {
        val recipeToSave = createRecipeFromFormState()
        if (!validateRecipe(recipeToSave)) return // Validation handled within validateRecipe

        _recipeDetailState.value = RecipeDetailUiState.Loading

        if (recipeUiState.id == null) {
            // Save new recipe
            RecipeRepository.saveRecipe(
                recipe = recipeToSave,
                onSuccess = {
                    _recipeDetailState.value = RecipeDetailUiState.Idle // Reset state after save
                    resetFormState()
                },
                onError = { exception ->
                    _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao salvar receita: ${exception.message}")
                }
            )
        } else {
            // Update existing recipe
            RecipeRepository.updateRecipe(
                recipeId = recipeUiState.id,
                recipe = recipeToSave.copy(userId = recipeUiState.userId), // Ensure userId is passed for update check
                onSuccess = {
                    _recipeDetailState.value = RecipeDetailUiState.Idle // Reset state after update
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
                _recipeDetailState.value = RecipeDetailUiState.Idle // Reset state after delete
                resetFormState()
            },
            onError = { exception ->
                // If deletion fails, maybe revert to showing the detail?
                // For now, just show error. Consider state management for failed delete.
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao deletar receita: ${exception.message}")
            }
        )
    }

    // --- Form State Management ---

    fun updatePatientName(name: String) {
        recipeUiState = recipeUiState.copy(patientName = name)
    }

    fun updateDoctorName(name: String) {
        recipeUiState = recipeUiState.copy(doctorName = name)
    }

    fun updateCrm(crm: String) {
        recipeUiState = recipeUiState.copy(crm = crm)
    }

    fun updatePrescriptionDate(date: String) {
        // Basic validation or formatting can be added here if needed
        recipeUiState = recipeUiState.copy(prescriptionDate = date)
    }

    fun updateGeneralInstructions(instructions: String) {
        recipeUiState = recipeUiState.copy(generalInstructions = instructions)
    }

    fun updateNotes(notes: String) {
        recipeUiState = recipeUiState.copy(notes = notes)
    }

    fun updateImageUri(uri: String?) {
        recipeUiState = recipeUiState.copy(imageUri = uri)
    }

    // --- Prescribed Medication Form Management ---

    fun updateCurrentMedName(name: String) {
        currentPrescribedMedicationState = currentPrescribedMedicationState.copy(name = name)
    }

    fun updateCurrentMedDose(dose: String) {
        currentPrescribedMedicationState = currentPrescribedMedicationState.copy(dose = dose)
    }

    fun updateCurrentMedInstructions(instructions: String) {
        currentPrescribedMedicationState = currentPrescribedMedicationState.copy(instructions = instructions)
    }

    fun addCurrentPrescribedMedication() {
        if (currentPrescribedMedicationState.name.isNotBlank()) { // Basic validation
            prescribedMedicationsState = prescribedMedicationsState + currentPrescribedMedicationState
            currentPrescribedMedicationState = PrescribedMedication() // Reset for next entry
        }
    }

    fun removePrescribedMedication(medication: PrescribedMedication) {
        prescribedMedicationsState = prescribedMedicationsState - medication
    }

    // --- Helper Functions ---

    private fun resetFormState() {
        recipeUiState = RecipeFormData()
        prescribedMedicationsState = emptyList()
        currentPrescribedMedicationState = PrescribedMedication()
    }

    private fun updateFormState(recipe: Recipe) {
        recipeUiState = RecipeFormData(
            id = recipe.id,
            userId = recipe.userId,
            patientName = recipe.patientName,
            doctorName = recipe.doctorName,
            crm = recipe.crm,
            prescriptionDate = recipe.prescriptionDate,
            generalInstructions = recipe.generalInstructions,
            notes = recipe.notes,
            imageUri = recipe.imageUri
        )
        prescribedMedicationsState = recipe.prescribedMedications
        currentPrescribedMedicationState = PrescribedMedication() // Reset current med form
    }

    private fun createRecipeFromFormState(): Recipe {
        return Recipe(
            id = recipeUiState.id,
            userId = recipeUiState.userId, // Important for update checks
            patientName = recipeUiState.patientName.trim(),
            doctorName = recipeUiState.doctorName.trim(),
            crm = recipeUiState.crm.trim(),
            prescriptionDate = recipeUiState.prescriptionDate.trim(),
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
            // Simple date format validation (DD/MM/YYYY)
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.isLenient = false
                sdf.parse(recipe.prescriptionDate)
            } catch (e: Exception) {
                errorMessage += "Formato da data da prescrição inválido (use DD/MM/YYYY).\n"
                isValid = false
            }
        }
        if (recipe.prescribedMedications.isEmpty()) {
            errorMessage += "Adicione pelo menos um medicamento prescrito.\n"
            isValid = false
        }

        if (!isValid) {
            _recipeDetailState.value = RecipeDetailUiState.Error(errorMessage.trim())
        }

        return isValid
    }
}

// Data class to hold the form state separately
data class RecipeFormData(
    val id: String? = null,
    val userId: String? = null,
    val patientName: String = "",
    val doctorName: String = "",
    val crm: String = "",
    val prescriptionDate: String = "",
    val generalInstructions: String = "",
    val notes: String = "",
    val imageUri: String? = null
    // prescribedMedications are handled by a separate state list
)

