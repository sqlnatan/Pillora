package com.pillora.pillora.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A ViewModel shared across the application to manage global states like the active profile.
 *
 * Requires Application context to initialize SettingsRepository.
 * Consider using Hilt for proper dependency injection and scoping if your project uses it.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    // Initialize the repository (In a real app, use Dependency Injection e.g., Hilt)
    private val settingsRepository = SettingsRepository(application.applicationContext)

    /**
     * Flow representing the currently active profile ID.
     * Null if no profile is selected.
     */
    val activeProfileId: StateFlow<String?> = settingsRepository.activeProfileIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last subscriber
            initialValue = null // Start with no profile selected (or load last known value)
        )

    /**
     * Selects a profile as the active one.
     *
     * @param profileId The ID of the profile to set as active, or null to clear selection.
     */
    fun selectActiveProfile(profileId: String?) {
        viewModelScope.launch {
            settingsRepository.setActiveProfileId(profileId)
        }
    }

    // --- Add other global states or functions here if needed ---
    // Example: Theme preference
    // val themePreference: StateFlow<String?> = settingsRepository.themePreferenceFlow.stateIn(...)
    // fun setThemePreference(theme: String) { ... }

}
