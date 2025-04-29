package com.pillora.pillora.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit // Importar a extensão KTX
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Enum para preferências de tema
enum class ThemePreference { LIGHT, DARK, SYSTEM }

class SettingsViewModel(context: Context) : ViewModel() {

    // Usar applicationContext para evitar memory leaks
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)

    private val _themePreference = MutableStateFlow(getThemePreference())
    val themePreference: StateFlow<ThemePreference> = _themePreference

    // Preferências de notificação
    private val _doseRemindersEnabled = MutableStateFlow(prefs.getBoolean("notification_dose_reminders_enabled", true))
    val doseRemindersEnabled: StateFlow<Boolean> = _doseRemindersEnabled

    private val _stockAlertsEnabled = MutableStateFlow(prefs.getBoolean("notification_stock_alerts_enabled", true))
    val stockAlertsEnabled: StateFlow<Boolean> = _stockAlertsEnabled

    // TODO: Adicionar StateFlows para outras notificações (consultas, vacinas) quando implementadas

    private fun getThemePreference(): ThemePreference {
        return when (prefs.getString("theme_preference", ThemePreference.SYSTEM.name)) {
            ThemePreference.LIGHT.name -> ThemePreference.LIGHT
            ThemePreference.DARK.name -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            // Usar KTX edit
            prefs.edit {
                putString("theme_preference", preference.name)
            }
            _themePreference.value = preference
        }
    }

    fun setDoseRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Usar KTX edit
            prefs.edit {
                putBoolean("notification_dose_reminders_enabled", enabled)
            }
            _doseRemindersEnabled.value = enabled
        }
    }

    fun setStockAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Usar KTX edit
            prefs.edit {
                putBoolean("notification_stock_alerts_enabled", enabled)
            }
            _stockAlertsEnabled.value = enabled
        }
    }

    // TODO: Adicionar funções set para outras preferências de notificação
}

