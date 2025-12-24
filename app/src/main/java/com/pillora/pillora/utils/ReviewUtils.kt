package com.pillora.pillora.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "ReviewUtils"

/**
 * Inicia o fluxo de avaliação In-App Review da Google Play Store.
 *
 * @param context O contexto da aplicação, que deve ser uma Activity para o ReviewManager.
 * @param scope O CoroutineScope para lançar a operação assíncrona.
 */
fun startInAppReview(context: Context, scope: CoroutineScope) {
    val activity = context as? Activity
    if (activity == null) {
        Log.e(TAG, "O contexto fornecido não é uma Activity. Não é possível iniciar o In-App Review.")
        return
    }

    val manager = ReviewManagerFactory.create(activity)

    scope.launch {
        try {
            // 1. Solicitar o objeto ReviewInfo
            val reviewInfo = manager.requestReviewFlow().await()

            // 2. Iniciar o fluxo de avaliação
            manager.launchReview(activity, reviewInfo)

            Log.d(TAG, "Fluxo de avaliação iniciado com sucesso.")
        } catch (e: Exception) {
            // Ocorreu um erro ao solicitar ou iniciar o fluxo.
            // Isso pode acontecer se o dispositivo não tiver o Google Play instalado,
            // se o usuário já tiver avaliado recentemente, ou por outros motivos.
            // O erro é silencioso para o usuário, mas é bom logar.
            Log.e(TAG, "Erro ao iniciar o fluxo de avaliação In-App: ${e.message}", e)
        }
    }
}
