package com.pillora.pillora.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Classe para gerenciar preferências do usuário usando SharedPreferences
 * (mais leve que DataStore e sem dependências extras)
 */
class UserPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "user_prefs", Context.MODE_PRIVATE
    )

    // --- Fluxos de estado ---
    private val _acceptedTermsFlow = MutableStateFlow(getAcceptedTermsFromPrefs())
    val acceptedTerms: Flow<Boolean> = _acceptedTermsFlow.asStateFlow()

    private val _isPremiumFlow = MutableStateFlow(getPremiumFromPrefs()) // true pra testar depois inserir getPremiumFromPrefs() <---------------------------------- PREMIUM
    val isPremium: Flow<Boolean> = _isPremiumFlow.asStateFlow()

    companion object {
        private const val KEY_ACCEPTED_TERMS = "accepted_terms"
        private const val KEY_IS_PREMIUM = "is_premium"
    }

    // --- Métodos internos ---
    private fun getAcceptedTermsFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_ACCEPTED_TERMS, false)
    }

    private fun getPremiumFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PREMIUM, false)
    }

    // --- Métodos públicos ---
    fun setAcceptedTerms(accepted: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_ACCEPTED_TERMS, accepted) }
        _acceptedTermsFlow.value = accepted
    }

    fun setPremium(isPremium: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_IS_PREMIUM, isPremium) }
        _isPremiumFlow.value = isPremium
    }
}
