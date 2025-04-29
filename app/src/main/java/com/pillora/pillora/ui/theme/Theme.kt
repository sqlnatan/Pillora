package com.pillora.pillora.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Cores da SplashScreen (definidas em Color.kt)
// val SplashBackgroundLight = Color(0xFFF5F5F5)
// val SplashBackgroundDark = Color(0xFF303030)

// Cores derivadas para tema claro (definidas em Color.kt)
// val PrimaryLight = SplashBackgroundLight
// val SecondaryLight = Color(0xFFE0E0E0)
// val TertiaryLight = Color(0xFFB0B0B0)

// Cores derivadas para tema escuro (definidas em Color.kt)
// val PrimaryDark = SplashBackgroundDark
// val SecondaryDark = Color(0xFF404040)
// val TertiaryDark = Color(0xFF505050)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, // Usando as cores definidas em Color.kt
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
    background = PrimaryDark, // Cor de fundo principal para tema escuro
    surface = SecondaryDark, // Cor de superfície para Cards, etc. no tema escuro
    onPrimary = Color.White, // Texto sobre a cor primária
    onSecondary = Color.White, // Texto sobre a cor secundária
    onTertiary = Color.White, // Texto sobre a cor terciária
    onBackground = Color.White, // Texto sobre o fundo
    onSurface = Color.White // Texto sobre superfícies
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, // Usando as cores definidas em Color.kt
    secondary = SecondaryLight,
    tertiary = TertiaryLight,
    background = PrimaryLight, // Cor de fundo principal para tema claro
    surface = Color.White, // Cor de superfície para Cards, etc. no tema claro
    onPrimary = Color.Black, // Texto sobre a cor primária
    onSecondary = Color.Black, // Texto sobre a cor secundária
    onTertiary = Color.Black, // Texto sobre a cor terciária
    onBackground = Color.Black, // Texto sobre o fundo
    onSurface = Color.Black // Texto sobre superfícies
)

@Composable
fun PilloraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Desativado para usar nossas cores personalizadas
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Atualizar a cor da barra de status usando a abordagem moderna
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Permite desenhar sob a barra de status/navegação
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // REMOVIDO: Não usar o setter depreciado
            // window.statusBarColor = Color.Transparent.toArgb()

            // Define se os ícones da barra de status devem ser claros ou escuros
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme // Ícones escuros no tema claro, claros no tema escuro

            // Opcional: Definir cor da barra de navegação (se necessário)
            // controller.isAppearanceLightNavigationBars = !darkTheme // Ícones da barra de navegação
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Certifique-se de que Typography está definido em Type.kt
        content = content
    )
}
