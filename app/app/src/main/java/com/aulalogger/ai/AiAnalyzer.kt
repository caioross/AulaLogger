package com.aulalogger.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * Faz a chamada de análise para o provider selecionado.
 *
 * - Tudo passa direto device → provider, sem servidor intermediário.
 * - Cancelamento real (BUG-017): mantém referência da `HttpURLConnection` ativa
 *   em [inFlight]; `cancelInFlight()` chama `disconnect()` que interrompe IO.
 * - Gemini safety (BUG-015): trata `finishReason="SAFETY"` com mensagem clara.
 * - Claude max_tokens (BUG-016): default 8192 (Sonnet aceita).
 */
object AiAnalyzer {

    private const val TAG = "AiAnalyzer"
    private const val CLAUDE_MAX_TOKENS = 8192

    /** Conexão atualmente em flight; permite cancelamento externo. */
    private val inFlight = AtomicReference<HttpURLConnection?>(null)

    /** Resultado da análise. */
    sealed class Result {
        data class Success(val text: String, val provider: AiProvider, val model: String) : Result()
        data class Error(val message: String) : Result()
    }

    private val SYSTEM_PROMPT = """
Você é um assistente que ajuda professores a refletir sobre suas aulas.
Receba a transcrição abaixo e produza uma análise pedagógica em português brasileiro
com as seguintes seções, claras e diretas (sem floreios):

1. **Tema da aula** — em uma frase
2. **Tópicos abordados** — bullets em ordem
3. **Resumo** — 5 a 10 linhas explicando o que foi ensinado
4. **Pontos fortes** — o que ficou claro / bem explicado
5. **Pontos a melhorar** — onde poderia ser mais didático ou completo
6. **Conceitos-chave** — termos técnicos importantes mencionados
7. **Sugestões para próxima aula** — encadeamento natural

Não invente conteúdo que não esteja na transcrição.
""".trimIndent()

    /** Cancela qualquer chamada HTTP em andamento. Idempotente. */
    fun cancelInFlight() {
        inFlight.getAndSet(null)?.let {
            try { it.disconnect() } catch (_: Throwable) {}
        }
    }

    /**
     * Teste de conectividade — chamada minimal sem o system prompt pedagógico.
     */
    suspend fun testConnection(context: Context, provider: AiProvider): Result =
        withContext(Dispatchers.IO) {
            val apiKey = AiKeyStore.getKey(context, provider)
            if (apiKey.isBlank()) return@withContext Result.Error("API key vazia")
            val model = AiKeyStore.getModel(context, provider)
            return@withContext try {
                val text = when (provider) {
                    AiProvider.GEMINI -> callGeminiRaw(apiKey, model, "Responda só: OK", "Responda só: OK")
                    AiProvider.OPENAI -> callOpenAIRaw(
                        "https://api.openai.com/v1/chat/completions",
                        apiKey, model, "Responda só: OK", "Responda só: OK"
                    )
                    AiProvider.CLAUDE -> callClaudeRaw(apiKey, model, "Responda só: OK", "Responda só: OK")
                    AiProvider.OPENROUTER -> callOpenAIRaw(
                        "https://openrouter.ai/api/v1/chat/completions",
                        apiKey, model, "Responda só: OK", "Responda só: OK"
                    )
                }
                Result.Success(text.trim(), provider, model)
            } catch (t: Throwable) {
                Log.e(TAG, "Falha no test ${provider.id}", t)
                Result.Error(t.message ?: "Erro desconhecido")
            }
        }

    suspend fun analyze(
        context: Context,
        provider: AiProvider,
        transcript: String
    ): Result = withContext(Dispatchers.IO) {
        val apiKey = AiKeyStore.getKey(context, provider)
        if (apiKey.isBlank()) return@withContext Result.Error("API key não configurada para ${provider.displayName}")
        val model = AiKeyStore.getModel(context, provider)
        if (transcript.isBlank()) return@withContext Result.Error("Transcrição está vazia")

        return@withContext try {
            val text = when (provider) {
                AiProvider.GEMINI -> callGemini(apiKey, model, transcript)
                AiProvider.OPENAI -> callOpenAI("https://api.openai.com/v1/chat/completions", apiKey, model, transcript)
                AiProvider.CLAUDE -> callClaude(apiKey, model, transcript)
                AiProvider.OPENROUTER -> callOpenAI("https://openrouter.ai/api/v1/chat/completions", apiKey, model, transcript)
            }
            Result.Success(text.trim(), provider, model)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao analisar via ${provider.id}", t)
            Result.Error(t.message ?: "Erro desconhecido")
        }
    }

    private fun callGemini(apiKey: String, model: String, transcript: String): String =
        callGeminiRaw(apiKey, model, SYSTEM_PROMPT, "TRANSCRIÇÃO:\n$transcript")

    private fun callOpenAI(url: String, apiKey: String, model: String, transcript: String): String =
        callOpenAIRaw(url, apiKey, model, SYSTEM_PROMPT, "TRANSCRIÇÃO:\n$transcript")

    private fun callClaude(apiKey: String, model: String, transcript: String): String =
        callClaudeRaw(apiKey, model, SYSTEM_PROMPT, "TRANSCRIÇÃO:\n$transcript")

    private fun callGeminiRaw(apiKey: String, model: String, system: String, user: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "$system\n\n$user"))
                    })
                })
            })
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }
        val resp = postJson(url, body.toString(), headers = emptyMap())
        val obj = JSONObject(resp)
        val candidates = obj.optJSONArray("candidates")
            ?: error("Gemini não retornou candidates: ${resp.take(300)}")
        if (candidates.length() == 0) error("Gemini retornou 0 candidates")
        val candidate = candidates.getJSONObject(0)
        // BUG-015: trata bloqueio por safety/recitation
        val finishReason = candidate.optString("finishReason", "")
        if (finishReason == "SAFETY" || finishReason == "RECITATION" || finishReason == "BLOCKLIST") {
            error("Gemini bloqueou a resposta ($finishReason). Tente outro provedor ou reformule.")
        }
        val content = candidate.optJSONObject("content")
            ?: error("Gemini sem content (finishReason=$finishReason)")
        val parts = content.optJSONArray("parts")
            ?: error("Gemini sem parts")
        if (parts.length() == 0) error("Gemini retornou parts vazias")
        return parts.getJSONObject(0).optString("text", "").ifBlank {
            error("Gemini retornou text vazio")
        }
    }

    private fun callOpenAIRaw(url: String, apiKey: String, model: String, system: String, user: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
            put("temperature", 0.3)
        }
        val resp = postJson(
            url, body.toString(),
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "HTTP-Referer" to "https://aulalogger.com.br",
                "X-Title" to "AulaLogger"
            )
        )
        val obj = JSONObject(resp)
        val choices = obj.optJSONArray("choices")
            ?: error("Sem 'choices' na resposta: ${resp.take(300)}")
        if (choices.length() == 0) error("0 choices retornados")
        return choices.getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun callClaudeRaw(apiKey: String, model: String, system: String, user: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", CLAUDE_MAX_TOKENS)  // BUG-016: 8192 acomoda análises longas
            put("system", system)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "user").put("content", user))
            })
        }
        val resp = postJson(
            "https://api.anthropic.com/v1/messages", body.toString(),
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01"
            )
        )
        val obj = JSONObject(resp)
        val content = obj.optJSONArray("content")
            ?: error("Claude sem 'content': ${resp.take(300)}")
        if (content.length() == 0) error("Claude retornou content vazio")
        return content.getJSONObject(0).getString("text")
    }

    private fun postJson(url: String, body: String, headers: Map<String, String>): String {
        // Cancela qualquer chamada anterior (não deveria acontecer, mas defensivo)
        cancelInFlight()

        val conn = URL(url).openConnection() as HttpURLConnection
        inFlight.set(conn)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 180_000  // 3 min: análises longas com Claude
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) conn.setRequestProperty(k, v)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(body); it.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code: ${text.take(500)}")
            return text
        } finally {
            // Limpa ref apenas se ainda é a mesma conn (não substituída por cancel)
            inFlight.compareAndSet(conn, null)
        }
    }
}
