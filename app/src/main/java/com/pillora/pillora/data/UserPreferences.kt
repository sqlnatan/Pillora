package com.pillora.pillora.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Classe para gerenciar preferências do usuário usando SharedPreferences.
 *
 * IMPORTANTE: O status Premium é controlado EXCLUSIVAMENTE pelo BillingRepository.
 * Este arquivo apenas mantém um cache local para leitura rápida.
 *
 * REMOVIDO (para aprovação do Google):
 * - setPremium() - Permitia ativar premium manualmente
 * - simulateSubscriptionExpiration() - Função de teste
 * - simulateGracePeriodExpired() - Função de teste
 * - Sistema complexo de downgrade (Google Play gerencia automaticamente)
 */
class UserPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "user_prefs", Context.MODE_PRIVATE
    )

    // --- Fluxos de estado ---
    private val _acceptedTermsFlow = MutableStateFlow(getAcceptedTermsFromPrefs())
    val acceptedTerms: Flow<Boolean> = _acceptedTermsFlow.asStateFlow()

    // CACHE READ-ONLY: Apenas para leitura rápida, atualizado pelo BillingRepository
    private val _isPremiumFlow = MutableStateFlow(getPremiumFromPrefs())
    val isPremium: Flow<Boolean> = _isPremiumFlow.asStateFlow()

    // Flag para indicar que usuário precisa fazer seleção de downgrade
    private val _needsDowngradeSelectionFlow = MutableStateFlow(getNeedsDowngradeSelectionFromPrefs())
    val needsDowngradeSelection: Flow<Boolean> = _needsDowngradeSelectionFlow.asStateFlow()

    companion object {
        private const val KEY_ACCEPTED_TERMS = "accepted_terms"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_NEEDS_DOWNGRADE_SELECTION = "needs_downgrade_selection"
    }

    // --- Métodos internos ---
    private fun getAcceptedTermsFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_ACCEPTED_TERMS, false)
    }

    private fun getPremiumFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PREMIUM, false)
    }

    private fun getNeedsDowngradeSelectionFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_NEEDS_DOWNGRADE_SELECTION, false)
    }

    // --- Métodos públicos ---
    fun setAcceptedTerms(accepted: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_ACCEPTED_TERMS, accepted) }
        _acceptedTermsFlow.value = accepted
    }

    /**
     * MÉTODO INTERNO: Sincroniza o status premium do BillingRepository.
     *
     * IMPORTANTE: Este método deve ser chamado APENAS pelo BillingRepository.
     * Não expor publicamente para evitar manipulação manual do status premium.
     *
     * @param isPremium Status premium verificado pelo Google Play Billing
     */
    internal fun syncPremiumFromBilling(isPremium: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_IS_PREMIUM, isPremium) }
        _isPremiumFlow.value = isPremium
    }

    /**
     * Marca que o usuário precisa fazer seleção de downgrade.
     * Chamado pelo BillingRepository quando detecta perda de premium.
     */
    fun setNeedsDowngradeSelection(needs: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_NEEDS_DOWNGRADE_SELECTION, needs) }
        _needsDowngradeSelectionFlow.value = needs
    }

    /**
     * Marca que o downgrade foi completado.
     * Chamado após o usuário selecionar medicamentos/consultas.
     */
    fun setDowngradeCompleted() {
        setNeedsDowngradeSelection(false)
    }
}
