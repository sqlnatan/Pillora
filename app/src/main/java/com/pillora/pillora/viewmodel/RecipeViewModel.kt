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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Represents the different states for the Recipe List screen
sealed class RecipeListUiState {
    data object Loading : RecipeListUiState() // Use data object
    data class Success(val recipes: List<Recipe>) : RecipeListUiState()
    data class Error(val message: String) : RecipeListUiState()
}

// Represents the different states for the Recipe Form/Detail screen
sealed class RecipeDetailUiState {
    data object Loading : RecipeDetailUiState() // Use data object
    data class Success(val recipe: Recipe) : RecipeDetailUiState()
    data class Error(val message: String) : RecipeDetailUiState()
    data object Idle : RecipeDetailUiState() // Use data object // Initial state or after successful save/delete
}

class RecipeViewModel : ViewModel() {

    private val _recipeListState = MutableStateFlow<RecipeListUiState>(RecipeListUiState.Loading)
    val recipeListState: StateFlow<RecipeListUiState> = _recipeListState.asStateFlow()

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
            resetFormState()
            _recipeDetailState.value = RecipeDetailUiState.Idle
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
        if (!validateRecipe(recipeToSave)) return

        _recipeDetailState.value = RecipeDetailUiState.Loading

        if (recipeUiState.id == null) {
            RecipeRepository.saveRecipe(
                recipe = recipeToSave,
                onSuccess = {
                    _recipeDetailState.value = RecipeDetailUiState.Idle
                    resetFormState()
                    // Consider navigation back here or via LaunchedEffect in Screen
                },
                onError = { exception ->
                    _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao salvar receita: ${exception.message}")
                }
            )
        } else {
            val currentRecipeId = recipeUiState.id!!
            RecipeRepository.updateRecipe(
                recipeId = currentRecipeId,
                recipe = recipeToSave.copy(userId = recipeUiState.userId),
                onSuccess = {
                    _recipeDetailState.value = RecipeDetailUiState.Idle
                    resetFormState()
                    // Consider navigation back here or via LaunchedEffect in Screen
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
                _recipeDetailState.value = RecipeDetailUiState.Idle
                resetFormState()
                // Navigation back is usually handled in the Screen after calling delete
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

    fun updateValidityDate(date: String) { recipeUiState = recipeUiState.copy(validityDate = date) } // Added validity date update

    /* // Function commented out as image feature is not implemented
    fun updateImageUri(uri: String?) {
        recipeUiState = recipeUiState.copy(imageUri = uri)
    }
    */

    // --- Prescribed Medication Form Management ---
    fun updateCurrentMedName(name: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(name = name) }
    fun updateCurrentMedDose(dose: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(dose = dose) }
    fun updateCurrentMedInstructions(instructions: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(instructions = instructions) }

    // Starts editing a medication at the given index
    fun startEditingPrescribedMedication(index: Int) {
        if (index in prescribedMedicationsState.indices) {
            editingMedicationIndex = index
            currentPrescribedMedicationState = prescribedMedicationsState[index]
        }
    }

    // Cancels the current editing operation
    fun cancelEditingPrescribedMedication() {
        editingMedicationIndex = -1
        currentPrescribedMedicationState = PrescribedMedication() // Reset form
    }

    // Adds a new medication or updates the one being edited
    fun saveOrUpdateCurrentPrescribedMedication() {
        if (currentPrescribedMedicationState.name.isBlank()) return // Basic validation

        val currentList = prescribedMedicationsState.toMutableList()
        if (editingMedicationIndex != -1 && editingMedicationIndex in currentList.indices) {
            // Update existing medication
            currentList[editingMedicationIndex] = currentPrescribedMedicationState
        } else {
            // Add new medication
            currentList.add(currentPrescribedMedicationState)
        }
        prescribedMedicationsState = currentList.toList() // Update the state list
        cancelEditingPrescribedMedication() // Reset editing state and form
    }

    fun removePrescribedMedication(index: Int) {
        if (index in prescribedMedicationsState.indices) {
            val currentList = prescribedMedicationsState.toMutableList()
            currentList.removeAt(index)
            prescribedMedicationsState = currentList.toList()
            // If the removed item was being edited, cancel editing
            if (index == editingMedicationIndex) {
                cancelEditingPrescribedMedication()
            }
        }
    }

    // --- Helper Functions ---
    private fun resetFormState() {
        recipeUiState = RecipeFormData() // Includes validityDate reset to ""
        prescribedMedicationsState = emptyList()
        cancelEditingPrescribedMedication() // Also reset medication form/state
    }

    private fun updateFormState(recipe: Recipe) {
        recipeUiState = RecipeFormData(
            id = recipe.id,
            userId = recipe.userId,
            patientName = recipe.patientName,
            doctorName = recipe.doctorName,
            crm = recipe.crm,
            prescriptionDate = recipe.prescriptionDate,
            validityDate = recipe.validityDate, // Populate validity date
            generalInstructions = recipe.generalInstructions,
            notes = recipe.notes,
            imageUri = recipe.imageUri
        )
        prescribedMedicationsState = recipe.prescribedMedications
        cancelEditingPrescribedMedication() // Reset medication form/state
    }

    private fun createRecipeFromFormState(): Recipe {
        return Recipe(
            id = recipeUiState.id,
            userId = recipeUiState.userId,
            patientName = recipeUiState.patientName.trim(),
            doctorName = recipeUiState.doctorName.trim(),
            crm = recipeUiState.crm.trim(),
            prescriptionDate = recipeUiState.prescriptionDate.trim(),
            validityDate = recipeUiState.validityDate.trim(), // Add validity date
            prescribedMedications = prescribedMedicationsState, // Use the updated list
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

data class RecipeFormData(
    val id: String? = null,
    val userId: String? = null,
    val patientName: String = "",
    val doctorName: String = "",
    val crm: String = "",
    val prescriptionDate: String = "",
    val validityDate: String = "", // Added validity date
    val generalInstructions: String = "",
    val notes: String = "",
    val imageUri: String? = null
)

