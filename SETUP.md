# Setup — primeira execução do AulaLogger

> Este arquivo é o "primeiro contato" com o repositório recém-criado.
> Siga na ordem.

---

## ✅ O que está pronto

109 arquivos criados, cobrindo:

- **Plano completo** (16 documentos em `docs/`)
- **Esqueleto do app Android** (Expo + RN + TypeScript + Expo Router) — compila e roda
- **Módulo nativo Kotlin** com:
  - Foreground Service de gravação (`RecordingService`)
  - Captura PCM via `AudioRecord`
  - Chunking WAV de 30s com escrita atômica
  - Manifest em disco (`meta.json`)
  - Wake lock parcial
  - Notificação persistente (Android 14 compatible)
  - Eventos JS↔nativo
  - Marcadores em tempo real
  - Pause/resume
- **Site Astro** com landing, download, privacy, changelog, 404
- **CI/CD** (4 workflows GitHub Actions)
- **Issue templates, PR template, Code of Conduct, SECURITY, CONTRIBUTING**
- **Tools** (download de modelos, geração de áudio teste, ADB helper, Dockerfile)

Todos os JSONs, YAMLs e arquivos JS validados sintaticamente.

---

## 🚧 O que ainda NÃO está pronto (próximas sprints)

- Testes Detox / E2E
- Pipeline de áudio (RNNoise, normalize, VAD) — sprint 6
- Whisper.cpp via JNI — sprint 8
- Sherpa-onnx diarização — sprint 12
- llama.cpp LLM local — sprint 16
- Cloud LLM client (Claude/OpenAI/Gemini) — sprint 19
- SQLite Room — sprint 2 (atualmente usando MMKV/Zustand persist)
- Exporters (PDF, DOCX, SRT) — sprint 4-5
- Onboarding (telas guiadas) — sprint 5
- Conteúdo MDX da documentação no site — sprint 7+
- Assets visuais finais (ícone, splash, OG image)
- Fontes Inter e JetBrains Mono em `assets/fonts/`
- Keystore para release (você gera local, NUNCA commita)

---

## 📋 Checklist de primeira execução no seu computador

### 1. Pré-requisitos (conferir/instalar)

```bash
node --version       # >= 20.0.0
npm --version        # >= 10.0.0
java -version        # 17.x
adb --version        # qualquer
```

Se algum faltar, veja [BUILD.md](BUILD.md).

### 2. Criar repositório no GitHub

1. Crie repo novo (privado primeiro, pode virar público depois) chamado `aulalogger`.
2. NÃO inicialize com README, LICENSE ou .gitignore (já temos).
3. No seu computador, dentro de `E:\AulaLogger`:

```bash
cd E:\AulaLogger
git init
git checkout -b main
git add .
git commit -m "chore: setup inicial do projeto + plano de desenvolvimento"
git remote add origin https://github.com/<seu-usuario>/aulalogger.git
git push -u origin main
```

### 3. Substituir placeholders

Procure por `<usuario>` em todos os arquivos e substitua pelo seu username GitHub:

```bash
# Linux/macOS
grep -rl "<usuario>" . --exclude-dir=node_modules | xargs sed -i 's/<usuario>/SEU-USERNAME/g'

# Windows PowerShell
Get-ChildItem -Recurse -File -Exclude node_modules | ForEach-Object {
  (Get-Content $_.FullName -Raw) -replace '<usuario>', 'SEU-USERNAME' | Set-Content $_.FullName
}
```

Arquivos afetados: README.md, CONTRIBUTING.md, SECURITY.md, .github/CODEOWNERS, .github/FUNDING.yml, .github/ISSUE_TEMPLATE/config.yml, site/src/components/Header.astro, site/src/components/Footer.astro, site/src/pages/changelog.astro, site/src/pages/download.astro.

### 4. Instalar dependências

```bash
cd app
npm install

cd ../site
npm install
```

### 5. Gerar projeto Android nativo

```bash
cd ../app
npx expo prebuild --platform android
```

Isso cria a pasta `app/android/` com toda a configuração nativa baseada em `app.json`.

### 6. Conectar device Android e rodar

```bash
adb devices            # confirma que o device aparece
npx expo run:android   # build + install + run
```

Você deve ver o app abrindo na home com o botão grande "INICIAR".

### 7. Testar gravação básica (smoke test)

1. Abra o app no celular
2. Conceda permissão de microfone quando pedir
3. Aperte INICIAR
4. Deixe gravando 1 minuto
5. Aperte parar
6. Verifique arquivos via ADB:

```bash
./tools/adb-helper.sh pull-recordings
ls device-recordings/files/recordings/<session-id>/
# Deve listar: meta.json, chunk-00000.wav, chunk-00001.wav
```

🎉 **Se chegou aqui sem erro, todo o pipeline está funcionando.**

### 8. Rodar testes

```bash
cd app
npm test               # Jest (TS)
cd android
./gradlew :aulalogger-native:test    # JUnit (Kotlin)
```

### 9. Rodar o site

```bash
cd site
npm run dev            # http://localhost:4321
```

### 10. Configurar secrets do GitHub (para release futuro)

No GitHub → Settings → Secrets and variables → Actions → New repository secret:

| Nome | Quando precisar |
|------|------------------|
| `KEYSTORE_BASE64` | release assinado (ver BUILD.md §11.4) |
| `KEYSTORE_PASSWORD` | idem |
| `KEY_ALIAS` | idem |
| `KEY_PASSWORD` | idem |
| `CLOUDFLARE_API_TOKEN` | deploy do site |
| `CLOUDFLARE_ACCOUNT_ID` | deploy do site |

---

## 🎯 Próximas decisões (críticas) que ainda dependem de você

Listadas em [docs/12 §12.4](docs/12-roadmap-riscos-decisoes-pendentes.md#124-decisões-pendentes--lista-consolidada):

- **P1 Nome do app** — atualmente `AulaLogger` em todo lugar
- **P2 Licença** — atualmente GPL-3.0 (ver `LICENSE`)
- **P3 Provedor cloud** — Claude primeiro nas configs (sem código de cliente ainda)
- **P4 Hardware mínimo** — Android 10+, 4GB RAM (em `app.json` minSdkVersion: 29)

Se quiser mudar, find/replace global resolve em minutos.

---

## 🗺️ Roadmap das próximas 8 semanas (Sprint 1-5: v1.0)

Conforme [docs/12 §12.1](docs/12-roadmap-riscos-decisoes-pendentes.md#121-roadmap-detalhado):

```
Sem 1-2  → Sprint 1 ✅ (você está aqui)
Sem 3-4  → Sprint 2: Foreground service estendido + Storage Room + UI gravação
Sem 5-6  → Sprint 3: Robustez (recovery, OEMs, edge cases)
Sem 7    → Sprint 4: Player + storage + export áudio
Sem 8    → Sprint 5: Polimento + site v1 + 🚀 Release v1.0.0
```

---

## ⚠️ Notas honestas

- **O módulo nativo compila mas precisa de teste em device real.** Em emulador, AudioRecord pode dar `NO_PERMISSION` ou produzir áudio silente. Use device físico para validação.
- **A persistência atual (Zustand + MMKV) é provisória.** Sprint 2 migra para SQLite Room para suportar busca FTS5 e queries complexas.
- **Não há onboarding ainda.** A primeira tela vai pedir permissão direto. Sprint 5 adiciona fluxo guiado.
- **CI roda mas alguns workflows precisam de secrets para passar 100%** (release e deploy do site).
- **`<usuario>` precisa ser substituído** antes de fazer push, senão links quebram.

---

## 📞 Quando precisar de ajuda

- **Ficou preso em algo do plano?** Volte ao Cowork (aqui), descreva o problema.
- **Bug nativo Android?** Cole stacktrace + `./tools/adb-helper.sh logs`.
- **Whisper/Llama/Sherpa?** Issues nos repos oficiais costumam ter resposta rápida.
- **Expo?** Discord oficial (super ativo).

Boa construção. 🎙️
