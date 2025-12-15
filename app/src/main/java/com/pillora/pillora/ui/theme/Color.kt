package com.pillora.pillora.ui.theme

import androidx.compose.ui.graphics.Color

// ===============================
// 🎨 PALETA BASE (Azul pálido)
// ===============================

// Azul principal — pálido, calmo, elegante
val SoftBlue = Color(0xFF8FB6C9)

// Azul um pouco mais forte para ações
val SoftBlueStrong = Color(0xFF6FA3BE)

// Azul escuro para tema dark (fundo e barras)
val DeepBlueDark = Color(0xFF121B22)

// Azul acinzentado para superfícies
val BlueGraySurface = Color(0xFFEAF1F4)

// Variante para tema escuro
val BlueGraySurfaceDark = Color(0xFF1E2A33)

// Cinzas equilibrados
val TextDark = Color(0xFF1C1C1C)
val TextLight = Color(0xFFF2F2F2)

// ===============================
// ☀️ TEMA CLARO
// ===============================

val PrimaryLight = SoftBlue
val OnPrimaryLight = Color.White

val SecondaryLight = SoftBlueStrong
val OnSecondaryLight = Color.White

val TertiaryLight = Color(0xFFB4C9D3)
val OnTertiaryLight = TextDark

val BackgroundLight = Color(0xFFF4F8FA) // Azul quase branco
val OnBackgroundLight = TextDark

val SurfaceLight = BlueGraySurface
val OnSurfaceLight = TextDark

val SurfaceVariantLight = Color(0xFFDCE8EE)
val OnSurfaceVariantLight = Color(0xFF425A66)

val OutlineLight = Color(0xFFB5C7D1)

// ===============================
// 🌙 TEMA ESCURO
// ===============================

val PrimaryDark = SoftBlue
val OnPrimaryDark = Color.Black

val SecondaryDark = Color(0xFF9BC7DD)
val OnSecondaryDark = Color.Black

val TertiaryDark = Color(0xFF6E8FA0)
val OnTertiaryDark = Color.White

val BackgroundDark = DeepBlueDark
val OnBackgroundDark = TextLight

val SurfaceDark = BlueGraySurfaceDark
val OnSurfaceDark = TextLight

val SurfaceVariantDark = Color(0xFF263540)
val OnSurfaceVariantDark = Color(0xFFBFD6E2)

val OutlineDark = Color(0xFF3E5663)
