# 06 — Diarização (separar a sua voz da dos alunos)

> Como o app entende **quem** está falando em cada momento, separa "Professor" de "Aluno A", "Aluno B", e como o usuário cadastra a própria voz para identificação automática.

---

## 6.1. O problema

Whisper te dá: "Texto de tudo o que foi falado, com timestamps."

Mas em uma aula você precisa de: "Texto de tudo o que foi falado, com timestamps, **e quem falou cada parte**."

Isso é **diarização de speakers** (speaker diarization). Tipicamente envolve:
1. **VAD** — onde tem voz, onde tem silêncio.
2. **Segmentação** — quebrar áudio em "turnos de fala".
3. **Embedding** — para cada turno, gerar um vetor representando a voz.
4. **Clustering** — agrupar vetores semelhantes = mesmo speaker.
5. **(Opcional) Identification** — comparar embeddings com vozes conhecidas (você).

---

## 6.2. Tecnologia escolhida: sherpa-onnx

[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) é um framework de inference para modelos de fala (ASR, TTS, diarização) baseado em ONNX Runtime. Cross-platform, com binding para Android.

### Por que sherpa-onnx?

- **Pipeline pronto** de diarização (VAD + segment + embedding + cluster).
- **ONNX Runtime** roda em Android com aceleração NNAPI.
- **Modelos pré-convertidos** disponíveis: pyannote VAD, pyannote segmentation, NeMo embeddings.
- **Apache 2.0**, sem fricção de licenciamento.
- **Bem documentado** com exemplos Android.

### Alternativas consideradas

- **pyannote.audio direto:** state of art mas só PyTorch, não viável Android sem ONNX export complexo.
- **Resemblyzer + clustering manual:** funciona mas precisa juntar peças manualmente.
- **NVIDIA NeMo:** pesado, focado em inference em servidor.
- **WeSpeaker:** ótimos embeddings mas sem pipeline completo pronto.

---

## 6.3. Arquitetura do DiarizationEngine

```
Cleaned audio (sessão completa, ou em chunks grandes ~5min)
            │
            ▼
   ┌─────────────────────────────────┐
   │ Stage 1: VAD                    │
   │  Modelo: pyannote VAD ONNX      │
   │  Output: segmentos com voz      │
   │  [(0.5s, 12.3s), (15.0s, ...)] │
   └────────────┬────────────────────┘
                ▼
   ┌─────────────────────────────────┐
   │ Stage 2: Speaker segmentation   │
   │  Modelo: pyannote segmentation  │
   │  Output: dentro de cada seg VAD,│
   │  identifica TURNOS distintos    │
   │  (mudança de speaker)           │
   └────────────┬────────────────────┘
                ▼
   ┌─────────────────────────────────┐
   │ Stage 3: Embedding              │
   │  Modelo: NeMo TitaNet ou        │
   │  ECAPA-TDNN ONNX                │
   │  Output: vetor 192d por turno   │
   └────────────┬────────────────────┘
                ▼
   ┌─────────────────────────────────┐
   │ Stage 4: Clustering             │
   │  Algoritmo: spectral / agglom   │
   │  Output: cada turno → speaker_id│
   │  (anônimos: speaker_0, _1, _2)  │
   └────────────┬────────────────────┘
                ▼
   ┌─────────────────────────────────┐
   │ Stage 5: Identificação          │
   │  Compara embedding médio de     │
   │  cada speaker com fingerprint   │
   │  enrolled do professor          │
   │  Output: speaker_X = "Professor"│
   └────────────┬────────────────────┘
                ▼
   Lista: [(start_ms, end_ms, speaker_label), ...]
                │
                ▼
   ┌─────────────────────────────────┐
   │ Stage 6: Merge com transcrição  │
   │  Para cada segmento de Whisper, │
   │  atribuir speaker baseado em    │
   │  overlap temporal               │
   └─────────────────────────────────┘
                │
                ▼
   transcript_final.json com speakers
```

---

## 6.4. Speaker Enrollment (cadastro da sua voz)

Para identificar **automaticamente** que "Speaker 0 é o Professor", precisamos da sua voz cadastrada.

### Fluxo de UX de enrollment

```
[Configurações > Identificação de voz > Cadastrar minha voz]

📢 Vamos cadastrar sua voz para que o app reconheça automaticamente 
   que você é o professor nas próximas aulas.
   
   Você vai ler 3 frases curtas em voz natural, como se estivesse 
   dando aula. ~30 segundos no total.

   [Próximo]

────────────────────────

📢 Frase 1 de 3

   Por favor leia em voz alta:

   "Bom dia. Hoje vamos começar nossa aula explorando os conceitos 
    fundamentais e na sequência veremos exemplos práticos para fixar 
    o conteúdo."

   [▶ Gravando: 0:08 / 0:15]

   [Reler]   [Próxima frase]

────────────────────────

✓ Voz cadastrada com sucesso

   Agora, durante as suas aulas, o app vai marcar automaticamente 
   o que você fala como "Professor". Outras pessoas serão marcadas 
   como "Aluno A", "Aluno B", etc.
   
   Você pode editar essas marcações depois se quiser.

   [Ok]
```

### Implementação técnica

1. Gravar 3 amostras de ~10s cada (frases pré-definidas, escolhidas para variação fonética).
2. Limpar áudio (RNNoise + normalize).
3. Para cada amostra, gerar embedding via NeMo TitaNet.
4. Calcular **embedding médio** das 3 = **voice fingerprint** do professor.
5. Persistir em `EncryptedSharedPreferences` (criptografado at-rest).

```kotlin
class SpeakerEnrollment(private val embeddingExtractor: SpeakerEmbedding) {
    suspend fun enrollUser(samplesList: List<FloatArray>): VoiceFingerprint {
        require(samplesList.size >= 2) { "Need at least 2 samples" }
        
        val embeddings = samplesList.map { embeddingExtractor.extract(it) }
        val mean = averageEmbeddings(embeddings)
        val variance = computeVariance(embeddings, mean)
        
        return VoiceFingerprint(
            embedding = mean,
            variance = variance,
            sampleCount = samplesList.size,
            createdAt = Clock.System.now()
        )
    }
    
    fun matchSpeaker(speakerEmbedding: FloatArray, fingerprint: VoiceFingerprint, threshold: Float = 0.65f): Boolean {
        val similarity = cosineSimilarity(speakerEmbedding, fingerprint.embedding)
        return similarity > threshold
    }
}
```

### Re-enrollment

Vozes mudam (resfriado, cansaço, microfone diferente). Após N aulas, o app pode sugerir re-enrolling com voz "atualizada" baseada em segmentos com alta confiança das aulas recentes.

**Decisão:** v1.2 com enrollment manual. v1.3+ com sugestão automática de re-enroll.

---

## 6.5. Identificação de outros speakers (alunos)

**v1.2:** speakers não-professor são identificados como "Aluno A", "Aluno B", etc. — apenas via clustering. Sem nomes.

**v1.3 (futuro):** opção de cadastrar alunos recorrentes (ex: tutor 1-on-1 com Pedro toda semana).

```
[Configurações > Vozes conhecidas]

  👤 Professor (você)               [Editar]
  👤 Pedro (tutor avançado)         [Editar]  [Apagar]
  
  [+ Cadastrar nova voz]
```

**v1.x:** opção de renomear "Aluno A" para "Pedro" dentro de uma aula específica. Renomeação é por aula, não global (privacidade).

---

## 6.6. Merge transcrição + diarização

Após ter:
- Lista de segmentos de Whisper: `[(start, end, text), ...]`
- Lista de turnos diarizados: `[(start, end, speaker), ...]`

Merge:

```kotlin
fun mergeTranscriptWithDiarization(
    transcriptSegments: List<TranscriptSegment>,
    speakerTurns: List<SpeakerTurn>
): List<EnrichedSegment> {
    return transcriptSegments.map { ts ->
        val midPoint = (ts.startMs + ts.endMs) / 2
        val turn = speakerTurns.find { midPoint >= it.startMs && midPoint < it.endMs }
        EnrichedSegment(
            startMs = ts.startMs,
            endMs = ts.endMs,
            text = ts.text,
            speaker = turn?.speakerLabel ?: "Desconhecido",
            confidence = computeOverlapConfidence(ts, turn)
        )
    }
}
```

### Edge cases

- Whisper retorna segmento que cobre mudança de speaker no meio. **Solução:** quando confidence baixa (overlap parcial), oferecer split na UI.
- Diarização não cobre todo o áudio (silêncio entre turnos). **Solução:** atribuir ao último speaker conhecido ou marcar "Desconhecido".

---

## 6.7. UI de viewer com diarização

```
[Tela: Transcrição da Aula de Python — 03/05]

🟦 Professor                     20:30:00
   Bom dia pessoal, hoje vamos falar sobre list comprehensions 
   no Python. Antes de começar, alguém tem dúvida da aula passada?

🟧 Aluno A                       20:30:24
   Professor, a parte de dicionários eu fiquei meio perdido na 
   diferença entre items() e values().

🟦 Professor                     20:30:38
   Boa pergunta. Então, items() retorna pares chave-valor, enquanto 
   values() retorna só os valores...

🟩 Aluno B                       20:31:12
   Eu também tenho uma dúvida relacionada a isso...

[ ▶ Reproduzir desde 20:30:24 ]   [📝 Editar speakers]   [💾 Exportar]
```

Cores: cada speaker tem cor distinta. Toque no avatar permite renomear.

### Edição de atribuições

Usuário pode:
- Renomear "Aluno A" → "Pedro" (escopo: esta aula).
- Reatribuir um segmento (drag-drop, ou tap → menu speakers).
- Mesclar dois speakers detectados como mesma pessoa ("Aluno A" e "Aluno C" eram a mesma voz).
- Dividir um speaker em dois (raro).

Edições são persistidas. Re-execução de diarização não desfaz edições do usuário.

---

## 6.8. Integração com sherpa-onnx no Android

### Setup

`build.gradle` da app:

```gradle
dependencies {
    implementation 'com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.x'
    // ou usar AAR baixado se a versão Maven não tiver tudo
}
```

### Modelos a baixar

- **VAD:** `silero_vad.onnx` (~1MB)
- **Segmentation:** `pyannote-segmentation-3.0.onnx` (~6MB)
- **Embedding:** `nemo_en_titanet_small.onnx` (~28MB) ou `3dspeaker_resnet34.onnx`

Total: ~35MB. Baixados na primeira vez que diarização é usada.

### Código (esqueleto)

```kotlin
class DiarizationEngine(context: Context) {
    private val vadModel = File(context.filesDir, "models/silero_vad.onnx")
    private val segModel = File(context.filesDir, "models/pyannote-segmentation.onnx")
    private val embModel = File(context.filesDir, "models/titanet.onnx")
    
    private val sherpa: OfflineSpeakerDiarization
    
    init {
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteConfig(model = segModel.path)
            ),
            embedding = SpeakerEmbeddingExtractorConfig(model = embModel.path),
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f)
        )
        sherpa = OfflineSpeakerDiarization(config)
    }
    
    suspend fun diarize(audioFile: File): List<SpeakerTurn> = withContext(Dispatchers.Default) {
        val samples = readWavAsFloat(audioFile)
        val result = sherpa.process(samples)
        result.segments.map { seg ->
            SpeakerTurn(
                startMs = (seg.startSeconds * 1000).toLong(),
                endMs = (seg.endSeconds * 1000).toLong(),
                speakerId = seg.speaker,  // 0, 1, 2, ...
                speakerLabel = "Speaker ${seg.speaker}"
            )
        }
    }
}
```

---

## 6.9. Identificação do professor após clustering

Após sherpa retornar clusters anônimos (Speaker 0, 1, 2):

```kotlin
fun identifyProfessor(turns: List<SpeakerTurn>, audioFile: File, fingerprint: VoiceFingerprint): List<SpeakerTurn> {
    // Para cada speaker_id, calcular embedding médio dos seus segmentos
    val speakerEmbeddings = turns
        .groupBy { it.speakerId }
        .mapValues { (_, segments) ->
            val embeddings = segments.map { 
                val samples = extractSamples(audioFile, it.startMs, it.endMs)
                embeddingExtractor.extract(samples)
            }
            averageEmbeddings(embeddings)
        }
    
    // Encontrar speaker mais similar à fingerprint do professor
    val professorSpeakerId = speakerEmbeddings
        .maxByOrNull { (_, emb) -> cosineSimilarity(emb, fingerprint.embedding) }
        ?.takeIf { (_, emb) -> cosineSimilarity(emb, fingerprint.embedding) > 0.65 }
        ?.key
    
    // Reatribuir labels
    val labelMap = speakerEmbeddings.keys.mapIndexed { idx, id -> 
        id to if (id == professorSpeakerId) "Professor" else "Aluno ${('A' + idx).toChar()}"
    }.toMap()
    
    return turns.map { it.copy(speakerLabel = labelMap[it.speakerId] ?: it.speakerLabel) }
}
```

---

## 6.10. Métricas de qualidade

### DER (Diarization Error Rate)

DER = (False Alarm + Missed Detection + Speaker Confusion) / Total Speech Time

- DER < 10%: excelente.
- DER 10–20%: aceitável (nosso target v1.2).
- DER > 30%: ruim (precisamos investigar).

### Métricas práticas

- **% do tempo do professor identificado corretamente** (após enrollment): meta > 95%.
- **# de speakers detectados** vs realidade: tolerância ±1 (em aula com 5 alunos esporádicos, detectar 4 ou 6 é OK).
- **Latência de diarização** para sessão de 4h: meta < 1h em celular médio.

---

## 6.11. Limitações honestas

Vamos ser claros com o usuário:

- **Vozes muito parecidas** (irmãos, mesma faixa de tom) podem ser confundidas.
- **Áudio de baixa qualidade** degrada significativamente a diarização.
- **Sobreposição de fala** (várias pessoas falando ao mesmo tempo) é mal tratada — esses momentos viram "Speaker desconhecido".
- **Vozes muito raras na aula** (aluno fala 1 frase a aula inteira) podem ser perdidas.
- **Mudança brusca de microfone** (você muda de mic no meio) pode confundir o cluster do professor.

Documentamos isso em ajuda:

```
ⓘ Sobre identificação de vozes

  • Em aulas com até 5 pessoas, normalmente identifica corretamente 
    > 90% dos turnos.
  • Em aulas com muitos alunos esporádicos, alguns podem ser 
    agrupados ou perdidos.
  • Áudios em ambiente barulhento têm precisão menor.
  • Você sempre pode corrigir manualmente as atribuições.
```

---

## 6.12. Plano de implementação

| Sprint | Entrega |
|--------|---------|
| Sprint 12 (sem 15) | Setup sherpa-onnx, modelos baixados, pipeline básico funcional |
| Sprint 12 (sem 15) | UI de enrollment (cadastro de voz) |
| Sprint 13 (sem 16) | Identificação automática do professor via fingerprint |
| Sprint 13 (sem 16) | Merge transcript + diarização |
| Sprint 14 (sem 17) | UI viewer com cores por speaker |
| Sprint 14 (sem 17) | Edição de atribuições, renomeação, merge speakers |
| Sprint 15 (sem 18) | Testes de qualidade, tuning de threshold de clustering |
