package com.pillora.pillora.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: ImageVector,
    val contentDescription: String
)

val bottomNavItems = listOf(
    BottomNavItem(
        name = "Início",
        route = Screen.Home.route,
        icon = Icons.Default.Home,
        contentDescription = "Tela inicial"
    ),
    BottomNavItem(
        name = "Medicação",
        route = Screen.MedicineList.route,
        icon = Icons.Default.Medication,
        contentDescription = "Lista de medicamentos"
    ),
    BottomNavItem(
        name = "Consultas",
        route = Screen.ConsultationList.route,
        icon = Icons.Default.CalendarToday,
        contentDescription = "Lista de consultas"
    ),
    BottomNavItem(
        name = "Vacinas",
        route = Screen.VaccineList.route,
        icon = Icons.Default.LocalHospital,
        contentDescription = "Lista de vacinas"
    ),
    BottomNavItem(
        name = "Receitas",
        route = RECIPE_LIST_ROUTE,
        icon = Icons.Default.MenuBook,
        contentDescription = "Lista de receitas"
    )
)
