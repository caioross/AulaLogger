# 📦 Guia de Publicação na Google Play — AulaLogger

Tudo o que você precisa para subir o app na Play Store: o **arquivo (AAB)**, os
**textos** para preencher cada campo, as **imagens** nos formatos exigidos e as
**respostas** dos formulários de conformidade (Data Safety, permissões,
classificação). Siga na ordem.

> **Versão:** 0.7.4 (versionCode 11) · **Pacote:** `com.aulalogger`
> **Gerado em:** junho/2026

---

## 0) Resumo da prontidão

### ✅ O que já foi corrigido para passar na revisão
| Item | Antes | Agora |
|---|---|---|
| **Assinatura** | APK assinado com chave **debug** (a Play rejeita) | Chave de **upload** RSA-4096 dedicada + assinatura de release configurada |
| **targetSdk** | 34 | **35 (Android 15)** — exigido pela Play para apps novos |
| **16 KB page size** | libs nativas em 4 KB | NDK r26 + flags de linker `max-page-size=16384` (exigência Android 15 p/ código nativo) |
| **Formato** | APK | **AAB** (Android App Bundle), obrigatório para apps novos |
| **Segredos** | — | keystore e senhas fora do versionamento (`.gitignore`) |

### ⚠️ O que VOCÊ precisa decidir/fazer (detalhado abaixo)
1. **Conta Google Play Console** (taxa única de US$ 25) e criar o app.
2. **Ativar o Play App Signing** (recomendado) ao subir o primeiro AAB.
3. Preencher **ficha da loja**, **Data Safety**, **classificação** e **declaração de permissões** (textos prontos abaixo).
4. **Recomendado:** lançar primeiro em **Teste Fechado/Aberto** (é um beta 0.7.x), não direto em Produção.

### 🔎 Pontos de atenção na revisão (médio risco — esteja preparado para justificar)
- **Permissão de ignorar otimização de bateria** e **serviços em primeiro plano** (microfone/dataSync) → exigem justificativa (textos prontos na seção 6). Tenha um vídeo curto da gravação à mão, caso peçam.

---

## 1) O arquivo para upload (AAB)

- **Caminho:** `app/app/build/outputs/bundle/release/app-release.aab`
- Para regerar: `cd app && ./gradlew bundleRelease` (precisa do `keystore.properties` — ver seção 2).
- O **mapping do R8** (desofuscação de crashes) sai em
  `app/app/build/outputs/mapping/release/mapping.txt` → o Play Console aceita upload junto (recomendado).

> A Play Store **não aceita APK** para apps novos — use o `.aab`. O Google gera os APKs otimizados por aparelho a partir dele.

---

## 2) Assinatura e chave (LEIA COM ATENÇÃO)

Foi gerada uma **chave de upload** dedicada:

- Keystore: `app/upload-keystore.jks` (RSA 4096, validade ~27 anos)
- Alias: `aulalogger-upload`
- Credenciais: em `app/keystore.properties` (**fora do git** — ver `.gitignore`)

### 🔐 Faça AGORA
1. **Faça backup** de `upload-keystore.jks` e do `keystore.properties` num lugar seguro (gerenciador de senhas / cofre). Sem eles, você não assina novas versões.
2. **Ative o Play App Signing** no Play Console (é o padrão hoje). Assim o Google guarda a *chave de assinatura do app* e a sua *chave de upload* vira **recuperável** se você perdê-la.
3. (Opcional, mas ideal) troque as senhas do keystore por uma sua. As senhas atuais estão no `keystore.properties` local — me avise se quiser que eu gere uma chave nova com senha definida por você.

> Se preferir gerar a chave você mesmo:
> ```bash
> keytool -genkeypair -v -keystore upload-keystore.jks -alias aulalogger-upload \
>   -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
> ```

---

## 3) Ficha da loja (Store listing) — textos prontos

### Nome do app (máx. 30 caracteres)
```
AulaLogger
```
*(alternativa com palavra-chave, 24/30): `AulaLogger: gravar aulas`)*

### Descrição curta (máx. 80 caracteres)
```
Grave, transcreva e analise aulas longas no seu celular. Offline e privado.
```

### Descrição completa (máx. 4000 caracteres)
```
O AulaLogger transforma suas aulas em conhecimento pesquisável — sem nuvem, sem assinatura e sem entregar o áudio dos seus alunos para ninguém.

Feito para professores, instrutores, tutores e palestrantes que dão aulas longas e querem um registro inteligente do que ensinaram.

▶ GRAVAÇÃO QUE NÃO FALHA
Grave aulas de 4 horas ou mais com a tela apagada, em qualquer celular. Um serviço nativo grava direto no armazenamento, com salvamento contínuo: se o app fechar no meio, você recupera tudo até o último segundo. Tem pausar, retomar e um atalho na tela inicial.

▶ TRANSCRIÇÃO NO PRÓPRIO APARELHO
O reconhecimento de voz (Whisper) roda dentro do seu celular. Sem upload, sem custo por minuto, funciona até em modo avião. O app escolhe automaticamente o melhor modelo para a memória do seu aparelho e pula os silêncios para transcrever mais rápido.

▶ SABE QUEM FALOU
Separa os trechos por locutor, para você acompanhar a conversa entre professor e alunos e reler com clareza.

▶ ANÁLISE PEDAGÓGICA COM IA (OPCIONAL)
Gere um resumo da aula, os tópicos abordados, pontos fortes e sugestões para a próxima aula. Você usa a IA da nuvem que preferir (Claude, OpenAI, Gemini ou OpenRouter) com a SUA própria chave — e apenas o texto da transcrição é enviado, nunca o áudio.

▶ 100% OFFLINE E PRIVADO
• O áudio e a transcrição nunca saem do seu celular.
• Zero telemetria, zero anúncios, nenhum servidor nosso.
• Código aberto (GPL-3.0), auditável por qualquer pessoa.
• A nuvem é opcional e só é usada se você ativar.

PARA QUEM É
Professores presenciais e online, tutores particulares, palestrantes e coaches — qualquer pessoa que fala muito e quer registrar e refletir sobre o que ensinou.

IMPORTANTE
• Requer Android 10 ou superior e cerca de 4 GB de RAM para a transcrição local.
• Ao gravar aulas com outras pessoas, avise e obtenha consentimento, conforme a LGPD.
• App em evolução: sua opinião ajuda a melhorar.

Política de privacidade: https://aulalogger.com.br/privacy
```

### Outros campos da ficha
| Campo | Valor sugerido |
|---|---|
| Categoria do app | **Educação** (alternativa: Produtividade) |
| Tags | gravador de voz, transcrição, aulas, produtividade, educação |
| E-mail de contato | *(defina um e-mail público — sugiro um dedicado, ex. `contato@aulalogger.com.br`; evite usar seu e-mail pessoal)* |
| Site | `https://aulalogger.com.br` |
| Política de privacidade | `https://aulalogger.com.br/privacy` |
| País/idioma padrão | Brasil / Português (Brasil) |

---

## 4) Recursos gráficos — arquivos prontos em `playstore/assets/`

| Recurso | Especificação exigida | Arquivo gerado | Status |
|---|---|---|---|
| **Ícone do app** | 512 × 512 px, PNG 32-bit, ≤ 1 MB | `assets/icon-512.png` | ✅ pronto |
| **Feature graphic** | 1024 × 500 px, PNG/JPG, ≤ 1 MB | `assets/feature-graphic-1024x500.png` | ✅ pronto |
| **Screenshots de celular** | 2 a 8 imagens, PNG/JPG 24-bit (sem alfa), lado 320–3840 px | `assets/screenshot-1..5-*.png` (1080×1920) | ✅ 5 prontas |
| Screenshots de tablet 7" | opcional | — | opcional |
| Screenshots de tablet 10" | opcional | — | opcional |
| Vídeo promocional | opcional (URL do YouTube) | — | opcional |

**Os 5 screenshots** (1080×1920, retrato 9:16):
1. `screenshot-1-recording.png` — Gravação
2. `screenshot-2-transcription.png` — Transcrição com locutores
3. `screenshot-3-analysis.png` — Análise com IA
4. `screenshot-4-privacy.png` — Privacidade/offline
5. `screenshot-5-home.png` — Biblioteca de aulas

> **Observação importante:** os screenshots foram desenhados fiéis às telas reais do app (cores, layout e textos do app). Eles atendem aos requisitos de formato/tamanho da Play. Para máxima fidelidade, **o ideal é substituí-los por capturas reais** do app rodando num aparelho/emulador antes do lançamento em Produção (veja "Como capturar reais" na seção 9). São perfeitamente utilizáveis para Teste Fechado/Aberto.

---

## 5) Segurança dos dados (Data Safety) — respostas

No Play Console → **Política → Segurança dos dados**. Respostas alinhadas ao comportamento real do app:

**Pergunta inicial — "Seu app coleta ou compartilha dados do usuário?"**
→ **Sim** (por causa do recurso opcional de IA em nuvem). Detalhe assim:

| Tipo de dado | Coletado? | Compartilhado? | Detalhe |
|---|---|---|---|
| **Gravações de áudio / voz** | **Não** | **Não** | O áudio é criado e fica **só no dispositivo**. Nunca é transmitido. |
| **Texto da transcrição** (em "Outros" / conteúdo do usuário) | Não | **Sim — somente se o usuário ativar a IA em nuvem** | Enviado ao provedor escolhido pelo usuário (Claude/OpenAI/Gemini/OpenRouter) com a chave do próprio usuário. Ação iniciada pelo usuário. |

**Detalhes a marcar:**
- **Coleta de dados pelo desenvolvedor:** Nenhuma. Não temos servidor, não temos analytics, não temos telemetria.
- **Os dados são criptografados em trânsito?** **Sim** (HTTPS para o provedor de IA, quando usado).
- **O usuário pode pedir exclusão dos dados?** **Sim** — tudo é local; o app tem opção de apagar todas as aulas/dados. Sem conta de usuário.
- **Compartilhamento:** marque que o texto da transcrição **pode** ser compartilhado com um serviço de terceiros **a pedido do usuário**, para o recurso de análise opcional.

> Resumo honesto para a aba pública: *"O AulaLogger não coleta dados. Áudio e transcrições ficam no seu aparelho. Se você ativar a análise em nuvem, apenas o texto da transcrição é enviado ao provedor de IA que você escolher, com a sua chave."*

---

## 6) Declaração de permissões sensíveis

No Play Console → **Política → Conteúdo do app → Permissões e APIs sensíveis** e **Serviços em primeiro plano**.

### a) Serviço em primeiro plano — tipo "microphone"
**Justificativa (cole):**
```
O app grava o áudio de aulas longas (4h+) em um serviço em primeiro plano para
que a gravação continue de forma confiável com a tela bloqueada e o app em
segundo plano. O usuário inicia a gravação explicitamente e vê uma notificação
persistente enquanto ela ocorre. O microfone é usado apenas para essa gravação,
iniciada pelo usuário.
```

### b) Serviço em primeiro plano — tipo "dataSync"
**Justificativa (cole):**
```
Após a gravação, o app transcreve o áudio localmente (no próprio aparelho, com
Whisper). Esse processamento roda em um serviço em primeiro plano de
sincronização de dados para concluir de forma confiável em arquivos longos,
exibindo progresso e tempo restante. Nenhum dado é enviado para a internet nesse
processo.
```

### c) Permissão `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
**Justificativa (cole):**
```
A função principal do app é a gravação ininterrupta de aulas de 4 horas ou mais.
Em vários fabricantes (Xiaomi, Samsung, Huawei, etc.), a otimização agressiva de
bateria encerra o serviço de gravação em segundo plano, causando perda de áudio.
A permissão apenas exibe o diálogo padrão do sistema para o usuário autorizar a
isenção; é opcional e o usuário pode recusar.
```
> ⚠️ **Maior risco de revisão.** Se o Google questionar/rejeitar, a saída é remover essa permissão (o serviço em primeiro plano de microfone sozinho já mantém a gravação na maioria dos aparelhos). Me avise que eu removo e regenero o AAB.

---

## 7) Classificação de conteúdo (IARC)

No Play Console → **Política → Classificação de conteúdo**. Respostas sugeridas:
- Categoria do questionário: **Aplicativo (Utilitário/Produtividade/Educação)** — não é jogo.
- Violência, sexo, drogas, linguagem imprópria, jogos de azar: **Não** para todos.
- O app permite compartilhar conteúdo / interagir com usuários? O usuário pode **compartilhar** a própria gravação/transcrição (via menu de compartilhar do Android). Responda conforme o questionário pedir.
- Coleta de localização: **Não**.
- Resultado esperado: **Livre / PEGI 3 / Everyone**.

---

## 8) Público-alvo, acesso e anúncios

- **Público-alvo:** marque faixas adultas (**18+** / "não destinado a crianças"). O app grava áudio e integra IA — não é direcionado a menores.
- **App access (acesso para revisão):** marque **"Todas as funcionalidades disponíveis sem restrições"** — não há login nem conta. (O recurso de IA em nuvem é opcional e usa chave do próprio usuário; não é necessário fornecer credenciais ao Google.)
- **Anúncios:** **Não contém anúncios.**
- **Notícias/COVID/Finanças/Saúde:** não se aplica.

---

## 9) Passo a passo no Play Console

1. **Criar app**: nome `AulaLogger`, idioma padrão Português (Brasil), tipo App, Gratuito.
2. **Configuração do app** (painel "Painel"): preencher os itens obrigatórios:
   - Política de privacidade → `https://aulalogger.com.br/privacy`
   - App access → sem restrições
   - Anúncios → não
   - Classificação de conteúdo → questionário (seção 7)
   - Público-alvo → 18+ (seção 8)
   - Segurança dos dados → seção 5
   - Permissões sensíveis / serviços em primeiro plano → seção 6
3. **Ficha da loja principal**: textos da seção 3 + imagens da seção 4.
4. **Criar release**:
   - Comece em **Teste fechado** (recomendado p/ um beta) → suba `app-release.aab`.
   - Aceite ativar o **Play App Signing**.
   - Suba o `mapping.txt` (desofuscação).
   - Cole as **notas da versão** (abaixo).
5. **Enviar para revisão.** A primeira revisão costuma levar de algumas horas a alguns dias.

### Como capturar screenshots reais (opcional, recomendado p/ Produção)
```bash
# com o app instalado num aparelho/emulador Android 10+:
adb exec-out screencap -p > screenshot.png
```
Capture as telas: gravação, detalhe da aula (transcrição), análise, biblioteca. Tamanho mínimo 1080×1920.

---

## 10) Notas da versão (What's new)

**pt-BR:**
```
Primeira versão pública (beta). Gravação confiável de aulas de 4h+, transcrição
no próprio aparelho (offline) e análise opcional com IA usando a sua chave.
100% offline e privado. Feedback é muito bem-vindo!
```
**en-US:**
```
First public release (beta). Reliable 4h+ lecture recording, on-device
transcription (offline), and optional AI analysis with your own key.
100% offline and private. Feedback is very welcome!
```

---

## 11) Ficha em inglês (en-US) — para alcançar mais usuários (opcional)

**Short description (≤80):**
```
Record, transcribe and analyze long lectures on your phone. Offline & private.
```
**Full description:** *(tradução da seção 3 — me peça que eu gero o texto completo em EN se quiser publicar em inglês também)*

---

## 12) Checklist final antes de enviar

- [ ] AAB gerado e assinado com a chave de upload (`app-release.aab`)
- [ ] Backup do keystore + senhas feito em local seguro
- [ ] Play App Signing ativado no primeiro upload
- [ ] Ícone 512, feature graphic e ≥2 screenshots enviados
- [ ] Descrições curta e completa coladas
- [ ] Data Safety preenchido conforme seção 5
- [ ] Declaração de serviços em primeiro plano (microfone + dataSync) e bateria
- [ ] Classificação de conteúdo respondida
- [ ] Público-alvo (18+) e App access (sem restrições) marcados
- [ ] Política de privacidade acessível em https://aulalogger.com.br/privacy
- [ ] Release em Teste Fechado/Aberto (recomendado antes de Produção)
- [ ] `mapping.txt` enviado

---

## 13) Atenção técnica antes de enviar

- **Teste o AAB num aparelho real** antes de promover para Produção. Instale a
  build de release com o `bundletool`:
  ```bash
  bundletool build-apks --bundle=app-release.aab --output=app.apks \
    --ks=upload-keystore.jks --ks-key-alias=aulalogger-upload --mode=universal
  bundletool install-apks --apks=app.apks
  ```
  (ou use a trilha de **Teste Interno** do Play Console, que entrega o app pela própria Play).
- **Android 15 / edge-to-edge:** com `targetSdk 35`, o Android 15 força layout
  "edge-to-edge". Confira num aparelho/emulador Android 15 se nenhum conteúdo
  fica escondido atrás da barra de status/navegação. (O app usa Material 3, que
  trata insets na maioria dos casos — mas vale conferir.)
- **Modelos de IA baixados em runtime:** o app baixa os modelos do Whisper
  (~180–580 MB) na primeira utilização, não no APK. Isso mantém o AAB pequeno
  (~9 MB) e é permitido pela Play. O usuário é avisado antes do download.
- **16 KB confirmado:** todas as bibliotecas nativas do AAB estão alinhadas em
  16 KB (`c++` e OpenMP linkados estaticamente na `libwhisperjni.so`; sem
  `libc++_shared.so`/`libomp.so` avulsos).

---

> Dúvidas ou quiser que eu ajuste algo (texto, imagens, remover a permissão de bateria, gerar a ficha EN completa, capturar screenshots reais via emulador)? É só pedir.
