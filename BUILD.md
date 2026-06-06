# Build local — AulaLogger

Como buildar o app e o site no seu computador.

---

## Requisitos

### Geral

- **Git** 2.40+
- **Node.js** 20.x LTS (recomendado via [nvm](https://github.com/nvm-sh/nvm) ou [fnm](https://github.com/Schniz/fnm))
- **npm** 10+ (vem com Node) ou **pnpm** 9+

### Para o app Android

- **JDK 17** (Temurin/Adoptium recomendado)
- **Android SDK** com:
  - Build Tools 34.0.0+
  - Platform 34 (Android 14)
  - NDK 26.x ou 27.x
  - CMake 3.22+
- **Android Studio** (opcional, mas recomendado)
- **Dispositivo Android físico** ou emulador (Android 10+, API 29+)

### Para o site

- Apenas Node.js 20+

---

## Setup inicial (primeira vez)

### 1. Clonar repositório

```bash
git clone https://github.com/<usuario>/aulalogger.git
cd aulalogger
```

### 2. Instalar dependências do app

```bash
cd app
npm install
```

Se for a primeira vez ou após mudança de módulo nativo, gere o projeto Android:

```bash
npx expo prebuild --platform android
```

Isso cria/atualiza a pasta `app/android/`.

### 3. Instalar dependências do site

```bash
cd ../site
npm install
```

### 4. Configurar variáveis de ambiente (opcional)

Copie o template:

```bash
cp app/.env.example app/.env.local
```

Edite com suas chaves se for testar IA cloud.

---

## Comandos do dia a dia

### App — desenvolvimento

```bash
cd app

# Iniciar Metro bundler + abrir em device/emulador Android
npx expo run:android

# Apenas Metro (se já tem APK debug instalado)
npx expo start

# Limpar cache do Metro
npx expo start --clear

# Verificar tipos
npm run typecheck

# Lint
npm run lint
npm run lint:fix

# Format
npm run format

# Tests
npm test
npm run test:watch
```

### App — Android nativo

```bash
cd app/android

# Build debug APK
./gradlew assembleDebug

# Instalar em device conectado
./gradlew installDebug

# Build release (requer keystore)
./gradlew assembleRelease

# Tests Kotlin
./gradlew test
./gradlew connectedAndroidTest  # requer emulador/device

# Limpar
./gradlew clean
```

### Site

```bash
cd site

# Dev server (http://localhost:4321)
npm run dev

# Build estático
npm run build

# Preview do build
npm run preview
```

---

## Setup do Android SDK (se não usar Android Studio)

### Linux/macOS

```bash
# Instalar command line tools manualmente
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
# Baixar de https://developer.android.com/studio#command-tools
unzip commandlinetools-*.zip
mv cmdline-tools latest

# Variáveis de ambiente (adicionar ao ~/.bashrc ou ~/.zshrc)
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator

# Aceitar licenças
sdkmanager --licenses

# Instalar componentes
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
sdkmanager "ndk;27.0.11718014" "cmake;3.22.1"
```

### Windows

Use [Android Studio](https://developer.android.com/studio) — ele instala tudo. Depois adicione ao PATH:

- `%LOCALAPPDATA%\Android\Sdk\platform-tools`
- `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin`

---

## Conectar device físico

1. No celular, ative **Opções do desenvolvedor**:
   - Configurações > Sobre o telefone > toque 7x em "Número da versão"
2. Em Opções do desenvolvedor, ative:
   - **Depuração USB**
   - **Instalar via USB** (se aparecer)
3. Conecte via USB, autorize a depuração quando o popup aparecer
4. Verifique:
   ```bash
   adb devices
   ```
   Deve listar seu device como `device` (não `unauthorized` ou `offline`).

---

## Build reprodutível (para F-Droid)

Para garantir builds idênticos a partir do mesmo source, use o Dockerfile em `tools/Dockerfile`:

```bash
docker build -t aulalogger-build -f tools/Dockerfile .
docker run --rm -v $(pwd):/workspace aulalogger-build \
  bash -c "cd /workspace/app && npx expo prebuild --platform android && cd android && ./gradlew assembleRelease"
```

Hashes do APK resultante devem bater com os publicados no GitHub Release correspondente.

---

## Modelos ML (download)

Os modelos não são commitados (são grandes — total ~1GB). Para baixar para teste local:

```bash
cd tools
./download-models.sh
```

Isso baixa para `app/assets/models-cache/` (gitignored). O app usa esses modelos em modo dev.

Em produção, modelos são baixados pelo próprio app na primeira execução.

---

## Troubleshooting

### "SDK location not found"

Crie `app/android/local.properties`:

```
sdk.dir=/caminho/para/Android/Sdk
ndk.dir=/caminho/para/Android/Sdk/ndk/27.0.11718014
```

### "Could not find tools.jar"

Use JDK 17 (não JRE). Verifique:

```bash
java -version
javac -version
```

### Metro bundler trava

```bash
cd app
rm -rf node_modules .expo
npm install
npx expo start --clear
```

### Build Android lento

- Habilite Gradle daemon e parallel:
  ```
  # ~/.gradle/gradle.properties
  org.gradle.daemon=true
  org.gradle.parallel=true
  org.gradle.caching=true
  org.gradle.jvmargs=-Xmx4g
  ```

### Erros de NDK / CMake

- Confirme NDK 26+ instalado: `ls $ANDROID_HOME/ndk`
- Limpe cache CMake: `cd app/android && ./gradlew clean`

### "Failed to resolve plugin"

Atualize o Gradle wrapper:

```bash
cd app/android
./gradlew wrapper --gradle-version 8.10
```

---

## Verificação rápida pós-setup

Tudo deve funcionar sem erro:

```bash
cd app
npm run typecheck   # ✅ sem erros
npm run lint        # ✅ sem erros
npm test            # ✅ todos passam
npx expo prebuild --platform android --clean
cd android && ./gradlew assembleDebug   # ✅ APK gerado
ls app/build/outputs/apk/debug/         # ✅ APK presente
```

```bash
cd site
npm run build       # ✅ build em site/dist/
```

Se chegou aqui sem erros, está tudo pronto. 🎉
