package com.pillora.pillora.ui.theme

import androidx.compose.ui.graphics.Color

// --- Cores Base ---
// Cores da SplashScreen (usadas como fundo principal)
val SplashBackgroundLight = Color(0xFFF5F5F5) // Cinza claro
val SplashBackgroundDark = Color(0xFF303030) // Cinza escuro

// Azul inspirado no Windows 11
val WinBlueLight = Color(0xFF005FB8) // Azul um pouco mais forte para tema claro
val WinBlueDark = Color(0xFF0078D4) // Azul padrão Win11 para tema escuro

// --- Tema Claro ---
val PrimaryLight = WinBlueLight // Azul forte como cor primária
val OnPrimaryLight = Color.White // Texto branco sobre azul
val SecondaryLight = Color(0xFF003A70) // Azul mais escuro para variações
val OnSecondaryLight = Color.White
val TertiaryLight = Color(0xFF707070) // Cinza médio para elementos menos importantes
val OnTertiaryLight = Color.White
val BackgroundLight = SplashBackgroundLight // Fundo principal cinza claro
val OnBackgroundLight = Color.Black // Texto preto sobre fundo claro
val SurfaceLight = Color.White // Superfície branca para Cards, TextFields
val OnSurfaceLight = Color.Black // Texto preto sobre superfície branca
val SurfaceVariantLight = Color(0xFFE0E0E0) // Variante de superfície (ex: fundo de TextField desabilitado)
val OnSurfaceVariantLight = Color(0xFF404040) // Texto sobre variante de superfície
val OutlineLight = Color(0xFFB0B0B0) // Cor da borda para TextFields, etc.

// --- Tema Escuro ---
val PrimaryDark = WinBlueDark // Azul Win11 como cor primária
val OnPrimaryDark = Color.White // Texto branco sobre azul
val SecondaryDark = Color(0xFF309FF0) // Azul mais claro para variações
val OnSecondaryDark = Color.Black
val TertiaryDark = Color(0xFFB0B0B0) // Cinza claro para elementos menos importantes
val OnTertiaryDark = Color.Black
val BackgroundDark = SplashBackgroundDark // Fundo principal cinza escuro
val OnBackgroundDark = Color.White // Texto branco sobre fundo escuro
val SurfaceDark = Color(0xFF424242) // Superfície cinza um pouco mais clara que o fundo
val OnSurfaceDark = Color.White // Texto branco sobre superfície escura
val SurfaceVariantDark = Color(0xFF505050) // Variante de superfície
val OnSurfaceVariantDark = Color(0xFFB0B0B0) // Texto sobre variante de superfície
val OutlineDark = Color(0xFF707070) // Cor da borda para TextFields, etc.

