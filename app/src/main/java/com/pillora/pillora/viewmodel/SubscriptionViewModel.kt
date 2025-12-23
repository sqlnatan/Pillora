package com.pillora.pillora.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.data.SubscriptionStatus
import com.pillora.pillora.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o estado da assinatura e processo de downgrade.
 */
class SubscriptionViewModel(
    application: Application,
    private val userPreferences: UserPreferences
) : AndroidViewModel(application) {

    private val _subscriptionStatus = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.FREE)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    private val _showGracePeriodWarning = MutableStateFlow(false)
    val showGracePeriodWarning: StateFlow<Boolean> = _showGracePeriodWarning.asStateFlow()

    private val _gracePeriodDaysRemaining = MutableStateFlow(0)
    val gracePeriodDaysRemaining: StateFlow<Int> = _gracePeriodDaysRemaining.asStateFlow()

    private val _requiresDowngrade = MutableStateFlow(false)
    val requiresDowngrade: StateFlow<Boolean> = _requiresDowngrade.asStateFlow()

    // Flag para controlar se o aviso de período de carência já foi mostrado nesta sessão
    private val _gracePeriodWarningShownThisSession = MutableStateFlow(false)

    init {
        checkSubscriptionStatus()
    }

    /**
     * Verifica o status atual da assinatura e atualiza os estados.
     */
    fun checkSubscriptionStatus() {
        viewModelScope.launch {
            val status = userPreferences.getSubscriptionStatus()
            _subscriptionStatus.value = status

            when (status) {
                is SubscriptionStatus.PREMIUM_ACTIVE -> {
                    _showGracePeriodWarning.value = false
                    _requiresDowngrade.value = false
                    _gracePeriodDaysRemaining.value = 0
                }
                is SubscriptionStatus.FREE -> {
                    _showGracePeriodWarning.value = false
                    _requiresDowngrade.value = false
                    _gracePeriodDaysRemaining.value = 0
                }
                is SubscriptionStatus.GRACE_PERIOD -> {
                    _gracePeriodDaysRemaining.value = status.daysRemaining
                    // Mostrar aviso apenas se ainda não foi mostrado nesta sessão
                    if (!_gracePeriodWarningShownThisSession.value) {
                        _showGracePeriodWarning.value = true
                    }
                    _requiresDowngrade.value = false
                }
                is SubscriptionStatus.DOWNGRADE_REQUIRED -> {
                    _showGracePeriodWarning.value = false
                    _requiresDowngrade.value = true
                    _gracePeriodDaysRemaining.value = 0
                }
            }
        }
    }

    /**
     * Marca que o aviso de período de carência foi visto.
     */
    fun dismissGracePeriodWarning() {
        _showGracePeriodWarning.value = false
        _gracePeriodWarningShownThisSession.value = true
    }

    /**
     * Marca que o downgrade foi completado.
     */
    fun completeDowngrade() {
        viewModelScope.launch {
            userPreferences.setDowngradeCompleted(true)
            _requiresDowngrade.value = false
            checkSubscriptionStatus()
        }
    }

    /**
     * Simula a expiração da assinatura (para testes).
     */
    fun simulateExpiration() {
        viewModelScope.launch {
            userPreferences.simulateSubscriptionExpiration()
            _gracePeriodWarningShownThisSession.value = false
            checkSubscriptionStatus()
        }
    }

    /**
     * Simula que o período de carência expirou (para testes).
     */
    fun simulateGracePeriodExpired() {
        viewModelScope.launch {
            userPreferences.simulateGracePeriodExpired()
            _gracePeriodWarningShownThisSession.value = false
            checkSubscriptionStatus()
        }
    }

    /**
     * Reseta o estado de downgrade (usado quando usuário renova assinatura).
     */
    fun resetDowngradeState() {
        viewModelScope.launch {
            userPreferences.resetDowngradeData()
            userPreferences.setPremium(true)
            _gracePeriodWarningShownThisSession.value = false
            checkSubscriptionStatus()
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            userPreferences: UserPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SubscriptionViewModel(application, userPreferences) as T
            }
        }
    }
}
