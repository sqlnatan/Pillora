package com.pillora.pillora.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.pillora.pillora.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repository para gerenciar Google Play Billing (assinaturas).
 * Versão: billing-ktx 8.3.0
 *
 * FONTE ÚNICA DA VERDADE para status Premium.
 * Integrado com UserPreferences para cache local.
 *
 * IDs de Produtos (devem ser configurados no Google Play Console):
 * - "pillora_premium_monthly" - Assinatura mensal
 * - "pillora_premium_yearly" - Assinatura anual
 */
class BillingRepository(
    private val context: Context,
    private val userPreferences: UserPreferences
) {

    companion object {
        private const val TAG = "BillingRepository"

        // IDs dos produtos de assinatura (configurar no Google Play Console)
        const val PRODUCT_MONTHLY = "pillora_premium_monthly"
        const val PRODUCT_YEARLY = "pillora_premium_yearly"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var billingClient: BillingClient? = null

    // Rastrear estado anterior para detectar downgrade
    private var wasPremium = false

    // FONTE ÚNICA DA VERDADE: Apenas o BillingRepository controla este valor
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _monthlyProduct = MutableStateFlow<ProductDetails?>(null)
    val monthlyProduct: StateFlow<ProductDetails?> = _monthlyProduct.asStateFlow()

    private val _yearlyProduct = MutableStateFlow<ProductDetails?>(null)
    val yearlyProduct: StateFlow<ProductDetails?> = _yearlyProduct.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        setupBillingClient()
    }

    /**
     * Configura e conecta o BillingClient.
     * Versão 8.x usa enablePendingPurchases() com PendingPurchasesParams.
     */
    private fun setupBillingClient() {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .enablePrepaidPlans()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                // Listener para atualizações de compras em tempo real
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (purchases != null) {
                        processPurchaseList(purchases)
                    }
                } else {
                    Log.e(TAG, "Erro na compra: ${billingResult.debugMessage}")
                }
            }
            .enablePendingPurchases(pendingParams)
            .enableAutoServiceReconnection() // Reconexão automática
            .build()

        startConnection()
    }

    /**
     * Inicia conexão com o Google Play Billing.
     */
    private fun startConnection() {
        _connectionState.value = ConnectionState.CONNECTING

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing conectado com sucesso")
                    _connectionState.value = ConnectionState.CONNECTED

                    // Carregar produtos e verificar compras
                    scope.launch {
                        queryProducts()
                        queryPurchases()
                        _isLoading.value = false
                    }
                } else {
                    Log.e(TAG, "Erro ao conectar Billing: ${billingResult.debugMessage}")
                    _connectionState.value = ConnectionState.ERROR
                    _isLoading.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing desconectado")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    /**
     * Consulta os produtos de assinatura disponíveis.
     */
    private suspend fun queryProducts() {
        val client = billingClient ?: return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = client.queryProductDetails(params)

        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val productDetailsList = result.productDetailsList
            if (productDetailsList != null) {
                for (product in productDetailsList) {
                    when (product.productId) {
                        PRODUCT_MONTHLY -> _monthlyProduct.value = product
                        PRODUCT_YEARLY -> _yearlyProduct.value = product
                    }
                }
                Log.d(TAG, "Produtos carregados: ${productDetailsList.size}")
            }
        } else {
            Log.e(TAG, "Erro ao carregar produtos: ${result.billingResult.debugMessage}")
        }
    }

    /**
     * Verifica assinaturas ativas do usuário.
     */
    private suspend fun queryPurchases() {
        val client = billingClient ?: return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = client.queryPurchasesAsync(params)

        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            processPurchaseList(result.purchasesList)
        } else {
            Log.e(TAG, "Erro ao verificar compras: ${result.billingResult.debugMessage}")
        }
    }

    /**
     * Atualiza o status de compras (pode ser chamado externamente).
     */
    fun refreshPurchases() {
        scope.launch {
            queryPurchases()
        }
    }

    /**
     * Processa a lista de compras do usuário.
     * ATUALIZA O STATUS PREMIUM BASEADO NAS COMPRAS REAIS.
     */
    private fun processPurchaseList(purchases: List<Purchase>) {
        var hasActivePremium = false

        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Verificar se é um produto premium
                val products = purchase.products
                if (products.contains(PRODUCT_MONTHLY) || products.contains(PRODUCT_YEARLY)) {
                    hasActivePremium = true

                    // Acknowledge a compra se ainda não foi
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }

        // DETECTAR DOWNGRADE: Era premium e agora não é mais
        if (wasPremium && !hasActivePremium) {
            Log.d(TAG, "Downgrade detectado! Marcando necessidade de seleção.")
            userPreferences.setNeedsDowngradeSelection(true)
        }

        // Atualizar estado anterior
        wasPremium = hasActivePremium

        // Atualizar status premium (FONTE ÚNICA DA VERDADE)
        _isPremium.value = hasActivePremium

        // Sincronizar com UserPreferences (apenas para cache)
        syncPremiumStatusToPreferences(hasActivePremium)

        Log.d(TAG, "Status Premium atualizado: $hasActivePremium")
    }

    /**
     * Sincroniza o status premium com UserPreferences.
     * IMPORTANTE: Apenas para cache local, não para controle.
     */
    private fun syncPremiumStatusToPreferences(isPremium: Boolean) {
        // Atualizar cache no UserPreferences
        // Nota: UserPreferences não tem mais setPremium(), usa método interno
        userPreferences.syncPremiumFromBilling(isPremium)
    }

    /**
     * Confirma (acknowledge) uma compra.
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Compra confirmada com sucesso")
            } else {
                Log.e(TAG, "Erro ao confirmar compra: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Inicia o fluxo de compra de uma assinatura.
     *
     * @param activity Activity necessária para abrir o fluxo de pagamento
     * @param productDetails Detalhes do produto a ser comprado
     * @return BillingResult com o resultado da operação
     */
    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails): BillingResult? {
        val client = billingClient ?: return null

        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
        if (subscriptionOfferDetails.isNullOrEmpty()) {
            Log.e(TAG, "Subscription offer details não encontrado")
            return null
        }

        val offerToken = subscriptionOfferDetails[0].offerToken

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        return client.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Libera recursos do BillingClient.
     */
    fun endConnection() {
        billingClient?.endConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Reconecta ao serviço de billing.
     */
    fun reconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            startConnection()
        }
    }
}

/**
 * Estados de conexão do Billing.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
