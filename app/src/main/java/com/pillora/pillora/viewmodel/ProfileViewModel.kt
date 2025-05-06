package com.pillora.pillora.viewmodel

import android.app.Application // Import Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel // Changed to AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pillora.pillora.model.Profile
import com.pillora.pillora.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
// Removed unused import: import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// Represents the different states for the Profile List screen
sealed class ProfileListUiState {
    data object Loading : ProfileListUiState()
    data class Success(val profiles: List<Profile>) : ProfileListUiState()
    data class Error(val message: String) : ProfileListUiState()
}

// Represents the different states for the Profile Form/Detail screen
sealed class ProfileDetailUiState {
    data object Loading : ProfileDetailUiState()
    data class Success(val profile: Profile) : ProfileDetailUiState() // Data loaded
    data class Error(val message: String) : ProfileDetailUiState()
    data object OperationSuccess : ProfileDetailUiState() // Save/Update/Delete successful
    data object Idle : ProfileDetailUiState() // Initial or after operation
}

// *** MODIFIED: Changed to AndroidViewModel to get context for Repository ***
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    // *** MODIFIED: Instantiate Repository here (or use DI) ***
    private val profileRepository = ProfileRepository()

    private val _profileListState = MutableStateFlow<ProfileListUiState>(ProfileListUiState.Loading)
    val profileListState: StateFlow<ProfileListUiState> = _profileListState.asStateFlow()

    private val _profileDetailState = MutableStateFlow<ProfileDetailUiState>(ProfileDetailUiState.Idle)
    val profileDetailState: StateFlow<ProfileDetailUiState> = _profileDetailState.asStateFlow()

    // Form state for adding/editing profiles
    var profileUiState by mutableStateOf(ProfileFormData())
        private set

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // *** MODIFIED: Use repository instance ***
            profileRepository.getAllProfilesFlow(userId)
                .catch { e ->
                    _profileListState.value = ProfileListUiState.Error("Erro ao carregar perfis: ${e.message}")
                }
                .collect { profiles ->
                    _profileListState.value = ProfileListUiState.Success(profiles)
                }
        }
    }

    fun loadProfileDetails(profileId: String?) {
        if (profileId.isNullOrBlank()) {
            resetFormState()
            _profileDetailState.value = ProfileDetailUiState.Idle
            return
        }

        _profileDetailState.value = ProfileDetailUiState.Loading
        viewModelScope.launch {
            // *** MODIFIED: Use repository instance and handle suspend function ***
            val profile = profileRepository.getProfileById(profileId)
            if (profile != null) {
                updateFormState(profile)
                _profileDetailState.value = ProfileDetailUiState.Success(profile)
            } else {
                _profileDetailState.value = ProfileDetailUiState.Error("Perfil não encontrado.")
            }
        }
    }

    fun saveOrUpdateProfile() {
        val profileToSave = createProfileFromFormState()
        if (!validateProfile(profileToSave)) return

        _profileDetailState.value = ProfileDetailUiState.Loading

        viewModelScope.launch {
            val result = if (profileUiState.id == null) {
                // Saving new profile
                // *** MODIFIED: Use repository instance ***
                profileRepository.saveProfile(profileToSave)
            } else {
                // Updating existing profile
                val currentProfileId = profileUiState.id!!
                // *** CORRECTED: Ensure userId is non-null before copying ***
                val userIdToSave = profileUiState.userId ?: auth.currentUser?.uid ?: ""
                // *** MODIFIED: Use repository instance ***
                profileRepository.updateProfile(currentProfileId, profileToSave.copy(userId = userIdToSave))
            }

            result.fold(
                onSuccess = {
                    _profileDetailState.value = ProfileDetailUiState.OperationSuccess
                    resetFormState()
                },
                onFailure = { exception ->
                    _profileDetailState.value = ProfileDetailUiState.Error("Erro ao salvar/atualizar perfil: ${exception.message}")
                }
            )
        }
    }

    fun deleteProfileAndData(profileId: String) {
        _profileDetailState.value = ProfileDetailUiState.Loading
        viewModelScope.launch {
            // *** MODIFIED: Use repository instance ***
            val result = profileRepository.deleteProfileAndData(profileId)
            result.fold(
                onSuccess = {
                    _profileDetailState.value = ProfileDetailUiState.OperationSuccess
                    resetFormState()
                    // TODO: Handle if the deleted profile was the active one (in AppViewModel)
                },
                onFailure = { exception ->
                    _profileDetailState.value = ProfileDetailUiState.Error("Erro ao deletar perfil: ${exception.message}")
                }
            )
        }
    }

    // Function to reset the detail state to Idle
    fun resetDetailState() {
        _profileDetailState.value = ProfileDetailUiState.Idle
    }

    // --- Form State Management ---
    fun updateName(name: String) { profileUiState = profileUiState.copy(name = name) }
    fun updateRelationship(relationship: String) { profileUiState = profileUiState.copy(relationship = relationship) }

    // --- Helper Functions ---
    private fun resetFormState() {
        profileUiState = ProfileFormData()
    }

    private fun updateFormState(profile: Profile) {
        profileUiState = ProfileFormData(
            id = profile.id,
            userId = profile.userId,
            name = profile.name,
            relationship = profile.relationship
        )
    }

    private fun createProfileFromFormState(): Profile {
        // userId will be set/confirmed by the repository during save/update
        return Profile(
            id = profileUiState.id,
            userId = profileUiState.userId ?: "", // Use existing userId if updating
            name = profileUiState.name.trim(),
            relationship = profileUiState.relationship.trim()
        )
    }

    private fun validateProfile(profile: Profile): Boolean {
        var isValid = true
        var errorMessage = ""

        if (profile.name.isBlank()) {
            errorMessage += "Nome do perfil não pode estar vazio.\n"
            isValid = false
        }

        if (!isValid) {
            _profileDetailState.value = ProfileDetailUiState.Error(errorMessage.trim())
        }

        return isValid
    }
}

// Data class to hold the form state for Profile
data class ProfileFormData(
    val id: String? = null,
    val userId: String? = null, // Keep track of original userId when editing
    val name: String = "",
    val relationship: String = ""
)

