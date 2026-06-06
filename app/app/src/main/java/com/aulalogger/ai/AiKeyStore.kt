package com.aulalogger.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiProvider(val id: String, val displayName: String, val placeholderModel: String) {
    GEMINI("gemini", "Google Gemini", "gemini-2.0-flash-exp"),
    OPENAI("openai", "OpenAI", "gpt-4o-mini"),
    CLAUDE("claude", "Anthropic Claude", "claude-3-5-sonnet-20241022"),
    OPENROUTER("openrouter", "OpenRouter", "openai/gpt-4o-mini");

    companion object {
        fun fromId(id: String?): AiProvider? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Armazena chaves de API criptografadas (AES-256-GCM via Android Keystore).
 * Em caso de falha (chave do Keystore corrompida em alguns OEMs), faz fallback
 * para SharedPreferences plain — não ideal mas funcional.
 *
 * O `prefs` é cacheado: criar EncryptedSharedPreferences custa ~5-15ms por
 * chamada (toca o Keystore). Sem cache, abrir a tela de API keys disparava 8+
 * inicializações.
 */
object AiKeyStore {

    private const val FILE = "aulalogger_ai_keys"
    private const val FILE_FALLBACK = "aulalogger_ai_keys.fallback"
    private const val KEY_PREFIX = "key_"
    private const val MODEL_PREFIX = "model_"
    private const val SELECTED_PROVIDER = "selected_provider"
    private const val TAG = "AiKeyStore"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val sp = try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                Log.e(TAG, "EncryptedSharedPreferences falhou, usando fallback", t)
                context.applicationContext.getSharedPreferences(FILE_FALLBACK, Context.MODE_PRIVATE)
            }
            cachedPrefs = sp
            return sp
        }
    }

    fun setKey(context: Context, provider: AiProvider, key: String) {
        prefs(context).edit().putString("$KEY_PREFIX${provider.id}", key.trim()).apply()
    }

    fun getKey(context: Context, provider: AiProvider): String =
        prefs(context).getString("$KEY_PREFIX${provider.id}", null) ?: ""

    fun hasKey(context: Context, provider: AiProvider): Boolean = getKey(context, provider).isNotBlank()

    fun setModel(context: Context, provider: AiProvider, model: String) {
        prefs(context).edit().putString("$MODEL_PREFIX${provider.id}", model.trim()).apply()
    }

    fun getModel(context: Context, provider: AiProvider): String =
        prefs(context).getString("$MODEL_PREFIX${provider.id}", null) ?: provider.placeholderModel

    fun setSelectedProvider(context: Context, provider: AiProvider) {
        prefs(context).edit().putString(SELECTED_PROVIDER, provider.id).apply()
    }

    fun getSelectedProvider(context: Context): AiProvider? =
        AiProvider.fromId(prefs(context).getString(SELECTED_PROVIDER, null))

    fun clearKey(context: Context, provider: AiProvider) {
        prefs(context).edit().remove("$KEY_PREFIX${provider.id}").apply()
    }
}
