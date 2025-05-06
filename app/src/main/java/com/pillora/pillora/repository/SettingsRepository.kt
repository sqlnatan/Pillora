package com.pillora.pillora.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define the DataStore instance at the top level
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {

    // *** RENAMED: Removed underscore from constant name ***
    private val activeProfileIdKey = stringPreferencesKey("active_profile_id")

    // Flow to observe the active profile ID
    val activeProfileIdFlow: Flow<String?> = context.dataStore.data
        .map {
                preferences ->
            // *** CORRECTED: Use renamed key ***
            preferences[activeProfileIdKey]
        }

    // Function to update the active profile ID
    suspend fun setActiveProfileId(profileId: String?) {
        context.dataStore.edit {
                settings ->
            if (profileId == null) {
                // *** CORRECTED: Use renamed key ***
                settings.remove(activeProfileIdKey)
            } else {
                // *** CORRECTED: Use renamed key ***
                settings[activeProfileIdKey] = profileId
            }
        }
    }

    // --- Add other settings keys and functions here if needed ---
    // Example: Theme preference
    // private val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    // val themePreferenceFlow: Flow<String?> = ...
    // suspend fun setThemePreference(theme: String) { ... }
}
