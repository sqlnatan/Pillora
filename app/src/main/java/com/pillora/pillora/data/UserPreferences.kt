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

    // Novos fluxos para gerenciamento de downgrade
    private val _subscriptionExpirationDateFlow = MutableStateFlow(getSubscriptionExpirationDateFromPrefs())
    val subscriptionExpirationDate: Flow<Long> = _subscriptionExpirationDateFlow.asStateFlow()

    private val _downgradeCompletedFlow = MutableStateFlow(getDowngradeCompletedFromPrefs())
    val downgradeCompleted: Flow<Boolean> = _downgradeCompletedFlow.asStateFlow()

    private val _downgradeRequiredDateFlow = MutableStateFlow(getDowngradeRequiredDateFromPrefs())
    val downgradeRequiredDate: Flow<Long> = _downgradeRequiredDateFlow.asStateFlow()

    companion object {
        private const val KEY_ACCEPTED_TERMS = "accepted_terms"
        private const val KEY_IS_PREMIUM = "is_premium"

        // Novas chaves para gerenciamento de downgrade
        private const val KEY_SUBSCRIPTION_EXPIRATION_DATE = "subscription_expiration_date"
        private const val KEY_DOWNGRADE_COMPLETED = "downgrade_completed"
        private const val KEY_DOWNGRADE_REQUIRED_DATE = "downgrade_required_date"

        // Período de carência em milissegundos (5 dias)
        const val GRACE_PERIOD_DAYS = 5
        const val GRACE_PERIOD_MILLIS = GRACE_PERIOD_DAYS * 24 * 60 * 60 * 1000L
    }

    // --- Métodos internos ---
    private fun getAcceptedTermsFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_ACCEPTED_TERMS, false)
    }

    private fun getPremiumFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PREMIUM, false)
    }

    private fun getSubscriptionExpirationDateFromPrefs(): Long {
        return sharedPreferences.getLong(KEY_SUBSCRIPTION_EXPIRATION_DATE, 0L)
    }

    private fun getDowngradeCompletedFromPrefs(): Boolean {
        return sharedPreferences.getBoolean(KEY_DOWNGRADE_COMPLETED, false)
    }

    private fun getDowngradeRequiredDateFromPrefs(): Long {
        return sharedPreferences.getLong(KEY_DOWNGRADE_REQUIRED_DATE, 0L)
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

    /**
     * Define a data de expiração da assinatura Premium.
     * Quando a assinatura expira, inicia o período de carência de 5 dias.
     */
    fun setSubscriptionExpirationDate(timestamp: Long) {
        sharedPreferences.edit { putLong(KEY_SUBSCRIPTION_EXPIRATION_DATE, timestamp) }
        _subscriptionExpirationDateFlow.value = timestamp
    }

    /**
     * Marca se o downgrade foi completado (usuário selecionou os itens para manter).
     */
    fun setDowngradeCompleted(completed: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_DOWNGRADE_COMPLETED, completed) }
        _downgradeCompletedFlow.value = completed
    }

    /**
     * Define a data quando o downgrade foi requerido (quando a assinatura expirou).
     */
    fun setDowngradeRequiredDate(timestamp: Long) {
        sharedPreferences.edit { putLong(KEY_DOWNGRADE_REQUIRED_DATE, timestamp) }
        _downgradeRequiredDateFlow.value = timestamp
    }

    /**
     * Verifica o status atual da assinatura considerando período de carência.
     * @return SubscriptionStatus indicando o estado atual
     */
    fun getSubscriptionStatus(): SubscriptionStatus {
        val isPremium = getPremiumFromPrefs()
        val expirationDate = getSubscriptionExpirationDateFromPrefs()
        val downgradeCompleted = getDowngradeCompletedFromPrefs()
        val downgradeRequiredDate = getDowngradeRequiredDateFromPrefs()
        val currentTime = System.currentTimeMillis()

        return when {
            // Usuário é premium ativo
            isPremium -> SubscriptionStatus.PREMIUM_ACTIVE

            // Assinatura nunca foi premium ou já fez downgrade
            expirationDate == 0L -> SubscriptionStatus.FREE

            // Downgrade já foi completado
            downgradeCompleted -> SubscriptionStatus.FREE

            // Está no período de carência (5 dias após expiração)
            currentTime < expirationDate + GRACE_PERIOD_MILLIS -> {
                val daysRemaining = ((expirationDate + GRACE_PERIOD_MILLIS - currentTime) / (24 * 60 * 60 * 1000)).toInt()
                SubscriptionStatus.GRACE_PERIOD(daysRemaining)
            }

            // Período de carência expirou, precisa fazer downgrade
            else -> SubscriptionStatus.DOWNGRADE_REQUIRED
        }
    }

    /**
     * Calcula quantos dias restam no período de carência.
     * @return Número de dias restantes ou 0 se já expirou
     */
    fun getGracePeriodDaysRemaining(): Int {
        val expirationDate = getSubscriptionExpirationDateFromPrefs()
        if (expirationDate == 0L) return 0

        val currentTime = System.currentTimeMillis()
        val gracePeriodEnd = expirationDate + GRACE_PERIOD_MILLIS

        return if (currentTime < gracePeriodEnd) {
            ((gracePeriodEnd - currentTime) / (24 * 60 * 60 * 1000)).toInt() + 1
        } else {
            0
        }
    }

    /**
     * Reseta todos os dados de downgrade (usado quando usuário renova assinatura).
     */
    fun resetDowngradeData() {
        sharedPreferences.edit {
            putLong(KEY_SUBSCRIPTION_EXPIRATION_DATE, 0L)
            putBoolean(KEY_DOWNGRADE_COMPLETED, false)
            putLong(KEY_DOWNGRADE_REQUIRED_DATE, 0L)
        }
        _subscriptionExpirationDateFlow.value = 0L
        _downgradeCompletedFlow.value = false
        _downgradeRequiredDateFlow.value = 0L
    }

    /**
     * Simula a expiração da assinatura (para testes).
     * Define a data de expiração como agora.
     */
    fun simulateSubscriptionExpiration() {
        val currentTime = System.currentTimeMillis()
        setSubscriptionExpirationDate(currentTime)
        setPremium(false)
        setDowngradeRequiredDate(currentTime)
    }

    /**
     * Simula que o período de carência já passou (para testes).
     * Define a data de expiração como 6 dias atrás.
     */
    fun simulateGracePeriodExpired() {
        val sixDaysAgo = System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L)
        setSubscriptionExpirationDate(sixDaysAgo)
        setPremium(false)
        setDowngradeRequiredDate(sixDaysAgo)
        setDowngradeCompleted(false)
    }
}

/**
 * Enum que representa os possíveis estados da assinatura.
 */
sealed class SubscriptionStatus {
    /** Usuário tem assinatura Premium ativa */
    object PREMIUM_ACTIVE : SubscriptionStatus()

    /** Usuário está no plano Free (nunca foi premium ou já fez downgrade) */
    object FREE : SubscriptionStatus()

    /** Usuário está no período de carência após expiração do Premium */
    data class GRACE_PERIOD(val daysRemaining: Int) : SubscriptionStatus()

    /** Período de carência expirou, downgrade obrigatório */
    object DOWNGRADE_REQUIRED : SubscriptionStatus()
}
