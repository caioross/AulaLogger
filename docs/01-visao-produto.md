# 01 — Visão de produto, personas e princípios

> Antes de mergulhar em arquitetura e código, este documento alinha "por que" o AulaLogger existe, "para quem" ele é, e "o que ele não é". Decisões técnicas em todos os outros documentos referenciam estes princípios.

---

## 1.1. Problema que estamos resolvendo

Professores, instrutores, palestrantes e tutores que dão aulas longas (1h–6h) hoje têm três frustrações reais:

1. **Esquecem o que falaram.** No final de uma aula de 4h, você lembra dos pontos altos, mas não lembra de cada definição, exemplo, dúvida que apareceu, gancho que abriu sem fechar.
2. **Não conseguem refletir sobre como ensinaram.** Você dá uma aula, sente que foi boa ou ruim, mas raramente tem dado para entender por quê. Quanto tempo passou em cada tópico? Você falou demais? Os alunos pararam de perguntar quando? Que jargão você usou sem explicar?
3. **Querem registro durável e pesquisável.** Quando um aluno pergunta "professor, na aula 3 você comentou sobre X, pode mandar de novo?", você não tem como achar isso a não ser ouvindo a aula inteira.

As soluções existentes hoje resolvem **uma** dessas frustrações:
- Gravadores de voz comuns: resolvem (1) parcialmente, mas não (2) nem (3).
- Otter.ai, Fireflies, etc: resolvem (1) e (3), mas mandam áudio para nuvem (problema com áudio de alunos), não funcionam offline, são caros e não são pensados para aula (são para reunião corporativa).
- Zoom/Google Meet com transcrição: só online, exige plataforma, não serve para aula presencial.

**Não existe um produto bom para professor presencial que queira gravar+transcrever+analisar localmente.** É essa lacuna que o AulaLogger preenche.

---

## 1.2. Personas

### Persona principal — "Caio, instrutor multi-disciplinar"

> Versão genérica de você. Esse perfil dirige o design.

- Dá várias aulas por semana, presencial ou em videochamada compartilhando tela.
- Aulas duram 2h–6h.
- Conteúdo muda toda semana: hoje é Excel avançado, amanhã é Python intermediário, depois fundamentos de IA.
- Quer melhorar como ensina, não só "ter o material".
- Tecnológico, confortável com configurar coisas, mas não quer ficar babysitting o app durante a aula.
- Valoriza privacidade — o áudio dos alunos não pode vazar.
- Tem celular Android moderno (não topo de linha).

**O que essa persona PRECISA:**
- Apertar 1 botão e esquecer que o app existe pelas próximas 4h.
- No final, ter áudio + transcrição prontos.
- Em uns minutos/horas depois, ter análise inteligente da aula.
- Achar coisas em aulas antigas via busca.

### Persona secundária — "Marina, professora universitária"

- Dá aulas presenciais de 2h, 2x por semana.
- Quer transformar aulas em material de estudo para alunos.
- Não vai usar funcionalidade avançada de análise — quer só áudio + transcrição limpa.
- Não é tão técnica. Precisa que "instalar e usar" seja trivial.

### Persona terciária — "Rafael, palestrante e coach"

- Dá palestras únicas, eventos, workshops.
- Quer rever a própria performance: o quanto falou, ritmo, jargão.
- Análise IA é o feature principal pra ele, mais do que o registro em si.

### Persona não-alvo — "Empresa que quer transcrever reuniões"

- Não estamos fazendo um app de reunião. Apesar de servir para isso, não vamos otimizar UX para esse caso.
- Multi-microfone, múltiplos canais, integração com Zoom — não.

### Persona não-alvo — "Jornalista que entrevista pessoas"

- Caso de uso muito próximo, mas com expectativas diferentes (entrevistas curtas, diarização perfeita de 2 pessoas conhecidas, interface diferente). Pode usar o app, mas não é nosso target.

---

## 1.3. Casos de uso ranqueados

| # | Caso de uso | Frequência | Importância | Cobertura no v1.x |
|---|-------------|------------|-------------|-------------------|
| 1 | Iniciar gravação de aula longa e ela não falhar | Toda aula | 🔴 Crítico | v1.0 |
| 2 | Encontrar uma aula passada na biblioteca | Recorrente | 🔴 Crítico | v1.0 |
| 3 | Reproduzir trecho específico de uma aula | Recorrente | 🟡 Alto | v1.0 |
| 4 | Exportar áudio para enviar a alguém | Ocasional | 🟢 Médio | v1.0 |
| 5 | Ler transcrição completa pós-aula | Toda aula | 🔴 Crítico | v1.1 |
| 6 | Buscar palavra/conceito em aulas anteriores | Recorrente | 🔴 Crítico | v1.1 |
| 7 | Exportar transcrição (PDF/DOCX) | Recorrente | 🟡 Alto | v1.1 |
| 8 | Ver no transcript "quem falou cada coisa" | Toda aula | 🟡 Alto | v1.2 |
| 9 | Cadastrar minha voz para diarização precisa | Uma vez | 🟡 Alto | v1.2 |
| 10 | Receber resumo automático da aula | Toda aula | 🟡 Alto | v1.3 |
| 11 | Receber insights pedagógicos (ritmo, jargão, etc) | Toda aula | 🟢 Médio | v1.3 |
| 12 | Receber alerta em tempo real ("você tá há 8min sem fazer pausa") | Recorrente | 🟢 Médio | v1.3 |
| 13 | Pedir análise mais profunda usando IA em nuvem | Ocasional | 🟢 Médio | v1.3 |
| 14 | Comparar várias aulas (ex: ritmo médio, palavras favoritas) | Ocasional | 🟢 Baixo | v2.0+ |
| 15 | Compartilhar transcrição com aluno via link | Ocasional | 🟢 Baixo | v2.0+ |

---

## 1.4. Princípios não-negociáveis (versão estendida)

Estes princípios estão no plano principal, mas aqui detalho **como cada um se traduz em decisão concreta**:

### P1. Confiabilidade absoluta da gravação

- **Tradução técnica:** A gravação é feita por um Foreground Service nativo Android (Kotlin), não por código JavaScript do React Native. O service tem prioridade alta, mantém wake lock parcial, e escreve em disco a cada 30s em arquivos chunk.
- **Tradução de UX:** A tela de gravação mostra explicitamente "Gravando há 1:23:45 — 87 chunks salvos — última gravação salva há 12 segundos". Sem ambiguidade.
- **Critério de teste:** uma sessão de 4h em celular médio (Snapdragon 6xx, 6GB RAM) com tela apagada e bateria começando em 50% deve produzir áudio íntegro do início ao fim, sem buracos > 100ms.

### P2. Privacidade por padrão

- **Tradução técnica:** Nenhuma chamada de rede acontece sem ação explícita. Cloud LLM é opt-in com tela clara. Áudio nunca é enviado para nuvem (nem para Whisper API). Dados em disco são criptografados via Android Keystore.
- **Tradução de UX:** Onboarding explica isso na primeira tela. Configurações têm um painel "Privacidade" mostrando exatamente o que sai do dispositivo.
- **Critério de teste:** monitorar tráfego de rede com app em uso — em modo padrão, **zero requisições externas** durante uma aula completa.

### P3. Funciona offline

- **Tradução técnica:** Modelos de Whisper, diarização e LLM ficam em disco (download na primeira execução). Após download inicial, app funciona em modo avião.
- **Tradução de UX:** Indicador de "modo offline" não é alarme, é status normal.
- **Critério de teste:** após primeiro setup, usar app em modo avião por uma aula completa, fazer transcrição, fazer análise local — tudo deve funcionar.

### P4. Flexível ao conteúdo

- **Tradução técnica:** Prompts da IA não mencionam matéria específica. Nenhum vocabulário hardcoded. O usuário pode opcionalmente cadastrar "vocabulário customizado" para boost de transcrição (ex: "VLOOKUP", "matplotlib"), mas nada vem pré-configurado.
- **Tradução de UX:** Nenhum tutorial assume "sua aula de X". Ícones e linguagem são neutros (não usar imagens de Excel, Python, etc).

### P5. Recuperável a falhas

- **Tradução técnica:** Cada chunk de 30s é independente e pode ser reproduzido sozinho. Metadata da sessão é atualizada após cada chunk. Se o app crash, ao reabrir ele detecta sessões "abertas" e oferece recuperar.
- **Tradução de UX:** Tela de "recuperação" pós-crash é clara e tranquila, não alarmante. "Encontramos uma sessão de 1h47 do dia X que parece interrompida. Quer recuperar?"
- **Critério de teste:** simular crashes (kill process, OOM, low storage) em vários momentos da gravação. Em todos, recuperar tudo o que foi escrito até o último chunk.

### P6. UI reversível e segura

- **Tradução técnica:** Deletes são "soft delete" por 30 dias. "Esvaziar lixeira" é a única ação irreversível e exige confirmação dupla.
- **Tradução de UX:** Nenhum botão grande de "deletar". Sempre swipe → confirmar.

### P7. Performance honesta

- **Tradução técnica:** Toda operação > 3s tem barra de progresso real (não spinner). Toda operação > 30s tem ETA. Background tasks notificam quando terminam.
- **Tradução de UX:** Mensagens são literais: "Transcrevendo aula de 4h12 — estimado 1h40 restantes — usando modelo Whisper Small".

### P8. Configurável com defaults sensatos

- **Tradução técnica:** Settings em camadas. Tela "Configurações" tem ~6 itens pra usuário casual; aba "Avançado" desbloqueia ~30 ajustes finos.
- **Tradução de UX:** Defaults funcionam para 90% dos casos. Avançado é claramente "se você sabe o que está fazendo".

---

## 1.5. Métricas de sucesso

Como vamos saber se o app está bom? (Métricas para ti, Caio — não telemetria do app.)

### Métricas de v1.0 (gravação)

- 🎯 **Sessão de 4h+ sem perda de áudio** em pelo menos 5 celulares Android diferentes (incluindo Xiaomi, Samsung, Motorola, Pixel).
- 🎯 **Tempo até primeira gravação útil** após instalar o app: < 60 segundos.
- 🎯 **Bateria consumida em 1h de gravação** com tela apagada: < 8%.

### Métricas de v1.1 (transcrição)

- 🎯 **WER (Word Error Rate) em PT-BR técnico** com modelo Whisper Small: < 12%.
- 🎯 **Tempo de transcrição** de uma aula de 4h em celular médio: < 4h (idealmente em background enquanto carrega).
- 🎯 **Busca em transcrição** retorna em < 500ms.

### Métricas de v1.2 (diarização)

- 🎯 **DER (Diarization Error Rate)** com 1 professor + até 5 alunos: < 20%.
- 🎯 **Identificação correta da voz do professor** após enrollment: > 95%.

### Métricas de v1.3 (IA)

- 🎯 **Resumo gerado é considerado útil** pelo professor: avaliação subjetiva > 4/5.
- 🎯 **Tempo de geração de resumo** com LLM local em celular médio: < 10 minutos para uma aula de 4h.
- 🎯 **Custos de IA em nuvem** (se usuário ativar): médio < R$ 1 por aula via Claude Haiku.

---

## 1.6. Fora de escopo (explicitamente NÃO faremos)

Para evitar scope creep, deixamos claro o que **não** estamos fazendo:

- **Não é app de reunião.** Sem detecção de quem entrou/saiu de call, sem integração com Zoom/Meet/Teams.
- **Não é editor de áudio.** Você pode reproduzir e exportar, mas não editar (cortar, juntar, equalizar). Use Audacity ou similar.
- **Não é plataforma educacional.** Sem turmas, sem alunos cadastrados, sem entrega de tarefa. É uma ferramenta pessoal do instrutor.
- **Não é tradutor.** Transcreve no idioma falado, ponto. Tradução pode entrar em v2+.
- **Não é app de notas escritas.** Você pode adicionar marcações na timeline durante/após gravação, mas não é Notion ou Obsidian.
- **É exclusivamente Android.** Sem iOS, nem nos planos futuros.
- **Não é desktop.** App é mobile. Companion web pode entrar em v2 (só leitura de gravações sincronizadas).
- **Não tem login/conta.** Sem usuários, sem nuvem própria. Tudo é local + opt-in cloud LLM. Talvez v2+.
- **Não tem features sociais.** Sem compartilhamento público, sem feed, sem follows.
- **Não tem ads, nunca.**

---

## 1.7. Glossário

- **Diarização (diarization):** processo de segmentar áudio por quem está falando (sem necessariamente saber a identidade — só "speaker A, B, C").
- **Speaker enrollment:** processo de cadastrar a voz de uma pessoa específica para que ela seja identificada por nome em vez de "speaker A".
- **VAD (Voice Activity Detection):** detector de "tem voz aqui ou é silêncio/ruído".
- **WER (Word Error Rate):** porcentagem de palavras erradas na transcrição vs. transcrição perfeita.
- **DER (Diarization Error Rate):** porcentagem do tempo em que a atribuição de speaker está errada.
- **Foreground service:** componente Android que roda em segundo plano com prioridade alta e notificação obrigatória, sobrevivendo a tela apagada e Doze.
- **Doze mode:** modo de economia de bateria do Android que pausa apps em background.
- **Quantização:** processo de reduzir precisão dos pesos de um modelo (16-bit → 4-bit) para caber em menos memória, com pequena perda de qualidade.
- **NNAPI:** Neural Networks API do Android, permite acelerar modelos via NPU/GPU.
- **JNI:** Java Native Interface, ponte para chamar código C/C++ do Java/Kotlin (usado para whisper.cpp, llama.cpp).
