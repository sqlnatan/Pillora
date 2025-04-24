package com.pillora.pillora.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Classe para gerenciar preferências do usuário usando SharedPreferences
 * como alternativa ao DataStore que está causando problemas de importação
 */
class UserPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "user_prefs", Context.MODE_PRIVATE
    )

    private val _acceptedTermsFlow = MutableStateFlow(getAcceptedTermsFromPrefs())
    val acceptedTerms: Flow<Boolean> = _acceptedTermsFlow.asStateFlow()

    companion object {
        private const val KEY_ACCEPTED_TERMS = "accepted_terms"
    }

    /**
     * Obtém o valor atual de aceitação dos termos
     */
    private fun getAcceptedTermsFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_ACCEPTED_TERMS, false)
    }

    /**
     * Define se o usuário aceitou os termos
     */
    fun setAcceptedTerms(accepted: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_ACCEPTED_TERMS, accepted) }
        _acceptedTermsFlow.value = accepted
    }
}
