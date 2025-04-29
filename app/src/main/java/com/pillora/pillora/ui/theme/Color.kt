package com.pillora.pillora.ui.theme

import androidx.compose.ui.graphics.Color

// Cores da SplashScreen
val SplashBackgroundLight = Color(0xFFF5F5F5) // #F5F5F5 do colors.xml
val SplashBackgroundDark = Color(0xFF303030) // #303030 do colors.xml

// Cores derivadas para tema claro
val PrimaryLight = SplashBackgroundLight
val SecondaryLight = Color(0xFFE0E0E0) // Versão um pouco mais escura do SplashBackgroundLight
val TertiaryLight = Color(0xFFB0B0B0) // Ainda mais escura para contraste

// Cores derivadas para tema escuro
val PrimaryDark = SplashBackgroundDark
val SecondaryDark = Color(0xFF404040) // Versão um pouco mais clara do SplashBackgroundDark
val TertiaryDark = Color(0xFF505050) // Ainda mais clara para contraste

