# 14 — Começar agora: o que fazer na primeira semana

> Guia prático de "amanhã de manhã abro o computador, o que faço primeiro?". Sequência concreta para sair do plano e entrar em execução, sem se perder na grandeza do projeto.

---

## 14.1. Antes de qualquer linha de código

### Dia 0 (você responde, eu não preciso)

Trave as decisões 🔴 críticas em [docs/12 §12.4](12-roadmap-riscos-decisoes-pendentes.md#124-decisões-pendentes--lista-consolidada):

- [ ] **Nome do app** (P1) — sugiro `AulaLogger` provisoriamente.
- [ ] **Licença** (P2) — sugiro `GPL-3.0`.
- [ ] **Provedor cloud principal** (P3) — sugiro Claude.
- [ ] **Hardware mínimo** (P4) — sugiro Android 10+, 4GB RAM.

Não precisa decidir as 🟡 e 🟢 ainda — posso prosseguir sem elas.

---

## 14.2. Dia 1 — Setup do repositório

Estimativa: 2–3h.

### Passo a passo

1. Cria conta GitHub (se não tem) ou usa a existente.
2. Cria repositório `aulalogger` (privado primeiro, vira público quando v1.0 sair).
3. Cria estrutura local clonando + adicionando os documentos do plano.
4. Faz primeiro commit.
5. Cria projeto no Cloudflare Pages (preparar pra deploy de site futuro).
6. Compra domínio (se P7 já decidido).

### Estrutura inicial do repo

```bash
mkdir -p aulalogger/{app,site,docs,tools,.github/workflows,.github/ISSUE_TEMPLATE}
cd aulalogger
git init
git checkout -b main

# Copiar plano + docs
cp -r ../E:/AulaLogger/{PLANO_DE_DESENVOLVIMENTO.md,docs} ./

# Arquivos básicos
touch README.md LICENSE CONTRIBUTING.md CODE_OF_CONDUCT.md SECURITY.md CHANGELOG.md .gitignore

git add .
git commit -m "chore: plano de desenvolvimento inicial"
git remote add origin https://github.com/<voce>/aulalogger.git
git push -u origin main
```

### `.gitignore` mínimo

```gitignore
# Node / Expo
node_modules/
.expo/
dist/
.env
.env.local

# Android
android/build/
android/app/build/
android/.gradle/
android/local.properties
*.keystore
*.jks
keystore.properties

# Modelos ML (grandes — não commitamos, baixados em runtime)
*.bin
*.gguf
*.onnx

# Misc
.DS_Store
.idea/
*.log
coverage/
```

---

## 14.3. Dia 2 — Setup app/ Expo

Estimativa: 3–4h.

```bash
cd aulalogger
npx create-expo-app@latest app --template blank-typescript
cd app

# Habilitar bare workflow / módulos nativos
npx expo prebuild --platform android

# Dependências essenciais primeiro
npm install zustand @tanstack/react-query
npm install @hookform/resolvers react-hook-form zod
npm install expo-router expo-linking expo-constants
npm install lucide-react-native
npm install react-native-mmkv

# Dev dependencies
npm install -D @types/react @typescript-eslint/parser @typescript-eslint/eslint-plugin
npm install -D eslint eslint-config-expo prettier husky lint-staged
npm install -D jest @testing-library/react-native jest-expo

# Testar build
npx expo run:android
```

**Validação:** "Hello World" rodando em emulador Android.

### Setup do módulo nativo (esqueleto)

```bash
cd app
npx create-expo-module@latest --local aulalogger-native
```

Isso cria a estrutura em `app/modules/aulalogger-native/` com Kotlin no Android e TypeScript wrapper.

**Validação:** módulo nativo expõe uma função simples (ex: `hello()`) e UI consegue chamar.

---

## 14.4. Dia 3 — Primeiro AudioRecord funcionando

Estimativa: 4–6h.

### Tarefa concreta

No módulo nativo, adicionar:

```kotlin
// AulaloggerNativeModule.kt

@Function
fun startTestRecording(durationSec: Int, promise: Promise) {
    val outputFile = File(context.filesDir, "test-recording.wav")
    
    val sampleRate = 16000
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ) * 4
    
    val audioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.MIC)
        .setAudioFormat(/* ... */)
        .setBufferSizeInBytes(bufferSize)
        .build()
    
    GlobalScope.launch(Dispatchers.IO) {
        audioRecord.startRecording()
        val output = FileOutputStream(outputFile)
        writeWavHeader(output, sampleRate, 1, 16)
        
        val buffer = ShortArray(bufferSize / 2)
        val endTime = System.currentTimeMillis() + durationSec * 1000
        while (System.currentTimeMillis() < endTime) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            // converter shorts em bytes little-endian e escrever
            output.write(shortsToBytes(buffer, read))
        }
        
        audioRecord.stop()
        audioRecord.release()
        output.close()
        promise.resolve(outputFile.absolutePath)
    }
}
```

E na UI:

```tsx
// app/index.tsx
import { Button, View, Text } from "react-native";
import AulaloggerNative from "./modules/aulalogger-native";
import { useState } from "react";

export default function App() {
  const [recording, setRecording] = useState(false);
  const [path, setPath] = useState<string | null>(null);
  
  async function gravar() {
    setRecording(true);
    const file = await AulaloggerNative.startTestRecording(10);
    setPath(file);
    setRecording(false);
  }
  
  return (
    <View style={{ flex: 1, justifyContent: "center", alignItems: "center" }}>
      <Text style={{ fontSize: 32 }}>AulaLogger v0.0.1</Text>
      <Button title={recording ? "Gravando..." : "Gravar 10s"} onPress={gravar} disabled={recording} />
      {path && <Text>Gravado em: {path}</Text>}
    </View>
  );
}
```

**Validação:** botão grava 10s, retorna path, arquivo WAV existe e abre no player do celular.

🎉 Esse momento é importante. Você acabou de provar que toda a stack RN+Expo+Kotlin+JNI flow funciona.

---

## 14.5. Dia 4 — Primeiro CI

Estimativa: 2–3h.

Cria `.github/workflows/tests.yml` (versão simplificada do que está em [docs/11](11-distribuicao-cicd-open-source.md)):

```yaml
name: tests
on: [push, pull_request]

jobs:
  js:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: cd app && npm ci
      - run: cd app && npm run typecheck
      - run: cd app && npm run lint
      - run: cd app && npm test --if-present

  android-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - uses: android-actions/setup-android@v3
      - run: cd app && npm ci
      - run: cd app && npx expo prebuild --platform android
      - run: cd app/android && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/android/app/build/outputs/apk/debug/*.apk
```

Push → ver build verde no GitHub.

**Validação:** APK debug baixável do CI.

---

## 14.6. Dia 5 — Primeiro Foreground Service

Estimativa: 6–8h.

Aqui começa o trabalho real. Migrar do `startTestRecording` (in-process) para um `RecordingService` (foreground), seguindo [docs/03](03-subsistema-gravacao.md).

Marcos do dia:
- [ ] AndroidManifest com permissões + service declarado.
- [ ] `RecordingService.kt` com `startForeground()` + notification.
- [ ] AudioRecord rodando dentro do service.
- [ ] ChunkWriter escrevendo .wav a cada 30s.
- [ ] Module nativo expõe `startRecording()` / `stopRecording()` que controlam o service.
- [ ] UI atualizada: botão "iniciar/parar" + tempo.

**Validação:** gravar 5min com tela apagada, ver arquivos chunk no filesystem (via `adb shell ls /data/data/com.aulalogger.app/files/...`).

---

## 14.7. Dia 6 — Stress test mini

Estimativa: 1–2h ativo + 30min de gravação real.

**Teste real:**
1. Carregue celular 100%.
2. Modo avião ON.
3. Inicie gravação no app.
4. Bloqueie tela.
5. Coloque o celular do lado.
6. Espere 30 minutos.
7. Volte, pare, verifique:
   - Tempo total = 30min ± 10s.
   - Todos os chunks íntegros (60 chunks de 30s).
   - Bateria consumida < 5%.
   - Sem crash, sem perda.

Se passar: 🎉 — o coração do app funciona.
Se falhar: investigar (logs, crash dump, comparação com docs/03).

---

## 14.8. Dia 7 — Pausa, reflexão, planejamento Sprint 2

Estimativa: 2h.

Faça:
1. Update do `CHANGELOG.md` com tudo que conseguiu.
2. Commit + push de tudo.
3. Releitura do [docs/12](12-roadmap-riscos-decisoes-pendentes.md) §Sprint 2.
4. Lista de tasks pra próxima semana (use Issues do GitHub).
5. Descansa. Foi uma semana intensa.

---

## 14.9. Estado esperado ao final da semana 1

```
✅ Repositório criado, plano commitado
✅ Estrutura de pastas (app/, site/, docs/, .github/)
✅ Expo + RN + TypeScript + módulo nativo Kotlin compilando
✅ "Hello World" rodando em emulador
✅ Primeiro AudioRecord (10s WAV)
✅ CI verde (lint + build APK debug)
✅ Foreground Service rodando, chunking funcional
✅ Stress test 30min passou
⏳ Próximo: Sprint 2 (robustez)
```

Você terá saído do "tenho um plano" para "tenho um app que grava em um celular real". Esse é o salto mais importante.

---

## 14.10. Quando pedir ajuda (e como)

Em qualquer ponto que ficar travado:

### Para questões técnicas específicas
- Stack Overflow / GitHub Issues do whisper.cpp / sherpa-onnx / Expo.
- Discord do Expo (super ativo).
- Documentação oficial Android (sempre primeiro).

### Para revisão do plano ou decisões grandes
- Volta aqui (Cowork). Manda o problema ou trecho de código.
- Posso revisar arquitetura, sugerir alternativas, debugar problemas conceituais.

### Para "isso é normal" check
- Comparar com apps similares (mas pague atenção: a maioria não faz o que você quer).
- Reddit `/r/androiddev`, `/r/reactnative`.

---

## 14.11. Checklist mental antes de cada sprint

```
□ Reli o doc da sprint atual em /docs?
□ Tenho clareza dos critérios de aceitação?
□ Configurei o ambiente (modelos baixados, devices conectados)?
□ Cri a branch `feature/sprint-N-<nome>`?
□ Atualizei TASKS.md com o que vou fazer?
□ Defini um marco "PR mergeável" pra metade da sprint?
□ Saber quando vou parar pra testar manual em device real?
```

---

## 14.12. Anti-padrões a evitar

❌ **Querer fazer tudo perfeito antes de mostrar.** Faça MVP feio que funciona, polir depois.

❌ **Pular pra v1.3 antes de v1.0 estar sólida.** Tentar "já adiantar a IA" enquanto gravação ainda falha = receita de desastre.

❌ **Adicionar dependências sem necessidade.** Cada lib é peso, risco e manutenção.

❌ **Refatorar enquanto adiciona feature.** Faça uma coisa de cada vez.

❌ **Pular testes "porque é simples".** O código mais simples é o que mais quebra silenciosamente em produção.

❌ **Não testar em device real.** Emulador mente sobre bateria, OEM behavior, performance.

❌ **Esconder problemas em logs verbose.** Resolva ou marque como TODO claro com data.

❌ **Não documentar decisões.** Vai esquecer em 2 semanas por que escolheu X em vez de Y.

---

## 14.13. Frase final

> Esse projeto é grande. Mas é construível. Comece pequeno, valide cedo, fase a fase, com você usando o app você mesmo desde a v1.0. Cada release é um produto que funciona sozinho. Não tenha pressa de lançar v1.3 — tenha pressa de ter v1.0 confiável.
