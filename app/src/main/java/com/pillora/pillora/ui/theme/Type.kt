package com.pillora.pillora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import com.pillora.pillora.R

// Set of Material typography styles to start with
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

val InterDisplayFontFamily = FontFamily(
    Font(R.font.inter_large, FontWeight.Normal)
)

val Typography = Typography(
   bodyLarge = TextStyle(
       fontFamily = InterFontFamily,
       fontWeight = FontWeight.Normal,
       fontSize = 16.sp,
       lineHeight = 24.sp,
    ),
     headlineMedium = Typography().headlineMedium.copy(
        fontFamily = InterFontFamily
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily, // 👈 igual
        fontWeight = FontWeight.Medium,    // 👈 igual
        fontSize = 22.sp,                    // 👈 menor
        lineHeight = 26.sp
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = InterDisplayFontFamily
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = InterFontFamily
    )
)