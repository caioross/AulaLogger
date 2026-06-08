# Changelog

Todas as mudanças notáveis do AulaLogger são documentadas aqui.

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
Versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

> **Status:** beta em desenvolvimento ativo (v0.7.x). A confiabilidade da
> gravação longa é a prioridade número um e é testada com sessões reais de 4h+
> antes de cada release. Histórico completo de commits no
> [GitHub](https://github.com/caioross/AulaLogger).

---

## [0.7.4] — "Robustez" (Unreleased)

### Changed

- Endurecimento para produção a partir dos relatórios internos de auditoria:
  tratamento de recursos, defesa contra estados inválidos e fluxos de borda.

### Fixed

- Condições de corrida entre threads de gravação/transcrição.
- Vazamentos de recursos em fluxos de cancelamento.

## [0.7.3] — "Redesign" — 2026-05-10

### Changed

- Reformulação da interface com Material 3.
- Telas de sessão e de análise repensadas para leitura confortável de transcrições longas.

## [0.7.2] — "Progresso" — 2026-05-08

### Added

- ETA honesto e progresso em tempo real durante a transcrição ("faltam 12 min", nunca "carregando…").

### Fixed

- Cancelamento da transcrição agora responde em menos de 2 segundos.

## [0.7.1] — "Velocidade" — 2026-05-08

### Added

- VAD (Silero) integrado: pula silêncios e acelera a transcrição em 30–50%.

### Fixed

- Menos alucinação do modelo em pausas longas.

## [0.7.0] — "Correções" — 2026-05-05

### Fixed

- Estabilização do pipeline de transcrição e correções pós-polimento.

## [0.6.0] — "Polimento" — 2026-05-05

### Added

- Player de áudio com seek e waveform animado.

### Changed

- Refinamento geral de UX.

## [0.5.0] — "Estável" — 2026-05-05

### Changed

- Marco de estabilidade da gravação longa de 4h+.

## [0.4.0] — "Rolling" — 2026-05-05

### Added

- Gravação em chunks com escrita incremental e `fsync` periódico (crash-safe).

## [0.3.0] — "Pro" — 2026-05-05

### Added

- Recursos avançados de transcrição e gestão de modelos Whisper (download retomável e validação).

## [0.2.0] — "Streaming" — 2026-05-05

### Added

- Captura em streaming com escrita WAV contínua (PCM 16-bit, 16 kHz).

## [0.1.0] — "Native" — 2026-05-04

### Added

- Reescrita 100% nativa em **Kotlin + Jetpack Compose** (Material 3).
- Primeira transcrição on-device com **whisper.cpp** via JNI.
- Foreground Service de gravação, persistência local e recuperação de crash.

---

## Roadmap (rumo à v1.0 e além)

- [ ] Diarização real por impressão de voz, com cadastro do professor.
- [ ] Análise com LLM 100% local (sem nuvem nenhuma).
- [ ] Exportação rica: PDF, DOCX, SRT, VTT, JSON.
- [ ] Publicação no F-Droid + keystore de produção.
- [ ] Suporte a múltiplos idiomas.

---

## Convenções

### Tipos de mudança

- **Added** — features novas
- **Changed** — mudanças em features existentes
- **Deprecated** — features que serão removidas em release futuro
- **Removed** — features removidas
- **Fixed** — correções de bugs
- **Security** — correções relacionadas a segurança

### Tags

- `[BREAKING]` — mudança que quebra compatibilidade
- `[a11y]` — acessibilidade
- `[i18n]` — internacionalização
- `[perf]` — performance
- `[deps]` — atualização de dependência
