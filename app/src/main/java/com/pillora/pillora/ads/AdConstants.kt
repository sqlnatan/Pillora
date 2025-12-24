package com.pillora.pillora.ads

/**
 * Constantes para anúncios do AdMob.
 *
 * IMPORTANTE: Usar IDs de TESTE durante desenvolvimento e testes.
 * Os IDs de teste oficiais do Google são seguros e não violam políticas.
 *
 * Quando for publicar na Play Store com anúncios reais:
 * 1. Substitua os IDs de teste pelos IDs de produção do seu painel AdMob
 * 2. Configure os dispositivos de teste no AdMob Console
 */
object AdConstants {

    // ============================================
    // IDs DE TESTE OFICIAIS DO GOOGLE ADMOB
    // Estes são os IDs oficiais para testes - SEGUROS para usar
    // https://developers.google.com/admob/android/test-ads
    // ============================================

    /**
     * ID de teste oficial do Google para Native Advanced Ads
     * Este ID sempre retorna anúncios de teste válidos
     */
    const val NATIVE_AD_UNIT_ID_TEST = "ca-app-pub-3940256099942544/2247696110"

    // ============================================
    // IDs DE PRODUÇÃO (SUBSTITUIR QUANDO FOR PUBLICAR)
    // ============================================

    /**
     * ID de produção para Native Advanced Ads
     * TODO: Substituir pelo ID real do seu painel AdMob antes de publicar
     */
    const val NATIVE_AD_UNIT_ID_PRODUCTION = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

    // ============================================
    // CONFIGURAÇÃO ATUAL
    // ============================================

    /**
     * Define se está em modo de teste ou produção.
     * Mude para false apenas quando for publicar com anúncios reais.
     */
    const val IS_TEST_MODE = true

    /**
     * Retorna o ID correto baseado no modo atual
     */
    val NATIVE_AD_UNIT_ID: String
        get() = if (IS_TEST_MODE) NATIVE_AD_UNIT_ID_TEST else NATIVE_AD_UNIT_ID_PRODUCTION
}
