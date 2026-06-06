# 13 — Segurança, testes e conformidade legal

> Documento consolidado sobre como garantir que o AulaLogger é seguro (não vaza dados, não é vetor de ataque), bem testado (não falha onde importa) e em conformidade com legislação brasileira (LGPD principalmente).

---

## 13.1. Modelo de ameaças

### Ativos a proteger

1. **Áudio das aulas** — pode conter informação sensível (alunos menores, opiniões, conteúdo proprietário).
2. **Transcrições** — versão textual do áudio, indexável e fácil de exfiltrar.
3. **API keys** dos provedores cloud — se vazadas, causam custo e podem dar acesso a contas.
4. **Voice fingerprint** do usuário — biométrico, sensível.
5. **Metadados** — quando, onde (não geo, mas inferível), quanto tempo.

### Atores hostis considerados

| Ator | Capacidade | Mitigação |
|------|------------|-----------|
| App malicioso no mesmo device | Tentaria ler `/data/data/<pkg>/` | OS já isola, criptografia opcional |
| Atacante com acesso físico ao device desbloqueado | Tudo | Pouco a fazer; sugerir bloqueio com PIN/biometria |
| Atacante com acesso físico ao device bloqueado | Difícil; pode tentar dump | Encryption opcional cobre |
| Servidor malicioso fingindo ser provedor cloud | MitM em chamada API | TLS + cert pinning para Anthropic/OpenAI/Google |
| Lib third-party comprometida (supply chain) | Código malicioso embutido | Pin de versões, audit dependabot, modelo vendor-when-critical |
| Trabalho de inferência malicioso (modelo ML adversarial) | Causar OOM, output esquisito | Timeout, validação de output, modelos de fontes oficiais |
| Eu/contribuidores com más intenções | Adicionar telemetria, backdoor | Code review obrigatório, CI checa imports suspeitos |

### Não-alvos (escopo)

- Resistir a forensics avançada com custom firmware → não é nossa missão.
- Anonimato em rede → não é app de comunicação.
- Defesa contra OEMs do device → não é nosso layer.

---

## 13.2. Práticas de segurança no código

### Geral

- **Sem `eval`, sem `exec` dinâmico**. Sem geração de código em runtime.
- **Sem reflection** desnecessária.
- **Sem `System.loadLibrary` de paths controláveis pelo usuário** — só de assets do APK.
- **Sem WebView com JS habilitado** carregando conteúdo não trusted.
- **Sem permissão de Internet** quando não necessário (apenas quando cloud LLM ativo, condicionalmente requestado).

### React Native

- **Sem `dangerouslySetInnerHTML`**.
- **Sem `eval` no código JS**.
- **Validar todo input que vem do nativo** com Zod antes de usar.

### Kotlin/Android

- `setAllowBackup="false"` (não queremos que `adb backup` exfiltre tudo).
- `setExtractNativeLibs="false"` (libs nativas em APK, não extraídas).
- `setNetworkSecurityConfig` permitindo só HTTPS.
- ProGuard/R8 obfuscação em release builds (com mapping preservado pra debug interno).
- `FLAG_SECURE` em telas com info sensível (API keys, transcrição) — bloqueia screenshots.

### Native code (C++ via JNI)

- Compilar com `-fstack-protector-strong`, `-D_FORTIFY_SOURCE=2`, `-O2`.
- ASLR e PIE habilitados (default em NDK).
- Vendoring de whisper.cpp/llama.cpp/RNNoise como submódulos com SHA pinned.

### Storage

- API keys em **EncryptedSharedPreferences**, chave do **Keystore**.
- Voice fingerprint em **EncryptedSharedPreferences**.
- Áudio em filesystem privado (`/data/data/<pkg>/files/`), com encryption opcional.

### Logs

- **Nunca logar:** API keys, conteúdo de transcrições, voice fingerprint.
- Logs de erro sanitizam: substituir tokens (regex `eyJ[A-Za-z0-9_-]+`, `sk-[A-Za-z0-9]+`) por `[REDACTED]`.
- Painel de export de logs avisa o usuário antes de exportar e mostra preview.

---

## 13.3. Cert pinning para cloud APIs

Para chamadas a Anthropic/OpenAI/Google, usar **public key pinning**:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.anthropic.com", "sha256/<KEY_HASH>")
    .add("api.openai.com", "sha256/<KEY_HASH>")
    .add("generativelanguage.googleapis.com", "sha256/<KEY_HASH>")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(pinner)
    .build()
```

Trade-off: se provedor mudar cert, app quebra até update. **Mitigação:** atualização rápida, fallback para "sem pinning" via remote config emergency (não na v1).

---

## 13.4. Política de divulgação responsável (Security Disclosure)

`SECURITY.md` no repo:

```markdown
# Política de segurança

## Reportar vulnerabilidades

NÃO abra issue público.

Envie email para: security@aulalogger.com.br
PGP key: [link]

Resposta inicial em até 72h.
Resolução em até 90 dias para crítico, 180 dias para outros.

## Escopo

In-scope:
- AulaLogger Android app
- Site aulalogger.com.br
- Modelos ML que distribuímos

Out-of-scope:
- Provedores cloud LLM (reporte direto a eles)
- OS Android (reporte ao Google)

## Reconhecimento

Damos crédito público a quem reportar (a menos que peça anonimato).
Hall of fame em [link].
```

---

## 13.5. LGPD — Lei Geral de Proteção de Dados

O AulaLogger processa dados pessoais? **Sim**, ainda que apenas localmente:
- Áudio = pode ser dado pessoal sensível (voz do professor, dos alunos).
- Voice fingerprint = dado biométrico.

Mas como **tudo é processado e armazenado no device do próprio usuário**, e o usuário é o "controlador" desses dados (não o AulaLogger), o app se enquadra em **uso pessoal/doméstico**, fora do escopo da LGPD para nós.

### Mas... o usuário (você, professor) pode ser controlador

Quando você grava uma aula com alunos, **você está coletando dados pessoais deles** (voz). Isso significa que **você** precisa:

- Comunicar aos alunos que está gravando (transparência).
- Ter base legal para gravar (consentimento, legítimo interesse, execução de contrato educacional).
- Garantir segurança dos dados.
- Permitir que aluno solicite acesso/exclusão.

**Documentaremos isso na seção "Boas práticas para o professor"** nas docs do app:

```markdown
## Sua responsabilidade ao gravar aulas

A LGPD se aplica a você quando você grava aulas com alunos. 
Algumas boas práticas:

1. **Avise os alunos** que a aula está sendo gravada — no início, 
   com clareza. Ex: "Pessoal, vou gravar nossa aula para gerar 
   transcrição depois. Tudo bem?"

2. **Tenha base legal:**
   - Consentimento explícito é o mais simples (registre por escrito 
     no contrato/termo do curso).
   - Se o curso já prevê gravação, isso pode ser execução de contrato.

3. **Use os dados só para o que comunicou.** Se gravou para gerar 
   transcrição pra alunos, não use pra outros fins sem novo consentimento.

4. **Permita exclusão.** Se um aluno pedir para apagar sua participação, 
   você precisa fazer isso (deletar a aula ou pelo menos o trecho).

5. **Cuidado com dados sensíveis.** Saúde, opinião política, religião 
   mencionadas em aula = dado sensível, exigem cuidado extra.

6. **Se compartilhar a transcrição,** faça-o de forma segura (link 
   privado, não público). Considere remover nomes próprios.

⚠️ Este texto não é orientação jurídica. Em dúvida, consulte um 
   advogado especializado em LGPD/educação.
```

### Para o app em si

- **Política de privacidade** clara no site e no onboarding.
- **Termos de uso** definindo escopo (uso por sua conta e risco).
- **Sem coleta de dados pelo app**.
- **Sem servidor próprio** = sem dados nossos para vazar.

---

## 13.6. Direitos autorais e modelos ML

Modelos ML que usaremos:
- **Whisper:** MIT (OpenAI). OK comercial e privado.
- **Llama 3.x:** Llama Community License (Meta). Permissivo para < 700M usuários ativos. OK pra nós.
- **Gemma 2:** Apache 2.0 com Terms of Use (Google). OK comercial.
- **Phi-3:** MIT (Microsoft). OK.
- **Qwen 2.5:** Apache 2.0 (Alibaba). OK.
- **Sherpa-onnx:** Apache 2.0. OK.
- **RNNoise:** BSD-3-Clause. OK.
- **Pyannote (modelos):** MIT. OK (mas convertidos para ONNX).
- **Silero VAD:** MIT. OK.
- **DeepFilterNet:** MIT. OK.

Documentar tudo em `CREDITS.md` com links para licenças originais.

### Audio/imagens em UI

- **Logo:** original ou licenciado.
- **Ícones:** Lucide (ISC license, OK).
- **Fontes:** Inter (SIL Open Font License), JetBrains Mono (Apache 2.0).
- **Imagens em landing:** próprias, geradas por IA (com cuidado em sourcing) ou Unsplash (free).

---

## 13.7. Estratégia de testes consolidada

### Pirâmide de testes

```
                  ▲
                 ╱ ╲
                ╱   ╲     E2E (manual + smoke automatizado)
               ╱─────╲    Cobertura: fluxos críticos
              ╱       ╲
             ╱         ╲   Integration tests
            ╱───────────╲  Cobertura: módulo nativo + bridges
           ╱             ╲
          ╱               ╲ Unit tests
         ╱─────────────────╲ Cobertura: 70% lógica de negócio
```

### Por categoria

#### Unit tests (Jest para JS, JUnit para Kotlin)

**JS/TS:**
- Componentes React Native (React Testing Library + jest-native).
- Hooks (renderHook).
- Stores Zustand.
- Funções puras (parsing, formatação, validação).
- Schemas Zod (validação correta).

**Kotlin:**
- DAOs do Room (com banco em memória).
- ChunkWriter (escreve corretamente, recovery).
- Pipeline de áudio (cada estágio).
- Bridges JNI (mock do native).
- Lógica de diarização clustering.
- Lógica de identificação de professor.

**Cobertura alvo:** 70% para lógica de negócio. UI cobertura por integration/E2E.

#### Integration tests

**Kotlin (instrumented, no emulador):**
- Gravar 30s real, verificar arquivo + manifest.
- Recovery: simular kill no meio, verificar recuperação.
- Whisper: transcrever áudio de teste conhecido, verificar texto.
- Sherpa: diarizar áudio com 2 speakers conhecidos, verificar separação.
- Llama: gerar resumo, verificar JSON parseável.
- Storage: workflow completo gravar→transcrever→exportar.

#### E2E (Detox para RN ou Maestro)

**Fluxos críticos automatizados:**
- Onboarding completo (permissões mockadas).
- Gravar 1 minuto, parar, ver na biblioteca.
- Abrir aula, ver transcrição.
- Exportar transcrição como PDF.
- Configurar API key, gerar análise cloud.

**Smoke test em CI:** suite mínima a cada PR.

#### Stress tests (manuais com instrumentação)

- Gravar 4h em celular real, todas combinações de fabricante.
- Encher disco até 100MB livres durante gravação.
- Bateria a 10% durante gravação.
- App killed sucessivamente durante gravação.
- Carregar 100 aulas na biblioteca, busca em todas.
- Modelo Whisper Medium em celular 4GB (deve dar OOM e fallback).

#### Performance tests

- Tempo de transcrição (real-time factor) por modelo + device.
- Memória peak por operação.
- Bateria/hora durante gravação.
- Tempo de boot do app.
- Tempo de carregamento da biblioteca com 100/500/1000 aulas.

#### Accessibility tests

- TalkBack: navegar todas as telas.
- Fonte ampliada (200%): UI não quebra.
- Cores: contraste mínimo 4.5:1 verificado.

#### Regression suite

- Após cada release, antes da próxima:
  - Suite automatizada roda em 3 devices CI.
  - Suite manual pelo dono (você) em 1 device real.

---

## 13.8. Gestão de dependências

- **Dependabot** habilitado para PRs automáticos de updates.
- **CVE scanning:** GitHub Security Advisories.
- **Pin de versões major.** Patch updates aceitos automaticamente; minor/major requerem revisão.
- **Lock files** commitados (`package-lock.json`, `gradle.lockfile`).

---

## 13.9. Build reprodutível

Importante para F-Droid e para auditoria:
- **Pin** de Gradle, AGP, NDK, CMake versions em `gradle/wrapper/gradle-wrapper.properties` e `build.gradle`.
- **Pin** de versão do Node, npm, Expo CLI.
- **Documentar** ambiente exato em `BUILD.md`.
- **Container Docker** opcional para reprodução exata.

```dockerfile
# Dockerfile (em tools/)
FROM eclipse-temurin:17-jdk
RUN apt-get update && apt-get install -y nodejs npm cmake ninja-build
RUN npm install -g expo-cli
COPY . /app
WORKDIR /app
RUN cd app && npm ci && npx expo prebuild --platform android
RUN cd app/android && ./gradlew assembleRelease
```

---

## 13.10. Auditoria interna periódica

A cada release MAJOR:
- [ ] Revisar permissions do AndroidManifest (alguma sobrando?).
- [ ] Revisar dependencies — alguma abandonada?
- [ ] Revisar logs — vazando algo sensível?
- [ ] Revisar tráfego de rede em modo "default" (deve ser zero).
- [ ] Revisar consumo de bateria vs releases anteriores.
- [ ] Revisar tamanho do APK vs releases anteriores.
- [ ] Pen test básico: instalar em device, tentar exfiltrar dados via outro app.

---

## 13.11. Resposta a incidentes

Se algo der errado em produção:

1. **Severidade Crítica** (vazamento de dados, gravações perdidas em massa):
   - Notificar usuários via banner no site + post no GitHub Discussions imediatamente.
   - Hotfix em até 24h.
   - Post-mortem público em até 7 dias.
2. **Severidade Alta** (crash que impede uso):
   - Notificar via release notes do hotfix.
   - Hotfix em até 7 dias.
3. **Severidade Média** (feature quebrada parcial):
   - Issue tracker, próximo release regular.
4. **Severidade Baixa** (cosmético, edge case raro):
   - Backlog.

---

## 13.12. Métricas e KPIs de qualidade

| Métrica | Meta | Como medir |
|---------|------|------------|
| Cobertura de testes (unit) | > 70% | Jest + JaCoCo |
| Tempo de build CI | < 15min | GitHub Actions |
| Crashes free rate (autoreporte) | > 99% | Voluntário, sem telemetria |
| % de gravações 4h+ que completam sem incidente | 100% | Beta testers reportam |
| Tempo médio de resposta a issue P0 | < 24h | GitHub |
| Vulnerabilidades críticas open | 0 | Dependabot |
| Lighthouse score do site | > 95 | Lighthouse CI |

---

## 13.13. Plano de implementação

| Sprint | Entrega de segurança/QA |
|--------|---------------------------|
| Sprint 1 | Setup Jest + JUnit, primeiros testes |
| Sprint 1 | SECURITY.md, CODE_OF_CONDUCT.md, privacy policy v1 |
| Sprint 3 | EncryptedSharedPreferences para futura API key |
| Sprint 5 | Suite de stress tests gravação |
| Sprint 8 | FLAG_SECURE em telas sensíveis |
| Sprint 11 | Cert pinning para cloud (quando necessário) |
| Sprint 15 | Dependabot ativo, audit |
| Sprint 19 | Auditoria de logs, redação de tokens |
| Antes v1.0 release | Pen test básico, build reprodutível |
