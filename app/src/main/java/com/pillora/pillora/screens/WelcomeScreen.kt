package com.pillora.pillora.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.pillora.pillora.R

@Composable
fun WelcomeScreen(
    onFinish: () -> Unit
) {

    // --- Modelo das páginas ---
    data class OnboardingPage(
        val title: String,
        val description: String,
        val icon: ImageVector
    )

    // --- Conteúdo do onboarding ---
    val pages = listOf(
        OnboardingPage(
            title = "Bem-vindo ao Pillora",
            description = "Gerencie medicamentos, vacinas, consultas e receitas de forma simples e segura.",
            icon = Icons.Outlined.Medication
        ),
        OnboardingPage(
            title = "Lembretes e controle",
            description = "Receba alertas no horário certo, controle o estoque e evite esquecimentos.",
            icon = Icons.Outlined.Event
        ),
        OnboardingPage(
            title = "Relatórios completos",
            description = "Acompanhe histórico médico, consumo de medicamentos e relatórios detalhados.",
            icon = Icons.Outlined.Assessment
        )
    )

    var currentPage by remember { mutableIntStateOf(0) }

    // 🔹 Controle de swipe
    var dragAmount by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 80f

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(currentPage) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragDelta ->
                            dragAmount += dragDelta
                        },
                        onDragEnd = {
                            if (abs(dragAmount) > swipeThreshold) {
                                if (dragAmount < 0 && currentPage < pages.lastIndex) {
                                    currentPage++
                                } else if (dragAmount > 0 && currentPage > 0) {
                                    currentPage--
                                }
                            }
                            dragAmount = 0f
                        },
                        onDragCancel = {
                            dragAmount = 0f
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(32.dp))

            // 🔥 CONTEÚDO ANIMADO (ícone + textos)
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(
                            animationSpec = tween(350),
                            initialOffsetX = { it }
                        ) + fadeIn() togetherWith
                                slideOutHorizontally(
                                    animationSpec = tween(350),
                                    targetOffsetX = { -it }
                                ) + fadeOut()
                    } else {
                        slideInHorizontally(
                            animationSpec = tween(350),
                            initialOffsetX = { -it }
                        ) + fadeIn() togetherWith
                                slideOutHorizontally(
                                    animationSpec = tween(350),
                                    targetOffsetX = { it }
                                ) + fadeOut()
                    }
                },
                label = "OnboardingContent"
            ) { page ->

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (page == 0) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Logo do Pillora",
                            modifier = Modifier.size(160.dp)
                        )
                    } else {
                        Icon(
                            imageVector = pages[page].icon,
                            contentDescription = null,
                            modifier = Modifier.size(160.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }


                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = pages[page].title,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = pages[page].description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- Indicador de páginas ---
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (index == currentPage) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Botão ---
            Button(
                onClick = {
                    if (currentPage < pages.lastIndex) {
                        currentPage++
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                AnimatedContent(
                    targetState = currentPage == pages.lastIndex,
                    label = "ButtonText"
                ) { isLast ->
                    Text(text = if (isLast) "Começar" else "Próximo")
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
