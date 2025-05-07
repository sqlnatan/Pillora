package com.pillora.pillora.model

data class Dependent(
    val id: String = "",
    val name: String = "",
    val userId: String = "" // Para vincular o dependente ao usuário principal
)
