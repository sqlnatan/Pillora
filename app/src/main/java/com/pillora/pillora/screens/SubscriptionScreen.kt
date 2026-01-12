package com.pillora.pillora.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val application = context.applicationContext as PilloraApplication
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: SubscriptionViewModel = viewModel(
        factory = SubscriptionViewModel.provideFactory(
            application = application,
            billingRepository = application.billingRepository
        )
    )

    val isPremium by viewModel.isPremium.collectAsState()
    val monthlyProduct by viewModel.monthlyProduct.collectAsState()
    val yearlyProduct by viewModel.yearlyProduct.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Assinatura") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.refreshPurchases()
                        scope.launch {
                            snackbarHostState.showSnackbar("Verificando assinaturas...")
                        }
                    }) {
                        Text("Restaurar")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Image(
                    painter = painterResource(id = com.pillora.pillora.R.drawable.app_logo),
                    contentDescription = "Pillora Logo",
                    modifier = Modifier.size(80.dp)
                )

                Text(
                    text = if (isPremium) "Você é Premium! ⭐" else "Escolha seu plano",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isPremium)
                        "Aproveite todas as funcionalidades sem limites."
                    else
                        "Tenha acesso ilimitado e livre de anúncios.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Plano Gratuito
                if (!isPremium) {
                    PlanCard(
                        title = "Gratuito",
                        subtitle = "Perfeito para começar",
                        price = "R$ 0",
                        period = "/mês",
                        features = listOf(
                            PlanFeature("Até 3 medicamentos e 1 consulta", Icons.Default.Check),
                            PlanFeature("Lembretes básicos", Icons.Default.Check),
                            PlanFeature("Com anúncios", Icons.Default.Close, isNegative = true)
                        ),
                        buttonText = "Plano Atual",
                        buttonEnabled = false,
                        onButtonClick = {},
                        isCurrentPlan = true
                    )
                }

                // Plano Mensal
                monthlyProduct?.let { product ->
                    val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
                    PlanCard(
                        title = "Mensal",
                        subtitle = "Flexibilidade total",
                        price = price,
                        period = "/mês",
                        features = listOf(
                            PlanFeature("Sem limites de itens", Icons.Default.Check),
                            PlanFeature("Sem anúncios", Icons.Default.Check),
                            PlanFeature("Backup na nuvem", Icons.Default.Check)
                        ),
                        buttonText = if (isPremium) "Plano Ativo" else "Assinar Mensal",
                        buttonEnabled = !isPremium,
                        onButtonClick = { activity?.let { application.billingRepository.launchBillingFlow(it, product) } }
                    )
                }

                // Plano Anual (COM DESTAQUE)
                yearlyProduct?.let { product ->
                    val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: ""
                    
                    // Cálculo dinâmico da economia
                    val monthlyPriceMicros = monthlyProduct?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros ?: 0L
                    val yearlyPriceMicros = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros ?: 0L
                    val currencyCode = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceCurrencyCode ?: ""

                    val savingsText = if (monthlyPriceMicros > 0 && yearlyPriceMicros > 0) {
                        val totalMonthlyCost = monthlyPriceMicros * 12
                        val savingsMicros = totalMonthlyCost - yearlyPriceMicros
                        if (savingsMicros > 0) {
                            val savingsAmount = savingsMicros / 1_000_000.0
                            val formattedSavings = try {
                                val format = NumberFormat.getCurrencyInstance()
                                format.currency = Currency.getInstance(currencyCode)
                                format.format(savingsAmount)
                            } catch (e: Exception) {
                                when (currencyCode) {
                                    "BRL" -> String.format(Locale.getDefault(), "R$ %.2f", savingsAmount)
                                    "USD" -> String.format(Locale.getDefault(), "$%.2f", savingsAmount)
                                    else -> String.format(Locale.getDefault(), "%.2f %s", savingsAmount, currencyCode)
                                }
                            }
                            "Economize $formattedSavings"
                        } else {
                            "Melhor custo-benefício"
                        }
                    } else {
                        "Economize 16%" // Fallback para o valor aproximado se não conseguir calcular
                    }

                    val percentageSavings = if (monthlyPriceMicros > 0 && yearlyPriceMicros > 0) {
                        val totalMonthlyCost = monthlyPriceMicros * 12.0
                        val percentage = ((totalMonthlyCost - yearlyPriceMicros) / totalMonthlyCost) * 100
                        String.format(Locale.getDefault(), "💰 Economize %d%% com o plano anual", percentage.toInt())
                    } else {
                        "💰 Economize 16% com o plano anual"
                    }

                    PlanCard(
                        title = "Anual",
                        subtitle = "Melhor custo-benefício",
                        price = price,
                        period = "/ano",
                        features = listOf(
                            PlanFeature("Tudo do plano mensal", Icons.Default.Check),
                            PlanFeature("⭐ Mais vantajoso", Icons.Default.Star, iconColor = Color(0xFFFFD700)),
                            PlanFeature(savingsText, Icons.Default.Check)
                        ),
                        description = percentageSavings,
                        buttonText = if (isPremium) "Plano Ativo" else "Assinar Anual",
                        buttonEnabled = !isPremium,
                        isHighlighted = true,
                        onButtonClick = { activity?.let { application.billingRepository.launchBillingFlow(it, product) } }
                    )
                }

                // Link de Restauração (UX amigável)
                if (!isPremium) {
                    Text(
                        text = "Já é assinante? Restaurar assinatura",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .clickable {
                                viewModel.refreshPurchases()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Verificando assinaturas...")
                                }
                            }
                    )
                }

                // Informações Legais
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Informações sobre a assinatura:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("• Cobrança recorrente, cancele quando quiser na Play Store.", style = MaterialTheme.typography.bodySmall)
                        Text("• O pagamento será debitado na sua conta do Google após a confirmação.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (isPremium) {
                    OutlinedButton(
                        onClick = { openPlayStoreManageSubscription(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gerenciar Assinatura")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PlanCard(
    title: String,
    subtitle: String,
    price: String,
    period: String,
    features: List<PlanFeature>,
    buttonText: String,
    buttonEnabled: Boolean,
    onButtonClick: () -> Unit,
    isCurrentPlan: Boolean = false,
    isHighlighted: Boolean = false,
    description: String? = null
) {
    val borderColor = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isHighlighted) 2.dp else 0.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isHighlighted) 12.dp else 0.dp)
                .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 8.dp else 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrentPlan) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isCurrentPlan) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Ativo", modifier = Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = price, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = period, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                }

                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

                features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            tint = feature.iconColor ?: if (feature.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = feature.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = buttonEnabled,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(buttonText, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isHighlighted) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "⭐ MAIS VANTAJOSO",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

data class PlanFeature(
    val text: String,
    val icon: ImageVector,
    val isNegative: Boolean = false,
    val iconColor: Color? = null
)

fun openPlayStoreManageSubscription(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://play.google.com/store/account/subscriptions".toUri()
        }
        context.startActivity(intent)
    } catch (ignored: Exception) {
        Toast.makeText(context, "Erro ao abrir Play Store", Toast.LENGTH_SHORT).show()
    }
}
