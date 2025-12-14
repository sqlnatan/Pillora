package com.pillora.pillora.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.viewmodel.ReportsViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication

    val reportsViewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.provideFactory(
            application = application,
            userPreferences = application.userPreferences,
            currentUserId = Firebase.auth.currentUser?.uid
        )
    )

    val isPremium by reportsViewModel.isPremium.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Assinatura") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
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

            // Header
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Premium",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Escolha seu plano",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Comece grátis e evolua quando precisar",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Plano Gratuito
            PlanCard(
                title = "Gratuito",
                subtitle = "Perfeito para começar",
                price = "R$ 0",
                period = "/mês",
                features = listOf(
                    PlanFeature("Até 5 medicamentos e 2 consultas", Icons.Default.Check),
                    PlanFeature("Lembretes básicos", Icons.Default.Check),
                    PlanFeature("Suporte por email", Icons.Default.Check),
                    PlanFeature("Uso pessoal", Icons.Default.Check),
                    PlanFeature("Com anúncios", Icons.Default.Close, isNegative = true)
                ),
                buttonText = if (isPremium) "Plano Atual: Premium" else "Plano Atual",
                buttonEnabled = false,
                onButtonClick = {},
                isPremiumPlan = false,
                isCurrentPlan = !isPremium
            )

            // Plano Premium
            PlanCard(
                title = "Premium",
                subtitle = "Funcionalidades completas",
                price = "R$ 13,05",
                period = "/mês",
                features = listOf(
                    PlanFeature("Sem limites", Icons.Default.Check),
                    PlanFeature("Todas as funcionalidades", Icons.Default.Check),
                    PlanFeature("Suporte prioritário", Icons.Default.Check),
                    PlanFeature("Relatórios avançados", Icons.Default.Check),
                    PlanFeature("Backup na nuvem", Icons.Default.Check),
                    PlanFeature("Sem anúncios", Icons.Default.Check)
                ),
                buttonText = if (isPremium) "Plano Atual" else "Assinar Premium",
                buttonEnabled = !isPremium,
                onButtonClick = {
                    openPlayStoreSubscription(context)
                },
                isPremiumPlan = true,
                isCurrentPlan = isPremium
            )

            // Informações adicionais
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Informações importantes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "• A assinatura é gerenciada pela Google Play Store\n" +
                                "• Você pode cancelar a qualquer momento\n" +
                                "• O pagamento será cobrado na sua conta Google Play\n" +
                                "• A renovação é automática, mas pode ser desativada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Botão de gerenciar assinatura (se for premium)
            if (isPremium) {
                OutlinedButton(
                    onClick = { openPlayStoreManageSubscription(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerenciar Assinatura na Play Store")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
    isPremiumPlan: Boolean,
    isCurrentPlan: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPremiumPlan && !isCurrentPlan) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPremiumPlan) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlan)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header do plano
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCurrentPlan) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Ativo",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Preço
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = period,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            HorizontalDivider()

            // Features
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            tint = if (feature.isNegative)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = feature.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (feature.isNegative)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Botão
            Button(
                onClick = onButtonClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = buttonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremiumPlan)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = buttonText,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

data class PlanFeature(
    val text: String,
    val icon: ImageVector,
    val isNegative: Boolean = false
)

// Função para abrir a página de assinatura na Play Store
fun openPlayStoreSubscription(context: Context) {
    try {
        // Tenta abrir a página de assinatura do app na Play Store
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Se falhar, abre a página do app na Play Store
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Se ainda falhar, abre no navegador
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}

// Função para abrir gerenciamento de assinaturas na Play Store
fun openPlayStoreManageSubscription(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/account/subscriptions")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir as configurações de assinatura", Toast.LENGTH_SHORT).show()
    }
}
