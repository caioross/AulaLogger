# 04 — Pipeline de áudio (limpeza e qualidade)

> Como transformar áudio bruto do microfone em áudio limpo, normalizado e pronto para alimentar Whisper, com qualidade suficiente para uma transcrição "perfeita" como você descreveu.

---

## 4.1. Por que limpar o áudio antes de transcrever?

Whisper é robusto, mas:
- Ruído de fundo (ar condicionado, projetor, conversa lateral) degrada WER em 10–30%.
- Volume muito baixo causa "alucinações" (Whisper inventa palavras).
- Volume muito alto causa clipping e palavras perdidas.
- Eco (sala grande, mic distante) confunde diarização.

**Pipeline = transformação antes da transcrição** que melhora WER em 5–15 pontos percentuais e a qualidade da diarização significativamente.

**Mas o áudio original (cru) é sempre preservado.** A limpeza é um "espelho" do áudio para alimentar os modelos. Se o usuário quiser exportar o áudio "como veio", tem.

---

## 4.2. Estágios do pipeline

```
Chunks WAV PCM 16kHz mono brutos
            │
            ▼
   ┌───────────────────┐
   │ 1. DC offset      │ Remove componente DC (mic ruim)
   │    removal         │
   └─────────┬─────────┘
             ▼
   ┌───────────────────┐
   │ 2. High-pass       │ Corta < 80Hz (rumble, vento)
   │    filter (80Hz)   │
   └─────────┬─────────┘
             ▼
   ┌───────────────────┐
   │ 3. Noise suppress  │ RNNoise — neural denoise
   │    (RNNoise)       │
   └─────────┬─────────┘
             ▼
   ┌───────────────────┐
   │ 4. Normalize       │ Loudness Normalization (LUFS)
   │    (LUFS -23)      │ via libsamplerate/sox-like
   └─────────┬─────────┘
             ▼
   ┌───────────────────┐
   │ 5. Limiter         │ Previne clipping pós-normalize
   └─────────┬─────────┘
             ▼
   ┌───────────────────┐
   │ 6. VAD trimming   │ Identifica e MARCA segmentos
   │    (Silero VAD)    │ de silêncio (não remove,
   │                    │ apenas marca para skip
   │                    │ no Whisper)
   └─────────┬─────────┘
             ▼
   Chunks "limpos" prontos para Whisper
```

**Importante:** o pipeline opera sobre **cópias** dos chunks, não sobre os arquivos originais. Output em `cleaned-NNNNN.wav` no mesmo session-dir.

---

## 4.3. Detalhamento por estágio

### 4.3.1. DC offset removal

Microfones podem ter um pequeno offset constante. Removemos:

```kotlin
fun removeDCOffset(samples: ShortArray): ShortArray {
    val mean = samples.map { it.toInt() }.average().toInt()
    return ShortArray(samples.size) { (samples[it] - mean).toShort() }
}
```

Custo: O(N), trivial.

### 4.3.2. High-pass filter (80Hz)

Remove rumble (passos, AC, vento). Voz humana começa em ~85Hz (homem grave) ou ~165Hz (mulher), então 80Hz é seguro.

Implementação: filtro IIR Butterworth de 2ª ordem.

```kotlin
class HighPassFilter(sampleRate: Int = 16000, cutoffHz: Float = 80f) {
    // Coeficientes pré-calculados (Butterworth 2nd order)
    private val a0: Float; private val a1: Float; private val a2: Float
    private val b1: Float; private val b2: Float
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f
    
    init { /* calcular coefs */ }
    
    fun process(samples: ShortArray): ShortArray {
        val output = ShortArray(samples.size)
        for (i in samples.indices) {
            val x0 = samples[i].toFloat()
            val y0 = a0 * x0 + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2
            output[i] = y0.toInt().coerceIn(-32768, 32767).toShort()
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return output
    }
}
```

Stateful: o filtro mantém estado entre chunks para não criar artifacts nas bordas.

### 4.3.3. Noise suppression — RNNoise

[RNNoise](https://github.com/xiph/rnnoise) é uma rede neural recorrente leve (~85KB de modelo) treinada para denoise de fala. Excelente custo-benefício, roda em real-time em qualquer hardware.

**Integração:** RNNoise é C, importamos via JNI.

```kotlin
class RNNoiseProcessor {
    private val state: Long  // ponteiro pra DenoiseState C
    
    init {
        state = nativeCreate()
    }
    
    external fun nativeCreate(): Long
    external fun nativeProcess(state: Long, frame: ShortArray): Float  // returns VAD prob
    external fun nativeDestroy(state: Long)
    
    fun process(samples: ShortArray): ShortArray {
        // RNNoise opera em frames de 480 samples @ 48kHz, mas funciona com 16kHz upsampled
        // Ou usar variante adaptada pra 16kHz nativo
        val output = samples.copyOf()
        // process in-place em frames de 480
        var i = 0
        while (i + 480 <= output.size) {
            val frame = output.copyOfRange(i, i + 480)
            nativeProcess(state, frame)
            System.arraycopy(frame, 0, output, i, 480)
            i += 480
        }
        return output
    }
}
```

**Alternativa moderna:** [DeepFilterNet](https://github.com/Rikorose/DeepFilterNet) — qualidade superior, ~5MB de modelo, roda em ARM NEON. Pesa mais. **Decisão:** começar com RNNoise (mais leve), oferecer DeepFilterNet como opção avançada.

**Configuração de usuário:**
- "Sem denoise": áudio cru. Para gravações em ambiente já silencioso.
- "Denoise leve" (RNNoise): default.
- "Denoise forte" (DeepFilterNet): para ambiente ruidoso.
- "Denoise da nuvem" (futuro): se usuário tiver internet, manda chunks pra serviço externo. **Não na v1.**

### 4.3.4. Normalize (LUFS)

Normalização baseada em loudness percebida, não em pico:

- **LUFS** (Loudness Units Full Scale) é a unidade que considera percepção humana.
- Padrão broadcast: -23 LUFS. Para fala mais próxima e clara: -16 a -20 LUFS.
- Calculamos LUFS integrado da sessão completa, aplicamos ganho global.

```kotlin
class LoudnessNormalizer(targetLufs: Float = -20f) {
    fun computeLufs(samples: ShortArray, sampleRate: Int): Float {
        // Implementa ITU-R BS.1770-4
        // (algo de complexidade média; usamos lib pré-implementada se possível)
    }
    
    fun applyGain(samples: ShortArray, gainDb: Float): ShortArray {
        val factor = 10.0.pow(gainDb / 20.0).toFloat()
        return ShortArray(samples.size) { 
            (samples[it] * factor).toInt().coerceIn(-32768, 32767).toShort() 
        }
    }
}
```

### 4.3.5. Limiter

Após ganho, alguns picos podem clipping. Limiter soft previne:

```kotlin
fun softLimit(sample: Short, threshold: Float = 0.95f): Short {
    val normalized = sample / 32768f
    val limited = if (abs(normalized) > threshold) {
        sign(normalized) * (threshold + (1 - threshold) * tanh((abs(normalized) - threshold) / (1 - threshold)))
    } else {
        normalized
    }
    return (limited * 32768).toInt().coerceIn(-32768, 32767).toShort()
}
```

### 4.3.6. VAD (Voice Activity Detection)

[Silero VAD](https://github.com/snakers4/silero-vad) é o estado da arte: ONNX, ~1MB, roda em real-time.

**Uso:** identifica segmentos de voz vs silêncio. Whisper transcreve apenas segmentos com voz, economizando tempo.

```kotlin
class SileroVad(modelPath: String) {
    private val ortSession: OrtSession
    
    fun detectSegments(samples: FloatArray, sampleRate: Int = 16000): List<VoiceSegment> {
        val frameSize = 512
        val segments = mutableListOf<VoiceSegment>()
        var inSpeech = false
        var speechStart = 0
        
        for (i in 0 until samples.size step frameSize) {
            val frame = samples.copyOfRange(i, minOf(i + frameSize, samples.size))
            val prob = ortSession.runInference(frame).speechProb
            
            if (prob > 0.5 && !inSpeech) {
                inSpeech = true
                speechStart = i
            } else if (prob < 0.35 && inSpeech) {
                inSpeech = false
                segments.add(VoiceSegment(speechStart, i))
            }
        }
        if (inSpeech) segments.add(VoiceSegment(speechStart, samples.size))
        return segments
    }
}

data class VoiceSegment(val startSample: Int, val endSample: Int)
```

**Resultado:** lista de segmentos de voz que serão alimentados ao Whisper, em vez do áudio inteiro.

---

## 4.4. Decisões de formato e qualidade

### 4.4.1. Formato durante gravação

| Critério | Escolha | Por quê |
|----------|---------|---------|
| Sample rate | 16000 Hz | Whisper é treinado nessa taxa |
| Channels | 1 (mono) | Voz, sem benefício estéreo |
| Bit depth | 16-bit PCM | Padrão, sem perda |
| Container | WAV | Sem header complexo, chunks reproduzíveis |
| Compressão | Nenhuma | Robustez > tamanho |

**Tamanho:** ~115MB/h. Razoável para celulares modernos (32GB+ comum).

### 4.4.2. Formato pós-sessão (opcional)

Após gravação concluída, opção de "comprimir" áudio:

| Opção | Codec | Tamanho | Qualidade |
|-------|-------|---------|-----------|
| Sem compressão | WAV PCM 16kHz | 100% | Original |
| Lossless | FLAC | ~50% | Original (sem perda) |
| Voz otimizada | Opus 24kbps | ~10% | Excelente para voz |
| Compatibilidade | MP3 64kbps | ~13% | Boa, universal |

**Default:** manter WAV. Configurável.

### 4.4.3. Configurações expostas ao usuário

```
[Configurações > Áudio]

Qualidade da gravação:
  ○ Padrão (16kHz mono — recomendado)
  ○ Alta (44.1kHz mono — para gravações musicais incidentais)
  
Source de áudio:
  ○ Microfone (cru — recomendado)
  ○ Microfone com pré-processamento do sistema

Limpeza de áudio para transcrição:
  ○ Sem limpeza
  ● Limpeza leve (RNNoise) — recomendado
  ○ Limpeza forte (DeepFilterNet) — celulares com 6GB+ RAM
  
Voice Activity Detection:
  ☑ Pular silêncios na transcrição (recomendado)
  
Compressão pós-sessão:
  ○ Manter sem compressão (WAV) — recomendado
  ○ FLAC (lossless, ~50% do tamanho)
  ○ Opus 24kbps (~10%, excelente para voz)
  ○ MP3 64kbps (~13%, máxima compatibilidade)
  
[Avançado]
  Tamanho do chunk: [30] segundos
  Sample rate: [16000] Hz
  Bit depth: [16-bit]
  Threshold do VAD: [0.50]
```

---

## 4.5. Quando o pipeline roda

**Não em tempo real durante gravação.** O pipeline é caro (especialmente DeepFilterNet, normalização) e poderia atrapalhar a estabilidade da gravação.

**Roda como WorkManager job pós-sessão:**
1. Sessão termina (usuário aperta "parar").
2. WorkManager enfileira `AudioCleanupJob`.
3. Job lê chunks brutos, aplica pipeline, salva em `cleaned-NNNNN.wav`.
4. Sucesso → enfileira próximo job (`TranscribeJob`).
5. Em UI: notificação "Processando áudio... 23%".

**Custo de tempo:** ~0.05x–0.15x tempo real (4h de áudio = 12–36 minutos de processamento). Em background, OK.

---

## 4.6. Verificação de qualidade

Após pipeline, antes de transcrever, verificamos:

| Métrica | Threshold |
|---------|-----------|
| RMS médio | > -40 dBFS (não áudio fantasma) |
| RMS máximo | < -3 dBFS (não clipped) |
| % silêncio (via VAD) | < 95% (não foi gravação muda) |
| Duração efetiva voz | > 60s (vale a pena transcrever?) |

Se algo falhar, alertamos o usuário antes de gastar 1h de CPU transcrevendo lixo.

---

## 4.7. Casos especiais

### Áudio com música (instrutor toca exemplo)
RNNoise/DeepFilterNet podem distorcer música. Adicionamos opção "preservar música" que pula denoise em segmentos de alta energia em frequências não-vocais.

### Áudio com tela compartilhada / vídeo de aula
Áudio de vídeos exibidos pelo professor (YouTube, slides com áudio) entra junto. **Sem solução perfeita.** Documentamos como esperado e oferecemos diarização para separar o que é "professor" do "vídeo".

### Eco em sala grande
DeepFilterNet faz dereverberação razoável. Para casos extremos, configuração avançada "Modo sala grande" aumenta agressividade.

### Múltiplos microfones (ex: lapela + ambiente)
**Não suportado na v1.** AudioRecord do Android só captura um source por vez. Usuário escolhe o melhor mic.

---

## 4.8. Plano de implementação do pipeline

| Sprint | Entrega |
|--------|---------|
| Sprint 6 (sem 9) | DC removal + High-pass + Limiter + Normalize (puro Kotlin) |
| Sprint 6 (sem 9) | Integração RNNoise via JNI |
| Sprint 7 (sem 10) | Integração Silero VAD via ONNX Runtime |
| Sprint 7 (sem 10) | DeepFilterNet (opcional) |
| Sprint 8 (sem 11) | Conversor de formato (FLAC/Opus/MP3) — usar FFmpeg-Android |
| Sprint 8 (sem 11) | Configurações de usuário, testes A/B, métricas de qualidade |
