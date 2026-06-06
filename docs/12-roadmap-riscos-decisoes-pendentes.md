# 12 — Roadmap, riscos e decisões pendentes

> Cronograma realista sprint-a-sprint, matriz consolidada de riscos com mitigações, e a lista das perguntas pendentes que precisam da sua resposta.

---

## 12.1. Roadmap detalhado

### Fase 0 — Preparação (semana 0, antes de codar)

- [ ] Você responde decisões 🔴 críticas (P1–P4 em §12.4).
- [ ] Definir nome final do app (P1).
- [ ] Comprar domínio (P7).
- [ ] Criar repositório no GitHub.
- [ ] Definir licença open-source (P2).
- [ ] Configurar conta de provedor de IA escolhido (P3) — para testes.
- [ ] Comprar/configurar 2–3 celulares de teste (já tens, ou empréstimo).

### Fase 1 — Fundação + Gravação Confiável (v1.0) — semanas 1–8

#### Sprint 1 (semanas 1–2): Setup + Hello World
**Meta:** projeto compilando, app vazio rodando em celular real.

- Setup repo + estrutura de pastas + README + LICENSE + CI básico
- Setup Expo + RN + TypeScript + lint + prettier + husky
- Setup módulo nativo `aulalogger-native` (Expo Modules API)
- App "Hello World" rodando: tela única que mostra "AulaLogger v0.0.1"
- AudioRecord básico: gravar 10s e salvar 1 WAV
- Setup Astro site, "em breve" landing
- Workflow CI: lint, typecheck, build APK debug

**Entregável demo:** APK que abre e grava 10s.

#### Sprint 2 (semanas 3–4): Foreground Service
**Meta:** gravar 30min em background, com foreground service e chunking.

- RecordingService completo (foreground)
- ChunkWriter com WAV header, fsync, atomic rename
- Manifest meta.json
- Wake lock partial
- Notificação persistente
- Tela de gravação básica (tempo, parar)
- Persistência em Room: tabela `sessions`, primeiros DAOs

**Entregável demo:** gravar 30min real com tela apagada.

#### Sprint 3 (semanas 5–6): Robustez
**Meta:** gravar 4h sem falhar, com recovery em caso de crash.

- Battery optimization request
- Doc/UX para fabricantes agressivos (Xiaomi/Samsung)
- HealthMonitor (espaço, bateria, atrasos)
- Recovery de sessões interrompidas
- Markers em tempo real
- Pausar/retomar
- Edge cases (call, focus loss, BT disconnect)
- Tela de Início (home) com status verdes
- Biblioteca v1 (lista de aulas)

**Entregável demo:** gravar 2h real com simulação de problemas.

#### Sprint 4 (semana 7): Player + Storage
**Meta:** ouvir o que foi gravado, exportar áudio.

- Player de áudio com waveform (concatena chunks ou usa WAV unificado)
- Detalhe de aula (Aba Áudio)
- Exportar áudio: WAV original
- Soft delete + lixeira

**Entregável demo:** gravar, listar, ouvir, exportar.

#### Sprint 5 (semana 8): Polimento v1.0 + Site v1
**Meta:** lançar v1.0 (gravação confiável).

- Onboarding completo (permissões, battery, modelo)
- Configurações básicas
- Site v1 completo: landing, download, política de privacidade
- Workflow de release assinado
- Stress test 4h em 3 celulares diferentes
- 🚀 **Release v1.0.0**

**Entregável:** APK v1.0 publicado, site no ar.

---

### Fase 2 — Transcrição (v1.1) — semanas 9–14

#### Sprint 6 (semana 9): Pipeline de áudio
- DC offset, high-pass, normalize, limiter (puro Kotlin)
- RNNoise via JNI
- Configurações de áudio
- Compressão pós-sessão (FLAC, Opus, MP3 via FFmpeg-Android)

#### Sprint 7 (semana 10): VAD + Whisper.cpp setup
- Silero VAD via ONNX Runtime
- Build whisper.cpp Android (CMakeLists, JNI)
- Carregamento de modelo
- Transcrever 1 chunk de 30s end-to-end

#### Sprint 8 (semana 11): Pipeline de transcrição completo
- Loop transcrição com chunking + overlap
- Word-level timestamps
- Persistência (transcript_segments + FTS5)
- Progresso em tempo real
- WorkManager para background

#### Sprint 9 (semana 12): Vocabulário + qualidade
- Vocabulário customizado (initial_prompt)
- Pós-processamento (substituições, pontuação)
- Gerenciamento de modelos (download, troca)

#### Sprint 10 (semana 13): UI de transcrição
- Viewer com timestamps
- Word highlight em sincronia com player
- Busca em transcrição (FTS5)
- Edição manual de segmentos

#### Sprint 11 (semana 14): Export + Site v2
- Exportar TXT, MD, PDF, DOCX, SRT, VTT, JSON
- Site v2 com docs de transcrição
- Stress test transcrição 4h
- 🚀 **Release v1.1.0**

---

### Fase 3 — Diarização (v1.2) — semanas 15–18

#### Sprint 12 (semana 15): Sherpa-onnx setup
- Build ou integrar sherpa-onnx Android
- Pipeline VAD + Segmentation + Embedding + Clustering
- Diarizar áudio de teste, retornar speakers anônimos

#### Sprint 13 (semana 16): Speaker enrollment
- Tela de enrollment (3 frases)
- Geração e persistência de voice fingerprint
- Identificação automática do professor

#### Sprint 14 (semana 17): Merge + UI
- Merge transcript + diarização
- UI viewer com cores por speaker
- Edição de atribuições, renomeação, merge speakers

#### Sprint 15 (semana 18): Polimento v1.2 + Site v3
- Métricas de DER, tuning de threshold
- Site v3: docs de diarização, tutorial enrollment
- Stress test diarização
- 🚀 **Release v1.2.0**

---

### Fase 4 — Análise IA (v1.3) — semanas 19–24

#### Sprint 16 (semana 19): LLM local setup
- Build llama.cpp Android, JNI
- Carregar Gemma 2B Q4
- Map-reduce para context longo
- Prompts v1 + validação Zod

#### Sprint 17 (semana 20): Análises pós-aula automáticas
- Resumo executivo
- Tópicos abordados
- Glossário
- Perguntas dos alunos
- Conceitos explicados
- Métricas pedagógicas (rule-based)

#### Sprint 18 (semana 21): UI de análise
- Aba Análise no detalhe da aula
- Aba Métricas com dashboards
- Cards de resumo, tópicos, glossário, etc

#### Sprint 19 (semana 22): IA em nuvem opcional
- Cliente Claude / OpenAI / Gemini
- Tela de configuração de API keys (encrypted)
- Análises sob demanda (plano reverso, material estudo, comparação)
- Estimativa de custo

#### Sprint 20 (semana 23): Análise em tempo real
- AlertEngine: regras + LLM tiny
- Vibração, notificação, log de alertas
- Configuração de alertas

#### Sprint 21 (semana 24): Polimento v1.3 + Site v4
- Cross-aula: dashboard de evolução, comparações
- Site v4: docs completas
- Stress test análise IA
- 🚀 **Release v1.3.0** — visão completa

---

### Fase 5 — v2.0 (futuro, após validação)

Possibilidades:
- Companion web (read-only)
- Multi-idioma de UI (EN, ES)
- Backup Google Drive
- Plugins/extensões
- Marketplace de prompts customizados
- Modo "aluno" (gravar sua participação na aula de outra pessoa)

---

## 12.2. Cronograma visual

```
Semana:  1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  21  22  23  24
v1.0:    ████████████████████████████████
v1.1:                                    ████████████████████████
v1.2:                                                            ████████████████
v1.3:                                                                            ████████████████████████
Site:    ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██
                                                                                                          
Marcos:                                  🚀v1.0                  🚀v1.1          🚀v1.2                  🚀v1.3
```

**Observação:** este é cronograma para 1 dev sênior em **tempo integral**. Em part-time (10h/semana), multiplique por ~4. Em equipe de 2 devs, divida por ~1.5.

---

## 12.3. Matriz consolidada de riscos

| # | Risco | Probabilidade | Impacto | Mitigação | Responsável |
|---|-------|---------------|---------|-----------|-------------|
| R1 | Foreground service ser killed por OEM agressivo (Xiaomi, Samsung) | Alta | Catastrófico | Documentação clara no onboarding, deeplinks pra settings, recovery robusto | Sprint 3 |
| R2 | Crash do whisper.cpp via JNI (memory issue) | Média | Alto | Catch nativo, marcar transcrição como failed, retry com modelo menor | Sprint 8 |
| R3 | Modelos ML grandes (1GB+) — usuário não baixa | Média | Médio | Modelo "Padrão" inicial pequeno (200MB), upgrade opcional | Sprint 9 |
| R4 | RN bridge instável em sessões longas | Baixa | Médio | Toda lógica crítica em nativo, RN só decora | Sprint 1 |
| R5 | Bateria consumida demais em 4h | Média | Alto | Wake lock parcial (não full), CPU otimizada, benchmark obrigatório | Sprint 5 |
| R6 | Whisper qualidade ruim em PT-BR técnico | Média | Médio | Vocabulário customizado, modelo Medium, pós-processamento | Sprint 9 |
| R7 | Diarização ruim em ambiente real (alunos longe) | Alta | Médio | Documentar limitações, edição manual fácil, threshold ajustável | Sprint 15 |
| R8 | LLM local lento em celular médio | Alta | Baixo | Modelo escala (Llama 1B vs Gemma 2B), expectativa clara, run in background | Sprint 16 |
| R9 | F-Droid rejeitar app por modelos baixados | Média | Baixo | Variant "tudo embutido" (1.2GB) específica para F-Droid | Sprint 15 |
| R10 | Custos cloud LLM surpreenderem usuário | Baixa | Médio | Estimativa pré-execução, limite mensal opcional | Sprint 19 |
| R11 | Schema do banco precisar migração quebrada | Baixa | Alto | Testes de migração, sempre incremental, backup pré-migração | Contínuo |
| R12 | Permissões Android restritas demais em versões futuras | Baixa | Médio | Acompanhar release notes Android, adaptar | Contínuo |
| R13 | Whisper.cpp/llama.cpp/sherpa abandonados | Muito baixa | Médio | Vendoring local, capacidade de fork | Contínuo |
| R14 | Dependência de keystore single-point-of-failure | Média | Catastrófico | Backup em 2 lugares offline, encrypted | Sprint 5 |
| R15 | Você queimar com escopo grande demais | Alta | Catastrófico | Faseamento em 4 releases utilizáveis, cada uma já entrega valor | Contínuo |
| R16 | Falta de feedback de usuários reais | Alta | Médio | Beta privado a partir de v1.0, comunidade GitHub Discussions | Sprint 5 |
| R17 | Bug crítico em produção pós-release | Média | Alto | Hotfix workflow rápido, versão anterior sempre disponível | Contínuo |
| R18 | API keys cloud LLM vazarem (logs, screenshots) | Baixa | Alto | EncryptedSharedPreferences, redação em logs, FLAG_SECURE em telas sensíveis | Sprint 19 |
| R19 | Storage encher silenciosamente em 4h+ de gravação | Média | Alto | HealthMonitor (já em §3), parada limpa < 50MB | Sprint 3 |
| R20 | Áudio gravado clipping ou silente sem usuário notar | Média | Alto | VU meter visível, alerta auto se RMS médio < threshold | Sprint 3 |

---

## 12.4. Decisões pendentes — lista consolidada

Cópia da §6 do plano principal, para fácil referência.

### 🔴 Críticas — antes de começar a codar

| # | Pergunta | Recomendação | Sua resposta |
|---|----------|---------------|---------------|
| P1 | Nome do app? | `AulaLogger` (provisório) | _____ |
| P2 | Licença open-source? | GPL-3.0 (forks abertos) ou MIT (max adoção) | _____ |
| P3 | Provedor cloud LLM principal? | Claude primeiro, suportar 3 (Claude/OpenAI/Gemini) | _____ |
| P4 | Hardware mínimo? | Android 10+, 4GB RAM | _____ |

### 🟡 Importantes — podem esperar fase relevante

| # | Pergunta | Recomendação | Quando responder |
|---|----------|---------------|--------------------|
| P5 | Backup/sync? | Local-only v1.0, opcional v1.2 | Sprint 11 |
| P6 | Identidade visual (logo, paleta)? | Indigo escuro, minimalista | Sprint 5 |
| P7 | Domínio? | `aulalogger.com.br` ou `.app` | Sprint 1 |
| P8 | Hospedagem site? | Cloudflare Pages | Sprint 1 |

### 🟢 Confortáveis

| # | Pergunta | Recomendação | Quando responder |
|---|----------|---------------|--------------------|
| P9 | Idiomas v1? | PT-BR primeiro, EN no v1.3 | Antes de v1.3 |
| P10 | Telemetria? | Zero | (já decidido no doc) |
| P11 | Monetização? | Doação opcional v1, sem cobrança | Antes de v2 |
| P12 | Comunidade? | GitHub Discussions v1 | Antes do release v1 |

---

## 12.5. Critérios de "pronto pra release" por versão

### v1.0 (Gravação)
- [ ] Stress test 4h+ em 3 celulares diferentes (Pixel, Samsung, Xiaomi) — zero perda
- [ ] Bateria < 8%/h
- [ ] Recovery testado com 5 cenários de crash diferentes
- [ ] Onboarding completo, todas as permissões
- [ ] Site live, política de privacidade, página de download
- [ ] Workflow de release funcional, APK assinado

### v1.1 (Transcrição)
- [ ] Whisper Small WER < 14% em PT-BR técnico (suite de 10 amostras)
- [ ] Transcrição 4h em celular médio em < 4h (background OK)
- [ ] Word timestamps acurados (< 200ms erro)
- [ ] Exportação de TXT, MD, PDF, DOCX, SRT, VTT, JSON funcional
- [ ] Busca FTS5 < 500ms

### v1.2 (Diarização)
- [ ] DER < 20% em sessão simulada com 3 speakers
- [ ] Identificação correta do professor após enrollment > 95%
- [ ] UI de edição de speakers fluida
- [ ] Diarização 4h em < 1h em celular médio

### v1.3 (Análise IA)
- [ ] Resumo gerado considerado útil em ≥ 8 das 10 aulas teste
- [ ] LLM local roda em celular 6GB sem OOM
- [ ] Cloud Claude integrado, custo estimado correto
- [ ] Alertas em tempo real funcionais e não invasivos
- [ ] Documentação completa

---

## 12.6. Plano de QA e testes em produção

### Beta privado (após v1.0)

- 5–10 instrutores convidados
- Rodam por 2 semanas
- Feedback via formulário Google Form (curto) + canal direto
- Métricas: # de gravações, % de sucesso, # de bugs reportados

### Beta público (após v1.1)

- Anunciar no GitHub Discussions, comunidades de educação
- Versão "beta" acessível na página de download
- Issues marcadas como `beta-feedback`

### Release público

- Comunicado no blog/site
- Posts em comunidades relevantes (Linux/Android, educação, Reddit r/sysadmin/teachers)

---

## 12.7. Como o plano evolui

Este plano é **vivo**. Convenção:

- **Mudança pequena** (clarificação, typo): edita direto.
- **Mudança média** (adicionar feature, mudar approach): edita + adiciona à seção "histórico de mudanças" abaixo.
- **Mudança grande** (refazer arquitetura, mudar stack): novo doc `docs/MIGRATION-XX.md` explicando, atualiza tudo.

### Histórico de mudanças
- 2026-05-03: Plano inicial criado.

---

## 12.8. Pra quê servem os documentos `docs/`

| Documento | Atualizado quando |
|-----------|---------------------|
| 01 (visão) | Pivôs de produto, novas personas |
| 02 (arquitetura) | Decisões técnicas relevantes |
| 03 (gravação) | Bugs encontrados, edge cases novos |
| 04 (áudio) | Novos algoritmos, novas otimizações |
| 05 (transcrição) | Whisper releases, novos modelos |
| 06 (diarização) | Sherpa releases, melhorias |
| 07 (IA) | Novos modelos, novos prompts |
| 08 (UI/UX) | Cada nova tela, redesigns |
| 09 (storage) | Schema migrations, novos exporters |
| 10 (site) | Refator do site, nova doc |
| 11 (CI/CD) | Mudanças no pipeline |
| 12 (este) | Cada sprint review, cada release |

---

## 12.9. Última observação

Esse plano é ambicioso. **Faseado, é factível.** A chave é:

1. **Não começar a codar a v1.3 antes de ter v1.0 sólida.**
2. **Cada release é um produto utilizável por si só** — você pode parar em qualquer ponto e ainda ter algo bom.
3. **Testar com você mesmo dando aulas reais** desde a v1.0.
4. **Documentar enquanto faz** — não deixar pra depois.
5. **Não inflar escopo no meio do caminho.** Anote pra v1.x ou v2 e segue o plano.

Boa sorte. Quando você responder as decisões 🔴 críticas, podemos começar.
