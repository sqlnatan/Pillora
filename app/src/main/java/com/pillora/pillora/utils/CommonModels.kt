package com.pillora.pillora.utils

/**
 * Data class para representar um destinatário (usuário principal ou dependente)
 * em listas de seleção (dropdowns) nos formulários.
 *
 * @param id O ID único do destinatário (pode ser o ID do usuário ou do dependente).
 * @param name O nome a ser exibido para o destinatário.
 * @param isUser Boolean indicando se este destinatário é o usuário principal (true) ou um dependente (false).
 */
data class RecipientDisplay(
    val id: String,
    val name: String,
    val isUser: Boolean
)

