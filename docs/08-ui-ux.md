# 08 — UI/UX, fluxos e configurações

> Como o app se sente nas mãos do usuário. Telas principais, fluxos críticos, painel de configurações, design system, acessibilidade, dark mode.

---

## 8.1. Filosofia de design

1. **Calma na tela.** Aula é estressante por si só. App não pode parecer "ferramenta complicada". Contagem regressiva e botão grande de "iniciar". Tudo o resto fica no caminho do "depois".
2. **Honestidade visual.** Status reais, não placebos. Se transcrição leva 1h, mostra "1h restantes", não "carregando".
3. **Reversibilidade.** Tudo destrutivo confirma. Lixeira existe. Nada de "tem certeza?" duplo, mas nada de delete instantâneo.
4. **Densidade quando útil.** Lista de aulas é densa (você quer ver muitas). Tela de gravação é minimalista (você quer foco).
5. **Dark mode primeiro.** Aulas costumam ser em luz baixa. Light mode é a alternativa.
6. **Tipografia legível.** Fonte sans-serif clara. Tamanhos generosos. Linha-altura confortável para ler transcrições longas.

---

## 8.2. Mapa de telas

```
┌──────────────────────────────────────────────────────────┐
│ Tab: Início (botão grande "Iniciar gravação")            │
│   - Atalho rápido pra começar                            │
│   - Última aula gravada                                   │
│   - Status: bateria, espaço, modelos baixados            │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Tela: Gravação ativa                                      │
│   - Timer big                                              │
│   - VU meter (nível de áudio)                            │
│   - Lista de marcadores                                   │
│   - Botões: pausar, parar, marcador                      │
│   - Status: chunks salvos, último save                   │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Tab: Biblioteca                                            │
│   - Lista de todas as aulas                               │
│   - Filtros: data, status, tag                           │
│   - Busca em título, transcrição, tags                   │
│   - Cards com: nome, duração, data, status               │
└─────┬────────────────────────────────────────────────────┘
      │ tap em aula
      ▼
┌──────────────────────────────────────────────────────────┐
│ Tela: Detalhe da aula                                      │
│   - Header: nome, data, duração, tags                    │
│   - Player de áudio (com waveform e word-highlight)      │
│   - Tabs: Transcrição | Análise | Métricas | Áudio       │
│   - Ações: editar nome/tags, exportar, deletar           │
└─────┬────────────────────────────────────────────────────┘
      │
      ├─→ Aba Transcrição (viewer com diarização)
      ├─→ Aba Análise (resumo, tópicos, conceitos, etc)
      ├─→ Aba Métricas (dashboards)
      └─→ Aba Áudio (player + chunks + export)

┌──────────────────────────────────────────────────────────┐
│ Tab: Análises (cross-aula)                                 │
│   - Visão de evolução (semanas/meses)                    │
│   - Comparações entre aulas                              │
│   - Heat map de tópicos cobertos                         │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Tab: Configurações                                         │
│   - Geral                                                 │
│   - Áudio e gravação                                      │
│   - Transcrição                                           │
│   - Diarização (vozes)                                    │
│   - IA local                                              │
│   - IA em nuvem                                           │
│   - Storage                                               │
│   - Privacidade                                           │
│   - Avançado                                              │
│   - Sobre / Diagnóstico                                   │
└──────────────────────────────────────────────────────────┘
```

---

## 8.3. Tela de Gravação (a mais importante)

```
┌────────────────────────────────────────┐
│ ← Voltar          Aula de Python ⓘ✏️   │
├────────────────────────────────────────┤
│                                          │
│                                          │
│              01:23:45                    │
│              ●●●●● REC                  │
│                                          │
│                                          │
│   ┌──────────────────────────────┐      │
│   │ ▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮ Nível de áudio │  │
│   └──────────────────────────────┘      │
│                                          │
│   📦 167 chunks salvos                   │
│   💾 Último salvo há 12s                 │
│   🔋 87% • 💿 12.4GB livres              │
│                                          │
│                                          │
│   📍 Marcadores (3)              [+ Marcar]
│   • 0:14:23 — Início VLOOKUP             │
│   • 0:42:11 — Pausa                       │
│   • 1:05:08 — Exercício prático           │
│                                          │
│                                          │
├────────────────────────────────────────┤
│   [⏸ Pausar]              [⏹ Parar]      │
└────────────────────────────────────────┘
```

**Comportamentos:**
- Tela respeita modo "manter ativa" (mas usuário pode bloquear). Gravação continua se bloquear.
- Notificação persistente quando app não em foco (com mesmos botões pausar/parar/marcar).
- Vibração discreta a cada chunk salvo (configurável, default OFF).
- Ao tentar voltar, alerta: "Continuar em background ou parar gravação?"

---

## 8.4. Tela de Início (home)

```
┌────────────────────────────────────────┐
│ AulaLogger                       ⚙️     │
├────────────────────────────────────────┤
│                                          │
│                                          │
│        ╭──────────────────╮              │
│        │                    │              │
│        │       ●            │              │
│        │   INICIAR          │              │
│        │   GRAVAÇÃO         │              │
│        │                    │              │
│        ╰──────────────────╯              │
│                                          │
│                                          │
│  Pronto pra gravar:                      │
│   ✓ Microfone autorizado                  │
│   ✓ Bateria: 87% (~10h)                   │
│   ✓ Armazenamento: 12.4GB livres          │
│   ✓ Modelos de IA: instalados             │
│                                          │
│  Última aula:                            │
│   📚 Python — 02/05 às 19:30 (4h12)      │
│   ✓ Transcrita • ✓ Analisada              │
│   [Abrir]                                 │
│                                          │
└────────────────────────────────────────┘
```

Status verdes/amarelos/vermelhos. Se algo está vermelho (mic não autorizado, sem espaço), botão "Iniciar" desabilitado com mensagem clara.

---

## 8.5. Tela de Biblioteca

```
┌────────────────────────────────────────┐
│ Biblioteca                    🔍   ⊕    │
├────────────────────────────────────────┤
│ [Todas] [Esta semana] [Mês] [Tag: ▾]   │
├────────────────────────────────────────┤
│                                          │
│ Hoje                                     │
│ ┌────────────────────────────────────┐  │
│ │ 🎙 Aula de Python                    │ │
│ │ 03/05 às 20:30 • 4h12 • [analisada]│ │
│ │ #python #intermediário                │ │
│ └────────────────────────────────────┘  │
│                                          │
│ Ontem                                    │
│ ┌────────────────────────────────────┐  │
│ │ 🎙 Aula de Excel                     │ │
│ │ 02/05 às 14:00 • 2h45 • [analisada]│ │
│ │ #excel #avançado                       │ │
│ └────────────────────────────────────┘  │
│                                          │
│ Semana passada                           │
│ ┌────────────────────────────────────┐  │
│ │ 🎙 Workshop IA Generativa            │ │
│ │ 28/04 às 09:00 • 6h00 • [transcrita]│ │
│ │ #ia #workshop                          │ │
│ └────────────────────────────────────┘  │
│                                          │
└────────────────────────────────────────┘
```

**Busca:** full-text na transcrição via SQLite FTS5.

---

## 8.6. Tela de Detalhe da Aula — Aba Transcrição

```
┌────────────────────────────────────────┐
│ ← Aula de Python              ⋯ menu    │
├────────────────────────────────────────┤
│ 03/05/2026 • 4h12min • #python          │
│                                          │
│ [Trans] [Análise] [Métricas] [Áudio]    │
├────────────────────────────────────────┤
│ 🔍 Buscar nesta aula...                  │
├────────────────────────────────────────┤
│                                          │
│ ▶ ━━━━●━━━━━━━━━━━━━━━━━━━━━━ 1:23/4:12│
│                                          │
│ ████ 🟦 Professor      20:30:00          │
│ Bom dia pessoal, hoje vamos falar         │
│ sobre list comprehensions no Python.      │
│ Antes de começar, alguém tem dúvida       │
│ da aula passada?                          │
│                                          │
│ ████ 🟧 Aluno A        20:30:24          │
│ Professor, a parte de dicionários eu     │
│ fiquei meio perdido na diferença entre   │
│ items() e values().                       │
│                                          │
│ ████ 🟦 Professor      20:30:38          │
│ Boa pergunta. Então, items() retorna...  │
│                                          │
│ 📍 Marcador: "Início VLOOKUP" 20:44:23   │
│                                          │
│ ████ 🟦 Professor      20:44:23          │
│ Agora vamos para o tópico principal...   │
│                                          │
└────────────────────────────────────────┘
```

**Interações:**
- Tap no parágrafo → reproduz daquele ponto.
- Long press → menu (copiar texto, marcar, adicionar nota, mudar speaker).
- Word highlight em sincronia com playback (graças aos word-timestamps).
- Modo "leitura corrida" (sem speakers) opcional.
- Modo "comprimido" (parágrafos compactos).

---

## 8.7. Tela de Análise da Aula

Já mostrada em [docs/07](07-ia-analise.md), §7.12.

---

## 8.8. Tela de Métricas

```
┌────────────────────────────────────────┐
│ ← Aula de Python • Métricas               │
├────────────────────────────────────────┤
│                                          │
│ Distribuição de fala                      │
│ ┌──────────────────────────────────┐    │
│ │ Você  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ 71%   │    │
│ │ Aluno A ▓▓▓▓ 12%                 │    │
│ │ Aluno B ▓▓ 5%                     │    │
│ │ Aluno C ▓ 1%                      │    │
│ │ Silêncio ▓▓▓ 11%                 │    │
│ └──────────────────────────────────┘    │
│                                          │
│ Ritmo (palavras/minuto)                   │
│ [gráfico de linha — wpm ao longo da aula]│
│ Média: 142 wpm                            │
│                                          │
│ Maior monólogo seu                        │
│ 7min 23s — entre 1:42:00 e 1:49:23        │
│ [Ir para esse trecho]                     │
│                                          │
│ Perguntas                                 │
│ Você fez: 23 perguntas aos alunos        │
│ Alunos: 15 perguntas no total              │
│ ⚠ 2 não tiveram resposta clara            │
│                                          │
│ Termos técnicos mais mencionados          │
│ • list comprehension (47x)                │
│ • iterable (28x)                          │
│ • function (24x)                          │
│ • dictionary (22x)                        │
│                                          │
└────────────────────────────────────────┘
```

---

## 8.9. Tela de Áudio (player + chunks)

```
┌────────────────────────────────────────┐
│ ← Aula de Python • Áudio                  │
├────────────────────────────────────────┤
│                                          │
│ ┌──────────────────────────────────┐    │
│ │ ███████████████████████████████  │ ← waveform
│ │       ▲ 1:23:45                  │    │
│ └──────────────────────────────────┘    │
│ [⏮ -10s]  [⏯]  [⏭ +10s]   1.0x ▾      │
│                                          │
│ Gravado em: 03/05 20:30                   │
│ Duração: 4h 12min                         │
│ Tamanho: 482MB (WAV) — [Comprimir]       │
│ Chunks: 506 (todos íntegros ✓)           │
│                                          │
│ [Exportar áudio]                         │
│   ○ WAV original (482MB)                  │
│   ○ FLAC lossless (~250MB)                │
│   ○ Opus voz otimizada (~50MB)            │
│   ○ MP3 64kbps (~65MB)                    │
│   [Exportar]                              │
│                                          │
└────────────────────────────────────────┘
```

---

## 8.10. Painel de Configurações (estrutura)

### Geral

- Tema: Auto / Claro / Escuro
- Idioma do app: PT-BR / EN
- Notificações: comportamento, sons
- Permissões: microfone, notificações, ignorar otimização de bateria

### Áudio e Gravação

- Source de mic: Microfone / VOICE_RECOGNITION
- Sample rate: 16000 / 22050 / 44100
- Tamanho do chunk: 10s / 30s / 60s / 120s
- Compressão pós-sessão: WAV / FLAC / Opus / MP3
- Limpeza automática: ON/OFF

### Transcrição

- Modelo Whisper: Tiny / Base / Small / Medium / Large
- Quantização: Q4 / Q5 / Q8 / FP16
- Idioma: Auto / PT / EN / ...
- Word-level timestamps: ON/OFF
- Vocabulário customizado: [editor]
- Pós-processamento: pontuação, capitalização, números

### Diarização (vozes)

- Diarização: ON/OFF
- Cadastrar minha voz (enrollment): [Iniciar]
- Vozes conhecidas: [lista, gerenciar]
- Threshold de identificação: [slider 0.5–0.9]

### IA local

- Modelo LLM: Gemma 2B / Phi-3 / Llama 3.2 1B / Desabilitado
- Análises automáticas pós-aula: [checkboxes]
- Análises em tempo real (alertas): [config detalhado]

### IA em nuvem

- Provedores: Claude / OpenAI / Gemini
- API keys: [criptografadas]
- Quais análises usam cloud
- Limite de gasto mensal opcional

### Storage

- Localização dos arquivos
- Espaço usado por categoria
- Limpeza automática: chunks de áudio antigos, exports
- Backup: nenhum / Google Drive (futuro) / pasta local

### Privacidade

- Lista do que sai e do que não sai do device
- Apagar tudo (factory reset do app)
- Logs: visualizar, exportar, limpar

### Avançado

- Threads para Whisper: [auto / 2 / 4 / 8]
- NNAPI delegate: ON/OFF
- Buffers de gravação: [tamanho]
- Modo experimental: [features beta]

### Sobre / Diagnóstico

- Versão do app, build, commit
- Modelos instalados, versões
- Espaço, memória, OS
- Botão: Exportar diagnóstico (ZIP de logs sem áudio/texto)
- Licença, créditos open-source

---

## 8.11. Onboarding (primeira execução)

Sequência:

1. **Boas-vindas** — uma frase, um botão "Vamos começar".
2. **Permissão de microfone** — explicação + pedido.
3. **Permissão de notificação** (Android 13+) — explicação + pedido.
4. **Battery optimization** — explicação ("para não interromper sua aula") + pedido.
5. **Modelo Whisper** — escolher e baixar (com opção "decidir depois").
6. **Cadastro de voz** (opcional, pode pular) — para diarização automática.
7. **Pronto** — botão "Iniciar primeira gravação".

Quanto mais user-friendly, melhor. Cada tela = uma decisão clara, com "pular" sempre disponível.

---

## 8.12. Dark mode

Default. Paleta proposta (a finalizar com identidade visual):

```
--bg-primary:    #0a0a0a
--bg-secondary:  #1a1a1a
--bg-card:       #232323
--text-primary:  #ffffff
--text-secondary: #b0b0b0
--text-muted:    #707070
--accent:        #6366f1   /* indigo — calmo, técnico */
--accent-hover:  #818cf8
--success:       #10b981
--warning:       #f59e0b
--danger:        #ef4444
--speaker-1:     #6366f1   /* professor */
--speaker-2:     #f97316
--speaker-3:     #10b981
--speaker-4:     #ec4899
--speaker-5:     #06b6d4
```

---

## 8.13. Acessibilidade (WCAG AA)

- Contraste 4.5:1 mínimo para texto, 3:1 para UI elementos.
- Todos os botões têm `accessibilityLabel` significativo.
- Tamanho mínimo de touch target: 48×48dp.
- Suporte a TalkBack: navegação completa.
- Suporte a fonte ampliada do sistema.
- Sem dependência apenas de cor (cores de speaker têm também ícone/iniciais).
- Modo "alto contraste" opcional.

---

## 8.14. Componentes-chave a desenvolver

| Componente | Responsabilidade |
|------------|-------------------|
| `RecordButton` | Botão grande de iniciar/parar com estados visuais |
| `WaveformPlayer` | Player de áudio com waveform e word highlight |
| `TranscriptViewer` | Lista virtualizada com diarização, busca, edição |
| `MarkerTimeline` | Timeline horizontal com marcadores e tópicos |
| `MetricsCard` | Cards de métrica com gráficos pequenos |
| `SpeakerAvatar` | Avatar colorido com iniciais por speaker |
| `StatusBadge` | Pendente / Em processamento / Pronto |
| `EmptyState` | Variações para "sem aulas", "sem busca", etc |

---

## 8.15. Plano de implementação (UI)

UI é desenvolvida em paralelo com cada feature. Mas:

| Sprint | Entrega de UI |
|--------|----------------|
| Sprint 1–2 | Setup design system, tema dark, componentes base |
| Sprint 3 | Tela de Início, Biblioteca lista vazia |
| Sprint 4 | Tela de Gravação ativa |
| Sprint 5 | Detalhe de aula — Aba Áudio + player |
| Sprint 9–10 | Detalhe de aula — Aba Transcrição |
| Sprint 13–14 | Diarização na transcrição, edição |
| Sprint 17–18 | Aba Análise + Aba Métricas |
| Sprint 19+ | Refinamento, polimento, animações |
