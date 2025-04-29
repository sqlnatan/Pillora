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

// As cores agora são definidas em Color.kt (ou Color_contrast.kt)
// Certifique-se de que os nomes das variáveis abaixo correspondem aos definidos lá.

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, // Azul Win11
    onPrimary = OnPrimaryDark, // Branco
    secondary = SecondaryDark, // Azul claro
    onSecondary = OnSecondaryDark, // Preto
    tertiary = TertiaryDark, // Cinza claro
    onTertiary = OnTertiaryDark, // Preto
    background = BackgroundDark, // Cinza escuro (Splash)
    onBackground = OnBackgroundDark, // Branco
    surface = SurfaceDark, // Cinza um pouco mais claro
    onSurface = OnSurfaceDark, // Branco
    surfaceVariant = SurfaceVariantDark, // Variante de superfície
    onSurfaceVariant = OnSurfaceVariantDark, // Texto sobre variante
    outline = OutlineDark // Cor da borda
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, // Azul forte
    onPrimary = OnPrimaryLight, // Branco
    secondary = SecondaryLight, // Azul escuro
    onSecondary = OnSecondaryLight, // Branco
    tertiary = TertiaryLight, // Cinza médio
    onTertiary = OnTertiaryLight, // Branco
    background = BackgroundLight, // Cinza claro (Splash)
    onBackground = OnBackgroundLight, // Preto
    surface = SurfaceLight, // Branco
    onSurface = OnSurfaceLight, // Preto
    surfaceVariant = SurfaceVariantLight, // Variante de superfície
    onSurfaceVariant = OnSurfaceVariantLight, // Texto sobre variante
    outline = OutlineLight // Cor da borda
)

@Composable
fun PilloraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Mantido desativado para usar nossas cores personalizadas
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

            // Define se os ícones da barra de status devem ser claros ou escuros
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme // Ícones escuros no tema claro, claros no tema escuro
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Certifique-se de que Typography está definido em Type.kt
        content = content
    )
}
