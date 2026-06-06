# 07 — IA de análise (on-device + cloud opcional)

> Como o app entende o conteúdo da aula, gera resumos, tira insights, dá alertas, e usa IA local por padrão com cloud opcional para análise profunda. **Esta é a "joia da coroa" do app — o que o diferencia de um simples gravador.**

---

## 7.1. Filosofia da IA no AulaLogger

Princípios que guiam toda a parte de IA:

1. **A IA é um assistente, não um juiz.** Não dá nota, não compara, não envergonha. Ela observa e oferece perspectivas.
2. **Flexível ao conteúdo.** Sem prompts hardcoded sobre matéria. Funciona para Excel, Python, yoga, história ou direito civil igualmente.
3. **Privacidade primeiro.** Análise local por default. Cloud é escolha consciente para análises mais profundas.
4. **Honesta sobre incerteza.** Quando o LLM "alucina" ou está incerto, ele diz isso.
5. **Útil de verdade.** Sem insights tipo "você falou bastante na aula" (óbvio). Insights acionáveis: "você passou 47min em VLOOKUP, mas só 6min em PROCH — proporcional à dificuldade que os alunos terão?"

---

## 7.2. Tipos de análise oferecidas

### 7.2.1. Pós-aula automáticas (default)

Geradas automaticamente após transcrição+diarização ficar pronta, em background:

| Análise | Descrição | Tamanho de output |
|---------|-----------|-------------------|
| **Resumo executivo** | Síntese da aula em 5–10 linhas | ~300 tokens |
| **Tópicos abordados** | Lista de temas com timestamps | ~500 tokens |
| **Glossário** | Termos técnicos mencionados, com definições curtas | ~800 tokens |
| **Perguntas dos alunos** | Lista das perguntas feitas + onde aparecem | ~400 tokens |
| **Conceitos explicados** | Estruturados como cards "Conceito → Explicação dada → Exemplo dado" | ~1000 tokens |
| **Métricas pedagógicas** | Tempo falando vs alunos, palavras/min, jargão usado, perguntas feitas pelo professor | dados estruturados |
| **Pontos de retomada** | "Onde você falou que ia voltar a algum tópico" — ganchos abertos | ~300 tokens |

### 7.2.2. Sob demanda (usuário pede)

Disponíveis em menu "Análises avançadas":

| Análise | Descrição |
|---------|-----------|
| **Plano de aula reverso** | Reconstrói o "plano de aula" a partir do que foi dado |
| **Material de estudo** | Gera material para os alunos a partir da aula |
| **Lista de exercícios** | Sugere exercícios baseados nos conceitos cobertos |
| **Comparação com aula anterior** | Como esta aula se relaciona com a anterior do mesmo curso |
| **FAQ** | Gera FAQ a partir das perguntas feitas pelos alunos |
| **Trechos para revisar** | Identifica momentos confusos, contradições, lapsos |

### 7.2.3. Em tempo real (durante a aula, opcional)

Configurável. Roda apenas se ativado, com modelo leve.

| Alerta | Trigger |
|--------|---------|
| **Silêncio prolongado** | > 30s sem fala detectada |
| **Ritmo acelerado** | Palavras/min > 200 por > 1 min (alunos podem estar perdidos) |
| **Aluno tentando falar** | Detecta voz de aluno mas você continua falando |
| **Tópico em loop** | Você voltou ao mesmo tópico 3+ vezes em 10 min |
| **Termo técnico sem explicação** | Usou jargão e não definiu |
| **Pergunta sem resposta** | Aluno perguntou e você seguiu sem responder |
| **Tempo do tópico excedido** | Se você definiu plano com tempos esperados |

**UX:** vibração discreta + pequeno toast no app (se aberto) ou nada (se em background, salvo em log).

---

## 7.3. Camadas técnicas

### 7.3.1. Camada A — Análise rule-based (sem LLM)

Métricas que **não precisam** de LLM, computadas a partir do transcript:

```kotlin
data class PedagogicalMetrics(
    val totalDurationSec: Long,
    val professorSpeakingSec: Long,
    val studentSpeakingSec: Long,
    val silenceSec: Long,
    val professorSpeakingPct: Float,
    val avgWordsPerMinute: Float,
    val professorQuestionsAsked: Int,        // contar interrogações nos turnos do professor
    val studentQuestionsAsked: Int,
    val uniqueWords: Int,
    val technicalTermsCount: Int,            // cruzar com vocabulário customizado
    val longestMonologueSec: Long,           // maior turno seguido do professor
    val longestSilenceSec: Long,
    val pauseEvents: List<PauseEvent>,       // pausas entre tópicos
    val speakerSwitches: Int,                // quantas vezes mudou speaker
)
```

Cálculo: percorrer transcript, agregar. Custo trivial.

### 7.3.2. Camada B — LLM local (Gemma 2 2B / Phi-3 mini)

LLM rodando on-device para tarefas que precisam compreensão de linguagem mas não precisam genialidade:

- Resumo executivo
- Lista de tópicos abordados
- Identificação de perguntas
- Glossário básico
- Pontos de retomada

**Tecnologia:** [llama.cpp](https://github.com/ggerganov/llama.cpp) via JNI, mesmo padrão do Whisper.cpp.

**Modelo recomendado:** Gemma 2 2B (Google, jul/2024). Suporta multilíngue bem, incluindo PT-BR. Q4_K_M = ~1.5GB.

**Alternativa para celulares mais fracos:** Phi-3 mini 3.8B (Microsoft) ou Llama 3.2 1B Q4 (~700MB).

**Performance esperada:**
- Pixel 7 (8GB RAM): ~15 tokens/s, prompt 4K + 800 tokens output = ~1 min
- Galaxy A54 (8GB RAM): ~8 tokens/s, mesma tarefa = ~2 min
- Celular 4GB RAM: usar Llama 3.2 1B, ~5 tokens/s, ~3 min

### 7.3.3. Camada C — LLM em nuvem (opcional, opt-in)

Para análises que se beneficiam de modelos grandes:

- Plano de aula reverso (estruturado)
- Material de estudo elaborado
- Comparação com aulas anteriores (contexto longo)
- FAQ rica
- Análise de "qualidade pedagógica" (observações qualitativas)

**Provedores suportados (recomendação):**
1. **Anthropic Claude (Sonnet ou Haiku):** primeiro suportado, melhor para textos longos.
2. **OpenAI (GPT-4o ou GPT-4o-mini):** alternativa popular.
3. **Google Gemini (Gemini Flash):** alternativa barata e com janela enorme.

**Decisão:** suportar **todos os 3** com escolha do usuário, começando por **Claude** (recomendação P3).

**Custo estimado por análise (aula 4h, ~50K tokens):**
- Claude Haiku: ~US$ 0.05
- Claude Sonnet: ~US$ 0.30
- GPT-4o-mini: ~US$ 0.05
- GPT-4o: ~US$ 0.50
- Gemini Flash: ~US$ 0.02

---

## 7.4. Integração llama.cpp para LLM local

Mesmo padrão do whisper.cpp. Estrutura:

```
modules/aulalogger-native/android/src/main/cpp/
├── llama-jni.cpp
├── whisper-jni.cpp
└── ...
```

**LlamaCppBridge.kt:**

```kotlin
class LlamaCppBridge(modelPath: String, contextSize: Int = 8192) : Closeable {
    init { System.loadLibrary("llamajni") }
    private val ctxPtr = nativeInit(modelPath, contextSize)
    
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.3f,
        onToken: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.Default) {
        nativeGenerate(ctxPtr, prompt, maxTokens, temperature) { token ->
            onToken?.invoke(token)
        }
    }
    
    override fun close() = nativeFree(ctxPtr)
    
    private external fun nativeInit(modelPath: String, contextSize: Int): Long
    private external fun nativeGenerate(ctxPtr: Long, prompt: String, maxTokens: Int, temperature: Float, callback: (String) -> Unit): String
    private external fun nativeFree(ctxPtr: Long)
}
```

---

## 7.5. Prompts e estrutura

Toda interação com LLM usa **prompts versionados** (em `assets/prompts/v1/`), output em **JSON estruturado** validado com Zod no JS.

### Exemplo: prompt de resumo executivo

`assets/prompts/v1/summary.md`:

````markdown
# Sistema

Você é um assistente que ajuda professores a refletirem sobre suas aulas.
Seu trabalho é criar um resumo conciso, neutro e útil da aula transcrita.

# Instruções

1. Leia a transcrição abaixo (com identificação de speakers).
2. Produza um resumo em **5 a 10 frases** que cubra:
   - O tema principal
   - Os tópicos abordados em ordem
   - Pontos altos da interação com alunos (sem nomes)
   - Conclusão ou próximos passos mencionados pelo professor
3. **Não dê opinião** sobre a qualidade da aula.
4. Use português brasileiro, tom neutro.
5. Responda APENAS com JSON válido no formato especificado.

# Formato de saída

```json
{
  "summary": "...",
  "main_topic": "...",
  "topics_covered": ["...", "..."],
  "next_steps_mentioned": "..." | null
}
```

# Transcrição

{{TRANSCRIPT}}
````

**Política para todos os prompts:**
- Versionados (`v1/`, `v2/`).
- Independentes de domínio (nada hardcoded sobre Excel/Python).
- Output sempre JSON estruturado validado com Zod.
- Idioma do output = idioma da transcrição.

---

## 7.6. Estratégia para transcrições longas (chunking + map-reduce)

LLM local tem context window limitado (Gemma 2: 8K tokens). Aula de 4h tem ~50K tokens.

### Map-reduce pattern

```
Aula 4h (~50K tokens)
       │
       ▼
Quebrar em "blocos pedagógicos" (15min cada, ~3K tokens):
  Bloco 1: 0:00–15:00
  Bloco 2: 15:00–30:00
  ...
  Bloco 16: 3:45–4:00
       │
       ▼ (MAP)
Para cada bloco, gerar resumo parcial via LLM local:
  Bloco 1 → resumo 1
  Bloco 2 → resumo 2
  ...
       │
       ▼ (REDUCE)
Concatenar todos os resumos parciais (~2K tokens total)
       │
       ▼
Gerar resumo final via LLM com todos os resumos parciais
```

**Vantagens:** cabe em context window, paraleliza-vel.
**Desvantagens:** pode perder coerência entre blocos. Mitigamos passando "contexto do bloco anterior" no prompt.

### Quebra inteligente

Em vez de quebrar a cada 15min fixos, quebrar em **mudanças de tópico** detectadas (silêncios longos, mudança brusca de vocabulário, marcadores manuais do usuário).

---

## 7.7. Detalhamento das análises pós-aula

### 7.7.1. Resumo executivo (LLM local)
Já mostrado na §7.5.

### 7.7.2. Tópicos abordados

Prompt pede ao LLM identificar "blocos temáticos" com timestamps:

```json
{
  "topics": [
    {
      "title": "Introdução ao conceito de list comprehension",
      "start_ms": 0,
      "end_ms": 945000,
      "subtopics": ["sintaxe básica", "comparação com for loop", "exemplo simples"]
    },
    {
      "title": "List comprehension com filtros",
      "start_ms": 945000,
      "end_ms": 1820000,
      "subtopics": ["if condicional", "if-else inline", "exercício prático"]
    }
  ]
}
```

UI: timeline visual mostrando blocos. Clicar leva ao trecho.

### 7.7.3. Glossário

Identifica termos técnicos. Para cada termo, oferece definição curta (LLM gera baseado no contexto da aula).

```json
{
  "glossary": [
    {
      "term": "list comprehension",
      "definition": "Sintaxe concisa do Python para criar listas a partir de outras iteráveis.",
      "first_mention_ms": 12000,
      "mentions_count": 47,
      "from_user_vocabulary": false
    }
  ]
}
```

### 7.7.4. Perguntas dos alunos

Filtro: turnos de speaker != "Professor" que terminam com `?` ou são gramaticalmente perguntas.

```json
{
  "student_questions": [
    {
      "asked_at_ms": 142000,
      "asker": "Aluno A",
      "question_text": "...",
      "professor_answer_segments": [
        { "start_ms": 145000, "end_ms": 198000, "text": "..." }
      ],
      "answered": true
    }
  ]
}
```

Identifica não-respondidas: pergunta sem turno de professor logo depois.

### 7.7.5. Conceitos explicados

Cards estruturados:

```json
{
  "concepts": [
    {
      "concept": "complexidade O(n)",
      "explanation_given": "É o número de operações que cresce linearmente com o tamanho da entrada.",
      "example_given": "Buscar um item em uma lista não ordenada: precisa olhar item por item, então é O(n).",
      "explained_at_ms": 1245000,
      "duration_explanation_sec": 87
    }
  ]
}
```

### 7.7.6. Métricas pedagógicas (rule-based, sem LLM)

Já descritas em §7.3.1. Output:

```json
{
  "metrics": {
    "total_duration_sec": 14400,
    "professor_speaking_pct": 71.3,
    "student_speaking_pct": 18.4,
    "silence_pct": 10.3,
    "avg_wpm": 142,
    "longest_monologue_sec": 437,
    "professor_questions": 23,
    "student_questions": 15,
    "speaker_switches": 89,
    "longest_silence_sec": 47
  }
}
```

UI: dashboard com gráficos.

### 7.7.7. Pontos de retomada

Detecta frases tipo "depois eu volto nisso", "deixa eu lembrar de mostrar X", "vou abrir um parêntese mas a gente fecha já".

LLM com prompt específico para identificar.

---

## 7.8. Análise em tempo real (alertas)

**Configuração:** desabilitada por default (consome bateria, pode incomodar). Usuário ativa em Settings.

### Arquitetura

```
Durante a gravação:
    ▼
Buffer de últimos 30s de transcrição parcial 
(via VAD + Whisper-tiny rodando em loop, ~real-time)
    ▼
A cada 5s, AlertEngine analisa buffer:
    - Regras simples: silêncio > 30s, sem pergunta há 10min, ...
    - LLM Phi-3 mini opcional: "Esse trecho tem alguma coisa estranha?"
    ▼
Se alerta disparado:
    - Vibração discreta
    - Notificação se app não em foco
    - Salvo em log de alertas da sessão
```

**Importante:** transcrição "parcial em tempo real" usa modelo **tiny** (mais rápido, menor precisão), não substitui transcrição final pós-aula com modelo Small/Medium.

**Trade-off:** consumo de bateria sobe ~3x quando alertas em real-time estão ON. Documentar e dar default OFF.

---

## 7.9. Integração com cloud LLM

### Gerenciamento de API keys

```
[Configurações > IA em nuvem]

Provedores configurados:
  ✓ Anthropic Claude          [Editar key]  [Remover]
  ⊘ OpenAI                    [+ Configurar]
  ⊘ Google Gemini             [+ Configurar]

Análises permitidas em nuvem:
  ☑ Plano de aula reverso
  ☑ Material de estudo
  ☑ Comparação com aulas anteriores
  ☐ Resumo executivo (já roda local)
  
Cobrança:
  Você paga diretamente ao provedor (Anthropic, OpenAI, Google).
  AulaLogger não cobra nada e não passa pelos nossos servidores.
  
  Estimativa de custo por aula de 4h:
    Claude Haiku:   ~R$ 0,30
    Claude Sonnet:  ~R$ 1,80
    GPT-4o-mini:    ~R$ 0,30
    Gemini Flash:   ~R$ 0,10

[ Privacidade: o que vai pra nuvem? ]
  • Apenas o TEXTO da transcrição
  • Nunca o áudio
  • Nunca metadados pessoais (nome de aluno, etc)
  • Você escolhe quais aulas analisar com cloud
```

### Cliente

```kotlin
sealed class CloudLLMProvider {
    abstract suspend fun generate(prompt: String, maxTokens: Int): String
}

class ClaudeProvider(private val apiKey: String, private val model: String = "claude-haiku-4-5") : CloudLLMProvider() {
    override suspend fun generate(prompt: String, maxTokens: Int): String {
        val client = HttpClient(CIO)
        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", model)
                put("max_tokens", maxTokens)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
            }.toString())
        }
        // parse, error handling, retry...
        return parseClaudeResponse(response)
    }
}

class OpenAIProvider(...) { /* idem */ }
class GeminiProvider(...) { /* idem */ }
```

### Storage seguro de API keys

`EncryptedSharedPreferences` (Android Jetpack Security). Criptografado at-rest com chave do Keystore.

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val prefs = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

prefs.edit().putString("anthropic_api_key", apiKey).apply()
```

---

## 7.10. Validação de output do LLM

LLM pode produzir JSON inválido, alucinado, ou simplesmente errado. Defesas:

1. **Sempre validar JSON com Zod** após retorno.
2. **Retry com prompt de correção** se inválido (max 2 retries).
3. **Sanidade-check semântica:** ex, se "topics" tem 0 itens em uma aula de 4h, algo está errado.
4. **Marcação de incerteza:** quando LLM retornar baixa confiança ou JSON malformado, mostrar "Análise pode estar incompleta — gerar de novo?"

---

## 7.11. Fluxo do usuário

```
Aula gravada e transcrita
        ▼
Análises automáticas em background:
  ⏳ Resumo executivo
  ⏳ Tópicos abordados
  ⏳ Glossário
  ⏳ Perguntas
  ⏳ Conceitos
  ⏳ Métricas
        ▼
Tudo pronto → notificação "Aula de Python 03/05: análise pronta"
        ▼
Usuário abre aula → vê tab "Análise"
        ▼
Pode pedir análise sob demanda (cloud opcional):
  [+ Gerar plano de aula reverso]
  [+ Gerar material de estudo]
  [+ Comparar com aulas anteriores]
```

---

## 7.12. UI de análise

```
[Tela: Aula de Python — 03/05 — Aba "Análise"]

📋 Resumo executivo
   Aula introdutória sobre list comprehensions em Python. Foram 
   cobertos: sintaxe básica, comparação com for loops, uso com 
   filtros condicionais e nested comprehensions. 15 perguntas dos 
   alunos foram respondidas. Aula concluiu com exercício prático e 
   indicação de leitura para próxima.
   [Ver detalhado]

🗂 Tópicos                                              [Ver todos]
   • Introdução (15min)
   • Sintaxe básica (28min)
   • Filtros condicionais (35min)
   • Nested comprehensions (42min)
   • Exercícios e dúvidas (52min)
   • Conclusão (8min)

📚 Glossário (12 termos)                                [Ver todos]
   list comprehension · iterable · expression · ...

❓ Perguntas dos alunos (15)                             [Ver todas]
   ⚠ 2 perguntas ficaram sem resposta clara

📊 Métricas pedagógicas
   Você falou 71% do tempo. Alunos 18%. Silêncios 11%.
   Ritmo médio: 142 palavras/min (saudável).
   Maior monólogo seu: 7min23s.
   23 perguntas que você fez aos alunos.

💡 Análises avançadas (cloud opcional)
   [+ Gerar plano de aula reverso]
   [+ Gerar material de estudo]
   [+ Comparar com aula anterior]
```

---

## 7.13. Plano de implementação

| Sprint | Entrega |
|--------|---------|
| Sprint 16 (sem 19) | llama.cpp build + JNI bridge + carregamento Gemma 2B |
| Sprint 16 (sem 19) | Prompts v1 + map-reduce + validação Zod |
| Sprint 17 (sem 20) | Análises pós-aula automáticas (resumo, tópicos, glossário, perguntas, conceitos) |
| Sprint 17 (sem 20) | Métricas pedagógicas rule-based |
| Sprint 18 (sem 21) | UI de análise, dashboards |
| Sprint 19 (sem 22) | Integração cloud (Claude, OpenAI, Gemini) |
| Sprint 19 (sem 22) | Análises sob demanda (plano reverso, material estudo, etc) |
| Sprint 20 (sem 23) | Análise em tempo real (alertas), config |
| Sprint 20 (sem 23) | Comparação entre aulas, dashboard de evolução |

---

## 7.14. Considerações sobre custo/responsabilidade da IA cloud

- **Usuário usa sua própria conta/key** com Anthropic/OpenAI/Google. AulaLogger nunca proxia chamadas (sem servidor próprio = sem custos pra gente, sem responsabilidade de terms-of-use de terceiros).
- **Estimativa de custo** mostrada antes de cada análise.
- **Limite mensal opcional** que usuário configura ("não gastar mais de R$ X/mês").
- **Sem retenção de dados** pelo provedor: usar parâmetros que pedem opt-out de training (Anthropic/OpenAI permitem por API).
- **Mensagem de privacidade** claríssima na primeira ativação de cloud.
