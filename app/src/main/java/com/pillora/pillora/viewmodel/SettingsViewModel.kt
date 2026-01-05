package com.pillora.pillora.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Enum para preferências de tema
enum class ThemePreference { LIGHT, DARK, SYSTEM }

/**
 * ViewModel para gerenciar configurações do app.
 *
 * VERSÃO LIMPA: Sem setPremiumStatus ou funções de teste.
 * Status premium é observado do BillingRepository.
 *
 * REMOVIDO (para aprovação do Google):
 * - setPremiumStatus() - Permitia ativar premium manualmente
 */
class SettingsViewModel(
    context: Context,
    private val billingRepository: BillingRepository
) : ViewModel() {

    // Usar applicationContext para evitar memory leaks
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)

    private val _themePreference = MutableStateFlow(getThemePreference())
    val themePreference: StateFlow<ThemePreference> = _themePreference

    // Status Premium (READ-ONLY do BillingRepository)
    val isPremium: StateFlow<Boolean> = billingRepository.isPremium

    private fun getThemePreference(): ThemePreference {
        return when (prefs.getString("theme_preference", ThemePreference.SYSTEM.name)) {
            ThemePreference.LIGHT.name -> ThemePreference.LIGHT
            ThemePreference.DARK.name -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            prefs.edit {
                putString("theme_preference", preference.name)
            }
            _themePreference.value = preference
        }
    }
}
