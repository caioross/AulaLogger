# AulaLogger — Plano Definitivo de Desenvolvimento

> Aplicativo Android (com site/landing page) para gravação confiável de aulas longas (4h+), transcrição de altíssima qualidade com diarização (separação de vozes) e análise pedagógica assistida por IA, projetado para ser flexível a qualquer matéria, instrutor ou contexto.

**Versão do plano:** 1.0
**Data:** 03 de maio de 2026
**Autor do plano:** Claude (com Caio)
**Status:** ✅ Plano completo, aguardando decisões pendentes (ver §6) antes de iniciar implementação

---

## Sumário

1. [Sumário executivo](#1-sumário-executivo)
2. [Decisões já tomadas](#2-decisões-já-tomadas)
3. [Princípios não-negociáveis](#3-princípios-não-negociáveis)
4. [Visão geral da arquitetura](#4-visão-geral-da-arquitetura)
5. [Roadmap em uma página](#5-roadmap-em-uma-página)
6. [Decisões pendentes (precisam da sua resposta)](#6-decisões-pendentes-precisam-da-sua-resposta)
7. [Como navegar este plano](#7-como-navegar-este-plano)
8. [Próximos passos imediatos](#8-próximos-passos-imediatos)

---

## 1. Sumário executivo

### O que vamos construir

**AulaLogger** (nome provisório — você decide, ver §6) é um aplicativo Android para professores e instrutores que precisam:

- Gravar aulas longas (4 horas ou mais) com **garantia de zero perda** mesmo se a tela apagar, o sistema tentar matar o app, a bateria estiver baixa ou houver crash do processo principal.
- Obter **transcrições hiper-detalhadas** com identificação de quem falou (você vs. alunos), timestamps precisos, e tratamento adequado do português brasileiro técnico.
- Receber **análise inteligente** da própria aula via IA local (privacidade total) e, opcionalmente, via IA em nuvem (análise mais profunda) — incluindo resumos, identificação de tópicos, métricas de comportamento docente, alertas e sugestões.
- Trabalhar **offline por padrão**, **flexível ao conteúdo** (não amarrado a Excel, Python, IA ou qualquer matéria específica).
- Ter um **site simples** apresentando o app e permitindo download direto, com documentação.

### Por que esse projeto é tecnicamente desafiador (e por que vale a pena planejar bem)

Três coisas tornam esse app muito mais complicado do que parece à primeira vista:

1. **Gravação contínua de 4h+ sem falhar é um problema sério no Android moderno.** Doze mode, App Standby, fabricantes que matam apps em background (Xiaomi, Samsung, Huawei são notórios), restrições de foreground service do Android 14+, gerenciamento de memória durante grandes buffers de áudio — tudo conspira contra um app de gravação longa. **Vamos resolver isso com arquitetura específica** (foreground service nativo Android + chunking + write-as-you-go + recovery).

2. **Transcrição on-device de português com diarização é um problema computacionalmente caro.** Whisper roda em celulares modernos, mas um celular médio leva 0,3x–0,8x do tempo real para transcrever (aula de 4h leva 1,3h–3,2h para transcrever). Diarização adiciona mais 20–40% de tempo. **Vamos resolver com modelo certo + execução em background + UX que comunica progresso.**

3. **IA on-device para análise pedagógica em tempo real é fronteira da arte.** LLMs locais quantizados (Gemma 2B, Phi-3 mini) cabem em celulares de 8GB+ RAM mas precisam de prompts e infraestrutura cuidadosos. **Vamos resolver com pipeline híbrido**: análise leve em tempo real (regras + LLM pequeno) + análise profunda opcional pós-aula (LLM maior local OU cloud).

### Premissas centrais

- **Stack:** React Native + Expo Modules API, com módulo nativo Android escrito em Kotlin para a parte crítica de gravação. Você escolheu RN; eu vou ser explícito sobre os riscos e mitigá-los com arquitetura. Detalhes em [docs/02-arquitetura-tecnica.md](docs/02-arquitetura-tecnica.md).
- **Distribuição:** APK direto pelo site + GitHub Releases + F-Droid (eventualmente). Sem Google Play na v1.
- **Open-source:** F-Droid exige código aberto. Recomendo MIT ou GPL-3.0. Tem implicações que cobrimos em [docs/11](docs/11-distribuicao-cicd-open-source.md).
- **Privacidade by default:** áudio nunca sai do dispositivo a menos que você explicitamente autorize.
- **Escopo v1 = "completo"**, conforme sua escolha. Mas **fasearei a entrega em 3 releases internas** (v1.0, v1.1, v1.2) para permitir feedback iterativo. Cronograma realista no [docs/12](docs/12-roadmap-riscos-decisoes-pendentes.md).

### Estimativa realista de esforço

Para um desenvolvedor sênior dedicado em tempo integral, com domínio em React Native + Android nativo + ML on-device:

| Fase | Escopo | Tempo |
|------|--------|-------|
| **Fundação** | Setup, módulo nativo de gravação, persistência, UI base | 4–5 semanas |
| **Transcrição** | Whisper.cpp integrado, pipeline de áudio, exportação | 3–4 semanas |
| **Diarização** | Speaker enrollment, atribuição, UI da timeline | 3 semanas |
| **IA local** | LLM on-device, análise pedagógica, alertas | 4–5 semanas |
| **IA cloud opcional** | Integração Claude/OpenAI, gestão de chaves, billing | 1–2 semanas |
| **Site + docs** | Landing page, docs, página de download, CI/CD de release | 2 semanas |
| **Polimento + QA** | Testes de stress 4h+, bugs, telas de erro, acessibilidade | 3–4 semanas |
| **Total** | | **20–25 semanas** (~5–6 meses tempo integral) |

Em part-time (10h/semana), aproximadamente **18 meses**. Vou propor marcos intermediários úteis em si mesmos para você não esperar 18 meses para usar nada.

---

## 2. Decisões já tomadas

Estas decisões saíram da nossa conversa inicial e ficam **travadas** a menos que você reabra:

| # | Decisão | Escolha | Implicação |
|---|---------|---------|------------|
| D1 | Stack do app | **React Native + Expo** | UI rápida de desenvolver. Custo: gravação longa exige módulo nativo Android customizado em Kotlin. Cobrimos isso em [docs/03](docs/03-subsistema-gravacao.md). |
| D2 | Estratégia de IA | **On-device para gravação/transcrição/diarização + cloud opcional só para análise profunda** | Privacidade do áudio dos alunos preservada. Análise rica disponível para quem quiser. Cobrimos em [docs/07](docs/07-ia-analise.md). |
| D3 | Distribuição | **APK direto pelo site + F-Droid + GitHub Releases** | Sem Google Play na v1. Sem revisão da Google, mas usuário precisa habilitar "fontes desconhecidas". F-Droid exige open-source. |
| D4 | Escopo da v1 | **Visão completa** (gravação + limpeza + transcrição + diarização + análise IA + alertas + insights) | Investimento maior, mas resultado entregue à altura da visão. Faseado internamente em v1.0, v1.1, v1.2. |
| D5 | Pasta-raiz do projeto | **`E:\AulaLogger`** | Toda a documentação e (depois) o código vivem aqui. |

---

## 3. Princípios não-negociáveis

Estes são os princípios que **toda decisão técnica** vai respeitar. Se algum deles for violado, o trade-off precisa ser explícito e aprovado.

1. **Confiabilidade absoluta da gravação.** Se o usuário aperta "iniciar", o áudio é capturado até ele apertar "parar" — ponto. Nenhuma otimização, feature ou refator pode ameaçar isso. Testaremos com sessões reais de 4h+ antes de cada release.

2. **Privacidade por padrão.** Áudio bruto nunca sai do dispositivo sem ação explícita do usuário. Transcrições idem. Cloud é opt-in, não opt-out.

3. **Funciona offline.** Toda a funcionalidade core (gravar, transcrever, diarizar, analisar com IA local) funciona com o aparelho em modo avião.

4. **Flexível ao conteúdo.** Nada no design assume que a aula é sobre Excel, Python ou IA. O app deve funcionar igualmente bem para um instrutor de yoga, um professor de história ou um palestrante.

5. **Recuperável a falhas.** Se o app crash no meio de uma gravação de 3h, na próxima abertura o usuário recupera tudo o que foi gravado até o momento do crash, sem perder nada.

6. **UI reversível e segura.** Ações destrutivas (deletar aula, apagar transcrição) sempre confirmam. Backup local automático antes de qualquer destruição.

7. **Performance honesta.** Se uma operação leva 1h, dizemos "leva 1h" e mostramos progresso real, não "carregando..." indefinido.

8. **Configurável, mas com defaults sensatos.** Usuário avançado pode mexer em tudo. Usuário casual abre o app e funciona.

---

## 4. Visão geral da arquitetura

```
┌─────────────────────────────────────────────────────────────────────┐
│                          AulaLogger (Android)                       │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                  React Native UI (Expo)                       │   │
│  │   Telas, navegação, configurações, viewer de transcrição     │   │
│  └────────────────────────┬─────────────────────────────────────┘   │
│                           │ Expo Modules (TypeScript ↔ Kotlin)      │
│  ┌────────────────────────┴─────────────────────────────────────┐   │
│  │            Módulo Nativo Android (Kotlin)                     │   │
│  │  ┌─────────────────────────────────────────────────────┐     │   │
│  │  │ RecordingService (Foreground Service, persistente)   │     │   │
│  │  │  - AudioRecord PCM 16-bit @ 16kHz mono              │     │   │
│  │  │  - Wake locks, partial wake locks                    │     │   │
│  │  │  - Chunking 30s → arquivos .wav incrementais         │     │   │
│  │  │  - Write-ahead, fsync periódico                      │     │   │
│  │  │  - Recovery em caso de crash                         │     │   │
│  │  └─────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────┐     │   │
│  │  │ AudioPipeline                                         │     │   │
│  │  │  - RNNoise (denoise) | normalização | VAD            │     │   │
│  │  └─────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────┐     │   │
│  │  │ TranscriptionEngine (whisper.cpp via JNI)            │     │   │
│  │  │  - Whisper small/medium quantizado (Q5_K_M)          │     │   │
│  │  │  - NNAPI/GPU delegate quando disponível              │     │   │
│  │  └─────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────┐     │   │
│  │  │ DiarizationEngine (sherpa-onnx)                       │     │   │
│  │  │  - Pyannote VAD + segmentação + embedding             │     │   │
│  │  │  - Speaker fingerprint do professor (enrollment)      │     │   │
│  │  └─────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────┐     │   │
│  │  │ LocalLLMEngine (llama.cpp via JNI)                   │     │   │
│  │  │  - Gemma 2 2B Q4 ou Phi-3 mini Q4                    │     │   │
│  │  │  - Análise pós-aula, resumos, insights               │     │   │
│  │  └─────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────┐     │   │
│  │  │ Storage (SQLite + arquivos)                          │     │   │
│  │  │  - Aulas, segmentos, transcrições, análises           │     │   │
│  │  │  - Encrypted at rest (Android Keystore)              │     │   │
│  │  └─────────────────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                           │                                           │
│                           │ (opcional, opt-in)                       │
│                           ▼                                           │
│              ┌──────────────────────────────┐                        │
│              │ Cloud LLM (Claude/OpenAI/Gemini) │                    │
│              │  - Análise profunda (texto only)  │                   │
│              │  - Nunca recebe áudio              │                   │
│              └──────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
```

**Detalhes em [docs/02-arquitetura-tecnica.md](docs/02-arquitetura-tecnica.md).**

---

## 5. Roadmap em uma página

```
v1.0 — "Gravação Confiável"                        [semanas 1–8]
├─ Fundação: setup RN + Expo + módulo nativo Kotlin
├─ Foreground service de gravação 4h+ (rock solid)
├─ Storage local (SQLite + WAV chunks)
├─ UI mínima: gravar, listar aulas, reproduzir, exportar áudio
└─ Site v1: landing simples + página de download
   ✅ Já é útil para você: substitui qualquer gravador de voz

v1.1 — "Transcrição Hiper-Detalhada"               [semanas 9–14]
├─ Pipeline de áudio (RNNoise + normalização + VAD)
├─ Whisper.cpp integrado, transcrição em background
├─ Viewer de transcrição com timestamps
├─ Exportação: PDF, DOCX, SRT, VTT, JSON
└─ Site v2: docs do recurso de transcrição
   ✅ Substitui qualquer serviço de transcrição online pago

v1.2 — "Sabe Quem Falou"                           [semanas 15–18]
├─ Diarização via sherpa-onnx
├─ Speaker enrollment (cadastro da sua voz)
├─ Atribuição automática "Professor / Aluno A / Aluno B"
├─ UI: cores por speaker, edição de atribuições
└─ Site v3: tutorial de diarização
   ✅ Diferencial competitivo real

v1.3 — "Analista de IA"                            [semanas 19–24]
├─ LLM local (Gemma 2B / Phi-3 mini)
├─ Análise pós-aula: resumos, tópicos, insights pedagógicos
├─ Alertas em tempo real (silêncios longos, ritmo, jargão)
├─ Integração cloud opcional (Claude/OpenAI/Gemini)
└─ Site v4: docs completas + comparação com alternativas
   ✅ Visão completa do produto

v2.0 (futuro) — multi-idioma, web companion, plugins, etc.
```

**Detalhamento sprint-a-sprint em [docs/12-roadmap-riscos-decisoes-pendentes.md](docs/12-roadmap-riscos-decisoes-pendentes.md).**

---

## 6. Decisões pendentes (precisam da sua resposta)

Estas decisões eu **não** travei sozinho. Vou listar a recomendação e a alternativa, e você responde quando puder. Implementação só inicia depois das essenciais (marcadas com 🔴):

### 🔴 Críticas (precisam ser respondidas antes do código)

- **P1. Nome do app.** Sugestões: `AulaLogger`, `AulaScribe`, `AulaIA`, `Salaverbi`, `LectureLab`, `EnsinIA`. Qual prefere? Outro?
- **P2. Licença open-source.** Recomendo **GPL-3.0** (força quem fizer fork a manter open-source) ou **MIT** (mais permissiva, mais adoção). Qual?
- **P3. Provedor de IA em nuvem para análise profunda opcional.** Recomendo suportar **múltiplos** com escolha do usuário (Claude, OpenAI GPT, Google Gemini), começando por **Claude** (preferência sua, e a melhor para análise textual longa). Ok?
- **P4. Hardware-alvo mínimo.** Recomendo: Android 10+ (API 29+), 4GB RAM mínimo (transcrição funciona, análise IA local exige 6GB+). Modelos Whisper escalam por classe de hardware. Ok?

### 🟡 Importantes (podem ser respondidas no início da fase relevante)

- **P5. Backup/sync.** Apenas local? Opcional Google Drive? Servidor próprio? Recomendo: **local-only no v1**, opcional Google Drive no v1.2.
- **P6. Identidade visual.** Logo, paleta de cores, fonte. Recomendo: paleta dark-friendly (necessária pra usar durante aulas), tom acadêmico-moderno. Quer contratar designer ou usar IA para gerar mockups?
- **P7. Domínio do site.** Sugestões: `aulalogger.com.br`, `aulalogger.app`, `aula.tools`. Quer que eu cheque disponibilidade?
- **P8. Hospedagem do site.** Recomendo: **Cloudflare Pages** ou **Vercel** (free tier, deploy via GitHub). Ok?

### 🟢 Confortáveis (podem ser respondidas a qualquer momento)

- **P9. Idiomas suportados na v1.** Português apenas? Português + inglês? Recomendo PT-BR primeiro, EN como segundo no v1.3.
- **P10. Telemetria/analytics.** Recomendo: **zero telemetria**, alinhado com o princípio de privacidade. Concorda?
- **P11. Monetização.** Gratuito sempre? Doação opcional? Pro tier (ex: cloud sync)? Recomendo: gratuito + doação no GitHub Sponsors no v1, sem mexer em monetização real até v2.
- **P12. Comunidade.** Vai abrir Discord/Telegram/Discussions do GitHub? Recomendo: GitHub Discussions no v1.

---

## 7. Como navegar este plano

A pasta `docs/` contém **12 documentos técnicos detalhados**, cada um aprofundando uma área. Pense neste arquivo como o "índice executivo" e os `docs/` como o "manual técnico".

| # | Arquivo | Quando ler |
|---|---------|------------|
| 01 | [Visão de produto, personas, princípios](docs/01-visao-produto.md) | Antes de qualquer coisa, para alinhar "por que" |
| 02 | [Arquitetura técnica geral](docs/02-arquitetura-tecnica.md) | Antes de iniciar qualquer código |
| 03 | [Subsistema de gravação](docs/03-subsistema-gravacao.md) | Sprint 1 — é o maior risco técnico |
| 04 | [Pipeline de áudio (limpeza)](docs/04-pipeline-audio.md) | Sprint 4 — junto com transcrição |
| 05 | [Transcrição on-device (Whisper)](docs/05-transcricao-whisper.md) | Sprint 5–7 |
| 06 | [Diarização (separar vozes)](docs/06-diarizacao.md) | Sprint 8–10 |
| 07 | [IA de análise (local + cloud)](docs/07-ia-analise.md) | Sprint 11–14 |
| 08 | [UI/UX, fluxos, configurações](docs/08-ui-ux.md) | Sprint 1 (UI base) e contínuo |
| 09 | [Storage e exportação](docs/09-storage-exportacao.md) | Sprint 2 |
| 10 | [Site/landing page e docs](docs/10-site-landing.md) | Sprint 3 (paralelo) |
| 11 | [Distribuição, CI/CD, open-source](docs/11-distribuicao-cicd-open-source.md) | Antes do primeiro release público |
| 12 | [Roadmap, riscos, decisões pendentes](docs/12-roadmap-riscos-decisoes-pendentes.md) | Toda semana, para tracking |
| 13 | [Segurança, testes e conformidade legal (LGPD)](docs/13-seguranca-testes-conformidade.md) | Sprint 1 e antes de release público |
| 14 | [Começar agora: o que fazer na primeira semana](docs/14-comecar-agora.md) | Quando você for sair do plano e começar a codar |

---

## 8. Próximos passos imediatos

Em ordem, depois que você responder as decisões 🔴 críticas (§6):

1. **Eu (Claude) crio o esqueleto inicial do projeto** em `E:\AulaLogger/app/` — Expo bootstrap, módulo nativo Android stub, README, licença, .gitignore, CI básico.
2. **Você revisa o esqueleto** e decide se quer eu ou você no driver para o sprint 1.
3. **Sprint 1 começa:** prova de conceito do foreground service de gravação. Meta: aplicar gravando 4h continuamente em um celular real, sem perda de áudio. Esse é o **maior risco técnico** — se isso não funcionar, o resto do plano é irrelevante. Por isso vai primeiro.
4. **Em paralelo, sprint do site v1** (landing simples + página "em breve" + email de notificação).

Tudo isso está escalonado em [docs/12-roadmap-riscos-decisoes-pendentes.md](docs/12-roadmap-riscos-decisoes-pendentes.md).

---

> **Lembrete final:** este plano foi escrito para ser **vivo**. Cada documento `docs/*.md` será atualizado conforme decisões mudarem ou aprendermos coisas novas. Se algo aqui ficar desatualizado, vale corrigir antes de seguir construindo em cima. Plano ruim seguido fielmente é pior que plano bom revisado.
