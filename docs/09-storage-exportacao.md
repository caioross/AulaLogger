# 09 — Storage, persistência, exportação

> Como os dados ficam organizados em disco, como o SQLite é estruturado, e como o usuário exporta áudio, transcrição e análise para fora do app.

---

## 9.1. Visão geral

O AulaLogger persiste em três camadas:

1. **SQLite (Room)** — metadados estruturados, queryáveis, indexados.
2. **Filesystem** — arquivos grandes (áudio, modelos ML, exports).
3. **EncryptedSharedPreferences** — config sensível (API keys, voice fingerprint).

Tudo dentro de `/data/data/com.aulalogger.app/`, privado ao app, com criptografia opcional.

---

## 9.2. Schema do banco SQLite

```sql
-- Sessões de gravação (cada "aula")
CREATE TABLE sessions (
    id              TEXT PRIMARY KEY,           -- uuid v4
    name            TEXT NOT NULL,
    started_at      INTEGER NOT NULL,           -- epoch ms
    ended_at        INTEGER,                     -- null se ainda gravando
    duration_sec    INTEGER,                     -- preenchido após terminar
    status          TEXT NOT NULL,              -- recording, paused, stopped, transcribed, analyzed, archived, deleted
    audio_format    TEXT NOT NULL,              -- wav | flac | opus | mp3
    audio_path      TEXT NOT NULL,              -- caminho para diretório com chunks
    sample_rate     INTEGER NOT NULL,
    bit_depth       INTEGER NOT NULL,
    channels        INTEGER NOT NULL,
    chunks_count    INTEGER NOT NULL DEFAULT 0,
    notes           TEXT,
    tags            TEXT,                        -- JSON array
    soft_deleted_at INTEGER,                     -- null se não deletado
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);

CREATE INDEX idx_sessions_started_at ON sessions(started_at DESC);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_soft_deleted ON sessions(soft_deleted_at) WHERE soft_deleted_at IS NULL;

-- Marcadores adicionados durante/depois da gravação
CREATE TABLE markers (
    id          TEXT PRIMARY KEY,
    session_id  TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    timestamp_ms INTEGER NOT NULL,
    label       TEXT NOT NULL,
    note        TEXT,
    created_at  INTEGER NOT NULL
);

CREATE INDEX idx_markers_session_id ON markers(session_id, timestamp_ms);

-- Segmentos de transcrição
CREATE TABLE transcript_segments (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    start_ms        INTEGER NOT NULL,
    end_ms          INTEGER NOT NULL,
    text            TEXT NOT NULL,
    speaker_id      TEXT,                       -- referencia speakers, NULL antes de diarização
    speaker_label   TEXT,                       -- "Professor", "Aluno A", etc
    confidence      REAL,                        -- 0..1
    word_timestamps TEXT,                        -- JSON array [{w, t0, t1}]
    edited          INTEGER NOT NULL DEFAULT 0   -- bool: usuário modificou?
);

CREATE INDEX idx_transcript_session_time ON transcript_segments(session_id, start_ms);

-- FTS5 para busca em transcrições
CREATE VIRTUAL TABLE transcript_fts USING fts5(
    text, 
    session_id UNINDEXED, 
    segment_id UNINDEXED,
    tokenize='porter unicode61'
);

CREATE TRIGGER transcript_fts_insert AFTER INSERT ON transcript_segments BEGIN
    INSERT INTO transcript_fts(text, session_id, segment_id) VALUES (NEW.text, NEW.session_id, NEW.id);
END;
-- (similares para UPDATE e DELETE)

-- Speakers detectados em uma sessão
CREATE TABLE session_speakers (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    speaker_index   INTEGER NOT NULL,           -- 0, 1, 2 (do clustering)
    label           TEXT NOT NULL,              -- "Professor", "Aluno A", "Pedro"
    embedding       BLOB,                        -- vetor 192d (FloatArray serializado)
    is_user         INTEGER NOT NULL DEFAULT 0  -- bool: é o professor?
);

CREATE INDEX idx_speakers_session ON session_speakers(session_id);

-- Análises geradas (resumo, tópicos, etc)
CREATE TABLE analyses (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    type            TEXT NOT NULL,              -- summary, topics, glossary, questions, concepts, metrics, custom
    source          TEXT NOT NULL,              -- local | cloud_claude | cloud_openai | cloud_gemini
    model           TEXT,                        -- ex: gemma-2-2b, claude-haiku-4-5
    content         TEXT NOT NULL,              -- JSON estruturado
    prompt_version  TEXT NOT NULL,              -- ex: v1, v2 (para invalidação)
    created_at      INTEGER NOT NULL,
    duration_ms     INTEGER                      -- tempo que levou para gerar
);

CREATE INDEX idx_analyses_session_type ON analyses(session_id, type);

-- Cursos/disciplinas para agrupar aulas (futuro)
CREATE TABLE courses (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    color       TEXT,
    created_at  INTEGER NOT NULL
);

ALTER TABLE sessions ADD COLUMN course_id TEXT REFERENCES courses(id) ON DELETE SET NULL;

-- Eventos/alertas em tempo real durante uma sessão (se análise real-time ativa)
CREATE TABLE realtime_events (
    id          TEXT PRIMARY KEY,
    session_id  TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    timestamp_ms INTEGER NOT NULL,
    event_type  TEXT NOT NULL,                  -- silence, speed, jargon, ...
    payload     TEXT,                            -- JSON
    created_at  INTEGER NOT NULL
);

-- Configurações chave-valor (estado do app, exceto sensíveis)
CREATE TABLE app_settings (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,                   -- JSON
    updated_at  INTEGER NOT NULL
);

-- Modelos baixados (Whisper, sherpa, llama)
CREATE TABLE installed_models (
    id          TEXT PRIMARY KEY,
    family      TEXT NOT NULL,                   -- whisper | sherpa-vad | sherpa-segment | sherpa-embed | llama
    name        TEXT NOT NULL,                   -- whisper-small-q5
    path        TEXT NOT NULL,
    size_bytes  INTEGER NOT NULL,
    sha256      TEXT NOT NULL,
    version     TEXT,
    installed_at INTEGER NOT NULL
);
```

### Migrações

Room cuida de migrações com versão. Cada release nova que muda schema = nova migração `Migration(N, N+1)`. Test cada migração com banco antigo populado.

---

## 9.3. Estrutura no filesystem

```
/data/data/com.aulalogger.app/
├── databases/
│   └── aulalogger.db
│   └── aulalogger.db-wal
│   └── aulalogger.db-shm
├── files/
│   ├── recordings/
│   │   └── <session-uuid>/
│   │       ├── meta.json                       (mirror do estado pra recovery)
│   │       ├── chunk-00000.wav
│   │       ├── chunk-00001.wav
│   │       ├── ...
│   │       ├── cleaned/                         (após pipeline)
│   │       │   ├── cleaned-00000.wav
│   │       │   └── ...
│   │       ├── compressed.flac                  (opcional, gerado pós)
│   │       └── waveform.bin                     (cache de waveform pra player)
│   ├── transcripts/
│   │   └── <session-uuid>.json                 (snapshot exportável)
│   ├── analyses/
│   │   └── <session-uuid>/
│   │       ├── summary.json
│   │       ├── topics.json
│   │       └── ...
│   ├── exports/                                 (gerados sob demanda)
│   │   └── <session-uuid>/
│   │       ├── transcript.pdf
│   │       ├── transcript.docx
│   │       └── ...
│   ├── models/
│   │   ├── whisper/
│   │   │   └── ggml-small-q5_k_m.bin
│   │   ├── sherpa/
│   │   │   ├── silero_vad.onnx
│   │   │   ├── pyannote-segmentation.onnx
│   │   │   └── titanet.onnx
│   │   └── llm/
│   │       └── gemma-2-2b-it-q4_k_m.gguf
│   └── logs/
│       ├── app-2026-05-03.log
│       └── ...
├── shared_prefs/
│   ├── encrypted_prefs.xml                      (API keys, voice fingerprint)
│   └── app_prefs.xml                            (UI state, last-used)
└── cache/                                       (limpo automaticamente pelo OS)
    └── ...
```

---

## 9.4. Encryption at rest (opcional)

Default: arquivos em `/data/data/<pkg>/` já são protegidos pelo OS Android (acessíveis só ao app). Suficiente para a maioria.

**Opção avançada:** "Criptografar áudio em disco" em Settings. Ativar criptografa cada chunk com AES-GCM usando chave do Android Keystore.

- Custo: I/O extra ~5%, CPU para crypto.
- Benefício: se aparelho rooteado ou dump físico, áudio fica ilegível sem a chave do Keystore.

**v1.0:** sem essa opção. **v1.x:** adicionar como opt-in.

---

## 9.5. Backup e sync

### v1.0 — Apenas local

Sem cloud sync. Backup é manual: exportar arquivos via "Compartilhar" do Android para Drive/email/etc.

### v1.2 (futuro) — Backup opcional

```
[Configurações > Backup]

  ⊘ Sem backup automático (apenas local)
  ☐ Google Drive — pasta privada
  ☐ Pasta no celular — escolher
  ☐ Servidor próprio (WebDAV)
  
O que incluir no backup:
  ☑ Áudio comprimido (Opus)
  ☑ Transcrições
  ☑ Análises
  ☐ Áudio original (WAV) — usa muito espaço
  
Frequência: ao terminar processamento de cada aula
```

Implementação: WorkManager job, transferência incremental, retry, criptografia opcional pré-upload.

---

## 9.6. Exportação

### Formatos de exportação

| Formato | Conteúdo | Caso de uso |
|---------|----------|-------------|
| **WAV** | Áudio cru | Edição de áudio, máxima qualidade |
| **FLAC** | Áudio lossless comprimido | Arquivo, qualidade preservada |
| **Opus** | Áudio voz otimizada | Compartilhar, baixo tamanho |
| **MP3** | Áudio universal | Compatibilidade máxima |
| **TXT** | Transcrição plana | Processamento simples |
| **MD** | Transcrição em Markdown | Documentação, blogs |
| **DOCX** | Transcrição formatada | Word, edição |
| **PDF** | Transcrição formatada | Distribuição, leitura |
| **SRT** | Legendas com timestamps | Vídeo, legendas |
| **VTT** | Legendas web | YouTube, web |
| **JSON** | Dados estruturados completos | Integração, backup |
| **HTML** | Transcrição + análise tudo-em-um | Compartilhar, arquivar |

### UX de exportação

```
[Tela: Exportar Aula de Python]

O que exportar:
  ☑ Áudio
      ◯ WAV (482MB)
      ◯ FLAC (~250MB)
      ● Opus (~50MB)
      ◯ MP3 (~65MB)
  
  ☑ Transcrição
      ☐ TXT puro
      ☑ Markdown com speakers
      ☐ DOCX formatado
      ☑ PDF
      ☐ SRT
      ☐ VTT
      ☐ JSON completo
  
  ☑ Análise
      ☑ Resumo
      ☑ Tópicos
      ☐ Glossário
      ☐ Métricas
      ☐ Tudo
  
Destino:
  ◯ Salvar no celular (escolher pasta)
  ◯ Compartilhar (apps instalados)
  ● Tudo em um ZIP
  
[Exportar]   ~85MB total estimado
```

### Implementação de exportadores

```kotlin
sealed class Exporter<T> {
    abstract suspend fun export(session: Session, options: T): File
}

class PdfExporter : Exporter<PdfOptions>() {
    override suspend fun export(session: Session, options: PdfOptions): File {
        // Usa PDFBox-Android (Apache 2.0)
        val doc = PDDocument()
        // ... gerar páginas com tipografia, speaker headers, marcações
        val out = File(exportsDir, "${session.id}/transcript.pdf")
        doc.save(out)
        return out
    }
}

class DocxExporter : Exporter<DocxOptions>() {
    // Geração manual via biblioteca leve em vez de Apache POI (que é pesado)
    // Pode usar templates ou python-docx style
}

class SrtExporter : Exporter<SrtOptions>() {
    // Converte segments → SRT
}
```

**Decisão sobre POI vs alternativas:** Apache POI tem ~25MB de JARs, inflaria APK. Avaliar:
- Geração manual de DOCX (é apenas um ZIP com XMLs).
- Lib mais leve como `kotlinx-docx`.
- Usar `mammoth` (RN side) para geração simples.

**Decisão tentativa:** geração manual de DOCX via templates XML simples. Suficiente para nossos casos.

---

## 9.7. Templates de exportação

### Markdown (default)

```markdown
# Aula de Python — 03/05/2026

**Duração:** 4h 12min
**Tags:** #python #intermediário
**Marcadores:** 3

## Resumo

[resumo gerado pela IA]

## Transcrição

### 20:30:00 — 🟦 Professor
Bom dia pessoal, hoje vamos falar sobre list comprehensions...

### 20:30:24 — 🟧 Aluno A
Professor, a parte de dicionários eu fiquei...

### 20:30:38 — 🟦 Professor
Boa pergunta. Então...

📍 **20:44:23 — Marcador: Início VLOOKUP**

### 20:44:23 — 🟦 Professor
Agora vamos para o tópico principal...

---

## Análise pedagógica

### Tópicos abordados
- Introdução (15min)
- Sintaxe básica (28min)
- ...

### Métricas
- Você falou 71% do tempo
- ...
```

### PDF

Template profissional:
- Capa com nome, data, duração
- Sumário automático
- Transcrição com cores discretas para speakers
- Cabeçalho/rodapé com paginação
- Análises em seção separada
- QR code linkando ao app (futuro)

---

## 9.8. Limpeza automática

```
[Configurações > Storage > Limpeza automática]

Apagar automaticamente:
  ☐ Áudio bruto (WAV) após X dias
      [após processar    ▾]
  ☐ Áudio comprimido após X dias
      [60 dias            ▾]
  ☑ Exports antigos após X dias
      [7 dias             ▾]
  ☐ Aulas inteiras após X dias (perigoso)
      [180 dias           ▾]

[Limpar agora]
```

Rotina: WorkManager job diário, baixa prioridade, segue regras configuradas.

Salvaguardas: nunca apaga aula com tag "permanente", nunca apaga última X aulas independente da idade.

---

## 9.9. Soft delete e lixeira

Quando usuário deleta:
1. Marca `soft_deleted_at = now()`.
2. Some das listas.
3. Disponível em [Configurações > Lixeira] por 30 dias.
4. Após 30 dias, hard delete via job.
5. Botão "esvaziar lixeira" para forçar agora.

---

## 9.10. Importação

**v1.0:** sem importação.
**v1.x:** importar áudio externo (WAV/MP3/M4A) → roda pipeline completo (cleanup + transcrição + diarização + análise).

```
[Tela: Importar áudio]

Selecione arquivo de áudio do seu celular:
  [Escolher arquivo]

Aulas importadas serão processadas igual a gravações nativas: 
limpeza, transcrição, diarização e análise opcional.
```

---

## 9.11. Métricas de storage

Painel em Settings:

```
[Storage usado]
  Total: 2.3 GB

  Por categoria:
    ▓▓▓▓▓▓▓▓▓▓ Áudio bruto     1.4 GB (61%)
    ▓▓▓▓▓ Modelos ML            580 MB (25%)
    ▓▓ Áudio comprimido         180 MB (8%)
    ▓ Exports                    65 MB (3%)
    ▓ Banco                      45 MB (2%)
    Outros                       30 MB (1%)
  
  Por aula (top 5):
    Workshop IA — 6h    →  812 MB
    Python — 4h12      →  482 MB
    Excel — 2h45       →  315 MB
    ...
  
  [Comprimir tudo para Opus]   [Gerenciar lixeira]
```

---

## 9.12. Plano de implementação

| Sprint | Entrega |
|--------|---------|
| Sprint 2 (sem 3) | Schema Room, migrations, DAOs básicos |
| Sprint 2 (sem 3) | FTS5 para busca |
| Sprint 3 (sem 5) | Exportadores: WAV, TXT, MD, JSON |
| Sprint 4 (sem 7) | Exportadores: PDF, SRT, VTT |
| Sprint 5 (sem 8) | Exportador DOCX, ZIP combinado |
| Sprint 11 (sem 14) | Conversão de formato (FFmpeg) — Opus, MP3, FLAC |
| Sprint 15 (sem 18) | Painel de storage, limpeza automática, lixeira |
| v1.x | Backup Google Drive, importação, encryption opcional |
