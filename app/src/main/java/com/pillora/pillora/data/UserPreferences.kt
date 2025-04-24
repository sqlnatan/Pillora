package com.pillora.pillora.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.Preferences

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        val ACCEPTED_TERMS = booleanPreferencesKey("accepted_terms")
    }

    val acceptedTerms: Flow<Boolean> = context.dataStore.data
        .map { it[ACCEPTED_TERMS] ?: false }

    suspend fun setAcceptedTerms(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ACCEPTED_TERMS] = accepted
        }
    }
}
