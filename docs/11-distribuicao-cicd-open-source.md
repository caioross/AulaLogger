# 11 — Distribuição, CI/CD e abertura do código

> Como o app chega até o usuário, como construímos releases reprodutíveis, como o projeto vive como open source.

---

## 11.1. Canais de distribuição

| Canal | Status v1.0 | Esforço | Audiência |
|-------|--------------|---------|-----------|
| **APK no site** | ✅ obrigatório | baixo | Geral |
| **GitHub Releases** | ✅ obrigatório | baixo | Devs, early adopters |
| **F-Droid** | 🟡 v1.1+ | médio (precisa metadados específicos) | Privacy-conscious |
| **Google Play Store** | ❌ não na v1 | alto (revisão restritiva pra apps de gravação) | Massa |
| **Amazon Appstore** | ❌ não | médio | Tablets Fire (ignorável) |
| **Huawei AppGallery** | ❌ não | médio | China + alguns mercados |

Foco: APK + GitHub + F-Droid.

---

## 11.2. Versionamento

Semver: `MAJOR.MINOR.PATCH`.
- MAJOR = breaking change (ex: schema do banco que precisa migração arriscada).
- MINOR = features novas (ex: v1.0 → v1.1 com transcrição).
- PATCH = bugs e melhorias pequenas.

**Pre-releases:** `1.3.0-beta.1`, `1.3.0-rc.2`.

`versionCode` (Android, integer monotônico) = derivado de `versionName`. Ex: `1.2.3` → `10203`. Ou contador automático no CI.

---

## 11.3. CI/CD com GitHub Actions

### Workflow 1: tests (a cada push/PR)

```yaml
# .github/workflows/tests.yml
name: tests
on: [push, pull_request]
jobs:
  js-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: cd app && npm ci
      - run: cd app && npm run test
      - run: cd app && npm run typecheck
      - run: cd app && npm run lint
  
  android-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: android-actions/setup-android@v3
      - run: cd app/android && ./gradlew test
  
  android-instrumented-tests:
    runs-on: macos-latest  # melhor suporte ao emulador
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          script: cd app/android && ./gradlew connectedAndroidTest
  
  site-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: cd site && npm ci && npm run build
```

### Workflow 2: build APK (em PR e em main)

```yaml
# .github/workflows/app-build.yml
name: app-build
on:
  push:
    branches: [main]
    paths: [app/**]
  pull_request:
    paths: [app/**]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { submodules: recursive }   # whisper.cpp, llama.cpp, etc
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - uses: android-actions/setup-android@v3
      - run: cd app && npm ci
      - run: cd app && npx expo prebuild --platform android
      - run: cd app/android && ./gradlew assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: aulalogger-apk
          path: app/android/app/build/outputs/apk/release/*.apk
```

### Workflow 3: release (tag push)

```yaml
# .github/workflows/app-release.yml
name: app-release
on:
  push:
    tags: ['v*']
jobs:
  release:
    runs-on: ubuntu-latest
    permissions: { contents: write }
    steps:
      - uses: actions/checkout@v4
        with: { submodules: recursive }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - uses: android-actions/setup-android@v3
      - run: cd app && npm ci
      - run: cd app && npx expo prebuild --platform android
      
      # Build APK assinado
      - name: decode keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: echo "$KEYSTORE_BASE64" | base64 -d > app/android/keystore.jks
      
      - name: build assinado
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: cd app/android && ./gradlew assembleRelease
      
      - name: gerar SHA-256
        run: sha256sum app/android/app/build/outputs/apk/release/*.apk > sha256.txt
      
      # Cria release no GitHub
      - uses: softprops/action-gh-release@v2
        with:
          files: |
            app/android/app/build/outputs/apk/release/*.apk
            sha256.txt
          generate_release_notes: true
          body_path: CHANGELOG.md
      
      # Copia APK pro site
      - name: copiar APK pro site
        run: |
          cp app/android/app/build/outputs/apk/release/*.apk site/public/apk/
          cp sha256.txt site/public/apk/
```

### Workflow 4: deploy site (push na pasta site/)

```yaml
# .github/workflows/site-deploy.yml
name: site-deploy
on:
  push:
    branches: [main]
    paths: [site/**, .github/workflows/site-deploy.yml]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: cd site && npm ci && npm run build
      - uses: cloudflare/pages-action@v1
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          projectName: aulalogger
          directory: site/dist
```

---

## 11.4. Assinatura do APK

**Crítico para distribuição fora da Play Store.** Mesma chave precisa ser usada em todas as releases (caso contrário, atualização vira "instalar app diferente" — perde dados).

### Geração da chave (uma vez, off-line, guardada em local seguro)

```bash
keytool -genkey -v \
  -keystore aulalogger-release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias aulalogger \
  -storepass <SENHA_FORTE> \
  -keypass <SENHA_FORTE>
```

Backup da chave em pelo menos 2 lugares offline (criptografada). **Perdê-la = nunca mais conseguir lançar update reconhecido como "AulaLogger".**

### Armazenamento no CI

- Codifica `aulalogger-release.jks` em base64.
- Guarda como secret no GitHub: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Workflow decodifica em runtime, assina, descarta.

---

## 11.5. F-Droid

F-Droid exige:
- Código aberto (escolha entre GPL-3.0 / Apache 2.0 / MIT — decisão pendente P2).
- Sem libs proprietárias (Google Play Services, etc).
- Build reprodutível (a partir do source na ref especificada, byte-exato).
- Metadata em `metadata/com.aulalogger.app.yml`.

### Build reprodutível

- Pinar versões de tudo (Gradle, NDK, CMake).
- Sem plugins não-livres.
- Sem deps que baixam binários proprietários.

### Modelos ML — F-Droid friendly?

Whisper (MIT), llama.cpp (MIT), sherpa-onnx (Apache 2.0), Gemma (Apache 2.0 com terms), RNNoise (BSD): todos OK.

**Ponto de atenção:** F-Droid prefere que dependências grandes não sejam baixadas em runtime. Como nossos modelos ML são grandes (~1GB total) e baixados sob demanda do nosso servidor, isso pode ser um motivo de ressalva pelo F-Droid. **Mitigação:** documentar transparentemente, oferecer "build com tudo embutido" (APK gigante de ~1.2GB) como variant para F-Droid.

### Repositório próprio?

Antes de submeter ao F-Droid oficial, podemos manter nosso próprio repositório F-Droid no GitHub Pages → instalável adicionando URL. Útil para testes.

---

## 11.6. Open Source — preparação do repositório

### Licença

**Decisão pendente (P2). Opções:**

- **MIT:** mais permissiva, max adoção. Quem fizer fork pode fechar.
- **Apache 2.0:** similar a MIT mas com cláusula de patentes.
- **GPL-3.0:** copyleft. Forks devem permanecer open source. Mais alinhado com filosofia do AulaLogger (privacy + open). Alguns devs evitam GPL.
- **AGPL-3.0:** GPL + cobre uso em rede. Overkill (nossa app não é serviço web).

**Recomendação:** **GPL-3.0** se você quer que forks permaneçam abertos. **Apache 2.0** se quer máxima adoção sem essa garantia.

### Arquivos obrigatórios na raiz do repo

- `README.md` — apresentação, instalação, contribuição
- `LICENSE` — texto da licença
- `CONTRIBUTING.md` — como contribuir
- `CODE_OF_CONDUCT.md` — Contributor Covenant 2.1
- `SECURITY.md` — como reportar vulnerabilidades responsavelmente
- `CHANGELOG.md` — histórico de releases
- `.gitignore`
- `.github/`
  - `ISSUE_TEMPLATE/`
    - `bug_report.md`
    - `feature_request.md`
    - `question.md`
  - `PULL_REQUEST_TEMPLATE.md`
  - `CODEOWNERS`
  - `dependabot.yml`
  - `workflows/...`

### README.md (esqueleto)

```markdown
# AulaLogger

> Grave, transcreva e analise suas aulas. Tudo no seu celular. Sem nuvem.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/<usuario>/aulalogger)](https://github.com/<usuario>/aulalogger/releases)
[![Site](https://img.shields.io/badge/site-aulalogger.com.br-purple)](https://aulalogger.com.br)

## Features
- Gravação de áudio rock-solid para aulas longas (4h+)
- Transcrição on-device com Whisper (português, inglês, +97 idiomas)
- Diarização: identifica quem falou cada coisa
- Análise pedagógica com IA local (Gemma) e cloud opcional
- 100% offline e privado por padrão
- Open source (GPL-3.0)

## Instalação

[Baixar APK](https://aulalogger.com.br/download) • [GitHub Releases](https://github.com/.../releases) • F-Droid (em breve)

## Como contribuir

Veja [CONTRIBUTING.md](CONTRIBUTING.md).

## Licença

GPL-3.0. Ver [LICENSE](LICENSE).

## Créditos

- [whisper.cpp](https://github.com/ggerganov/whisper.cpp)
- [llama.cpp](https://github.com/ggerganov/llama.cpp)
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- [RNNoise](https://github.com/xiph/rnnoise)
- ... lista completa em [CREDITS.md]
```

### CONTRIBUTING.md

```markdown
# Como contribuir

Obrigado pelo interesse!

## Reportar bugs

Use [Issues](https://github.com/.../issues) com template "Bug report".
Inclua:
- Versão do app
- Modelo do celular e versão Android
- Passos para reproduzir
- Logs (Configurações → Sobre → Diagnóstico → Exportar)

## Sugerir features

Use Issues com template "Feature request".

## Enviar pull request

1. Fork o repo
2. Crie branch `feature/<nome>` ou `fix/<nome>`
3. Faça suas mudanças com testes
4. Rode `npm run test && npm run lint && npm run typecheck`
5. Abra PR descrevendo a mudança

## Setup local

[Link para docs de build local]

## Tradução

Adicione novo idioma em `app/src/i18n/<lang>.json`.

## Áreas que precisam de ajuda

[link para issues marcadas com `help wanted`]
```

---

## 11.7. Política de versões e support

- **Versão atual + anterior:** suporte ativo (correções de bugs).
- **Versões mais antigas:** sem suporte, recomendar update.
- **Vulnerabilidades de segurança:** patches em todas as versões dos últimos 6 meses.

Tabela em SECURITY.md:

| Versão | Suporte | Até |
|--------|---------|-----|
| 1.2.x | ✅ | atual |
| 1.1.x | ✅ | enquanto 1.2 for atual |
| 1.0.x | ⚠️ só vulnerabilidades | mai/2027 |
| < 1.0 | ❌ | — |

---

## 11.8. Comunicação de releases

A cada release:
1. Tag git criada → workflow assina e gera APK.
2. Release no GitHub com changelog automático.
3. APK copiado pro site (deploy automático).
4. Post no blog (opcional para releases grandes).
5. Notificação na "tela inicial" do app (próxima abertura, banner discreto "atualização disponível").
6. Notificação F-Droid (deles, automático após sync).

Update **dentro do app** (in-app update): v1.0 sem isso. v1.x avaliar.

---

## 11.9. Telemetria de adoção (sem coletar dados)

Como saber quantas pessoas usam o app sem violar privacidade?

Opções:
- **Não saber.** Coerente com filosofia.
- **Contagem de downloads** (passive): GitHub release downloads, F-Droid stats (quando aceito).
- **GitHub Stars** como proxy de interesse.
- **Contagem de hits da página de download** (privacy-friendly via Plausible).

**Decisão:** usar contagem de downloads + stars. Sem analytics no app.

---

## 11.10. Custos operacionais

| Item | Custo |
|------|-------|
| Domínio (.com.br) | ~R$ 40/ano |
| Hospedagem site | R$ 0 (Cloudflare Pages free) |
| GitHub | R$ 0 (open source) |
| GitHub Actions | R$ 0 (2000min/mês free são suficientes) |
| CDN para modelos ML | Cloudflare R2 ou GitHub Releases (R$ 0 até ~10GB/mês de transferência) |
| Email transacional (futuro) | Resend free tier |
| Total mensal | **R$ ~3** (só domínio amortizado) |

Sustentabilidade: zero custo recorrente significa que projeto pode ficar vivo mesmo sem renda.

---

## 11.11. Plano de implementação

| Sprint | Entrega |
|--------|---------|
| Sprint 1 (sem 1) | Repo criado, README, LICENSE, .gitignore, primeiro CI |
| Sprint 5 (sem 8) | Workflows de build e deploy do site funcionais |
| Sprint 8 (sem 11) | Workflow de release (tag → APK assinado → GitHub Release) |
| Sprint 11 (sem 14) | Site v1 + APK v1.0 publicado |
| Sprint 15 (sem 18) | Submissão F-Droid (após várias releases estáveis) |
| Contínuo | Releases regulares, comunicação |
