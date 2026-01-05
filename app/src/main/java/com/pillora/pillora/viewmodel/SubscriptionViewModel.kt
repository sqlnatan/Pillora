package com.pillora.pillora.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.repository.BillingRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar assinaturas Premium.
 *
 * VERSÃO LIMPA: Sem funções de teste ou simulação.
 * Usa BillingRepository como fonte única da verdade.
 *
 * REMOVIDO (para aprovação do Google):
 * - simulateExpiration() - Função de teste
 * - simulateGracePeriodExpired() - Função de teste
 * - Sistema complexo de downgrade com período de carência
 * - resetDowngradeState() com setPremium manual
 */
class SubscriptionViewModel(
    application: Application,
    private val billingRepository: BillingRepository
) : AndroidViewModel(application) {

    // Observa o status premium do BillingRepository (fonte única da verdade)
    val isPremium: StateFlow<Boolean> = billingRepository.isPremium

    // Produtos disponíveis
    val monthlyProduct = billingRepository.monthlyProduct
    val yearlyProduct = billingRepository.yearlyProduct

    // Estado de carregamento
    val isLoading = billingRepository.isLoading

    // Estado de conexão
    val connectionState = billingRepository.connectionState

    /**
     * Atualiza o status de compras do usuário.
     * Chama o BillingRepository para verificar assinaturas ativas.
     */
    fun refreshPurchases() {
        viewModelScope.launch {
            billingRepository.refreshPurchases()
        }
    }

    /**
     * Reconecta ao serviço de billing se desconectado.
     */
    fun reconnect() {
        billingRepository.reconnect()
    }

    companion object {
        fun provideFactory(
            application: Application,
            billingRepository: BillingRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SubscriptionViewModel(application, billingRepository) as T
            }
        }
    }
}
