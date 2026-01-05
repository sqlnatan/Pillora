# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --- Google Play Billing ---
# Essencial para que as compras e assinaturas funcionem na versão final
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }

# --- Firebase & Google Play Services ---
# Garante que o SDK do Firebase não seja removido ou renomeado
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Seus Modelos (CRUCIAL) ---
# Impede que o ProGuard renomeie suas classes de dados (Medicamentos, Consultas, etc)
# Isso evita erros ao ler dados do Firestore, Room ou Gson
-keepattributes Signature, *Annotation*
-keep class com.pillora.pillora.model.** { *; }

# --- Gson ---
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-dontwarn androidx.room.**

# --- Kotlin Coroutines (Versão Corrigida) ---
# Removemos a linha que causava o erro 'Unresolved class name'
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.** { *; }

# --- Preservar Linhas para Debug ---
# Ajuda a identificar a linha exata de um erro em logs de produção
-keepattributes SourceFile, LineNumberTable

# --- WebView (Opcional) ---
# Se você usar WebView para Termos de Uso ou Política de Privacidade
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
