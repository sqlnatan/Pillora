package com.pillora.pillora.ads

import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.pillora.pillora.R

private const val TAG = "NativeAdView"

/**
 * Composable que exibe um anúncio nativo avançado do AdMob.
 *
 * Características:
 * - Carrega um novo anúncio cada vez que é exibido (não reutiliza)
 * - Não faz reload agressivo em caso de falha
 * - Identificação clara como "Anúncio"
 * - Limpa recursos quando o composable é descartado
 *
 * @param modifier Modificador para customização do layout
 * @param adUnitId ID da unidade de anúncio (usa teste por padrão)
 */
@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = AdConstants.NATIVE_AD_UNIT_ID
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Carregar o anúncio quando o composable é criado
    DisposableEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                // Anúncio carregado com sucesso
                nativeAd?.destroy() // Limpar anúncio anterior se existir
                nativeAd = ad
                isLoading = false
                hasError = false
                Log.d(TAG, "Native ad loaded successfully")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Native ad failed to load: ${error.message}")
                    isLoading = false
                    hasError = true
                    // NÃO tenta recarregar automaticamente - seguindo as diretrizes
                }

                override fun onAdClicked() {
                    Log.d(TAG, "Native ad clicked")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "Native ad impression recorded")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        // Carregar o anúncio
        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            // Limpar recursos quando o composable é removido
            nativeAd?.destroy()
            nativeAd = null
            Log.d(TAG, "Native ad disposed")
        }
    }

    // Se houver erro, não mostrar nada (sem espaço em branco)
    if (hasError) {
        return
    }

    // Se ainda está carregando, não mostrar nada
    if (isLoading) {
        return
    }

    // Exibir o anúncio quando carregado
    nativeAd?.let { ad ->
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Label "Anúncio" - Identificação clara conforme diretrizes
                Text(
                    text = "Anúncio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Conteúdo do anúncio nativo
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        val inflater = LayoutInflater.from(ctx)
                        val adView = inflater.inflate(R.layout.native_ad_layout, null) as NativeAdView
                        populateNativeAdView(ad, adView)
                        adView
                    },
                    update = { adView ->
                        populateNativeAdView(ad, adView)
                    }
                )
            }
        }
    }
}

/**
 * Popula a NativeAdView com os dados do anúncio
 */
private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    // Headline (obrigatório)
    adView.headlineView = adView.findViewById(R.id.ad_headline)
    (adView.headlineView as? TextView)?.text = nativeAd.headline

    // Body
    adView.bodyView = adView.findViewById(R.id.ad_body)
    if (nativeAd.body != null) {
        (adView.bodyView as? TextView)?.text = nativeAd.body
        adView.bodyView?.visibility = android.view.View.VISIBLE
    } else {
        adView.bodyView?.visibility = android.view.View.GONE
    }

    // Call to Action
    adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
    if (nativeAd.callToAction != null) {
        (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        adView.callToActionView?.visibility = android.view.View.VISIBLE
    } else {
        adView.callToActionView?.visibility = android.view.View.GONE
    }

    // Icon
    adView.iconView = adView.findViewById(R.id.ad_icon)
    if (nativeAd.icon != null) {
        (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
        adView.iconView?.visibility = android.view.View.VISIBLE
    } else {
        adView.iconView?.visibility = android.view.View.GONE
    }

    // Star Rating
    adView.starRatingView = adView.findViewById(R.id.ad_stars)
    if (nativeAd.starRating != null) {
        (adView.starRatingView as? RatingBar)?.rating = nativeAd.starRating?.toFloat() ?: 0f
        adView.starRatingView?.visibility = android.view.View.VISIBLE
    } else {
        adView.starRatingView?.visibility = android.view.View.GONE
    }

    // Advertiser
    adView.advertiserView = adView.findViewById(R.id.ad_advertiser)
    if (nativeAd.advertiser != null) {
        (adView.advertiserView as? TextView)?.text = nativeAd.advertiser
        adView.advertiserView?.visibility = android.view.View.VISIBLE
    } else {
        adView.advertiserView?.visibility = android.view.View.GONE
    }

    // Registrar o anúncio na view
    adView.setNativeAd(nativeAd)
}
