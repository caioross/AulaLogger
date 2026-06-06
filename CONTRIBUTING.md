# Como contribuir para o AulaLogger

Obrigado pelo interesse em contribuir!

## Antes de começar

Este projeto está em fase inicial. Antes de abrir um PR grande, **abra uma Issue** discutindo a proposta — isso evita trabalho jogado fora.

## Tipos de contribuição

### Reportar bugs

Use [GitHub Issues](../../issues) com o template "Bug report".

Inclua:

- Versão do app (Configurações > Sobre)
- Modelo do celular e versão Android
- Passos exatos para reproduzir
- O que aconteceu vs. o que era esperado
- Logs relevantes (Configurações > Sobre > Diagnóstico > Exportar)
- Não inclua áudio ou texto de aulas — apenas logs técnicos

### Sugerir features

Use Issues com template "Feature request". Descreva:

- O problema que você está tentando resolver
- A solução que você imagina
- Alternativas consideradas
- Quem mais pode se beneficiar

### Enviar pull request

1. Faça fork do repositório
2. Crie branch a partir de `main`:
   - `feature/<descrição-curta>` para features
   - `fix/<descrição-curta>` para correções
   - `docs/<descrição-curta>` para documentação
3. Faça commits pequenos e descritivos (Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`)
4. Adicione testes para o que você mudou
5. Rode antes de abrir PR:
   ```
   cd app && npm run typecheck && npm run lint && npm test
   cd app/android && ./gradlew test
   ```
6. Abra PR descrevendo a mudança e linkando a Issue relacionada

## Setup de desenvolvimento local

Veja [BUILD.md](BUILD.md).

## Diretrizes de código

### TypeScript / JavaScript

- TypeScript estrito (`strict: true`)
- ESLint + Prettier configurados — rode `npm run lint:fix`
- Componentes React funcionais com hooks
- Sem `any` exceto justificado em comentário
- Schemas Zod para validação de dados externos

### Kotlin

- Kotlin idiomático (data classes, extension functions, scope functions)
- ktlint para formatação
- Coroutines para async, sem `runBlocking` em produção
- Sem `!!` exceto justificado
- Documentação KDoc em APIs públicas

### Commits

Conventional Commits:

```
feat(recording): adicionar suporte a pause/resume
fix(transcription): corrigir overlap entre chunks
docs(readme): atualizar instruções de build
test(audio): adicionar testes para RNNoise wrapper
refactor(storage): extrair RecordingRepository
chore(deps): atualizar Expo SDK para 53
```

## Estrutura do projeto

Veja [docs/02-arquitetura-tecnica.md](docs/02-arquitetura-tecnica.md) para entender como tudo se encaixa.

## Áreas onde precisamos de ajuda

Procure por issues marcadas:

- `good first issue` — boas para iniciantes
- `help wanted` — qualquer pessoa pode pegar
- `hacktoberfest` — durante outubro

## Tradução

Ajude a traduzir o app:

1. Copie `app/src/i18n/pt-BR.json` para `app/src/i18n/<seu-idioma>.json`
2. Traduza as chaves
3. Abra PR

## Modelos ML

Se você tem expertise em ML e quer contribuir com:

- Modelos otimizados para Android (Whisper, Llama, Sherpa)
- Avaliação de qualidade (WER, DER)
- Prompts para análise pedagógica

... abra uma Issue! Estamos especialmente interessados.

## Code of Conduct

Este projeto segue o [Contributor Covenant](CODE_OF_CONDUCT.md). Comportamento respeitoso é mandatório.

## Licença

Ao contribuir, você concorda que suas contribuições serão licenciadas sob a [GPL-3.0](LICENSE).

## Contato

- Issues / Discussions: GitHub
- Email para questões sensíveis: a definir
