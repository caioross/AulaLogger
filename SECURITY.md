# Política de Segurança

## Versões com suporte

| Versão | Suporte de segurança |
|--------|----------------------|
| Pré-release (atual) | ✅ |
| < 1.0 | ❌ |

Após o lançamento da v1.0, esta tabela será atualizada para refletir versões em suporte ativo.

## Reportar uma vulnerabilidade

**Não abra um Issue público para reportar vulnerabilidades.**

Em vez disso:

1. Envie um email para: `security@aulalogger.com.br` (a ser configurado)
2. Inclua no email:
   - Descrição da vulnerabilidade
   - Passos para reproduzir
   - Impacto potencial
   - Sugestão de correção (se tiver)
   - Se você quer reconhecimento público pelo report
3. Aguarde resposta inicial em até 72 horas

## Compromisso de resposta

| Severidade | Resposta inicial | Patch público |
|------------|-------------------|---------------|
| Crítica (vazamento de dados, RCE) | < 24h | < 7 dias |
| Alta (escalação de privilégio, DoS) | < 72h | < 30 dias |
| Média (info disclosure não-sensível) | < 7 dias | < 90 dias |
| Baixa (boas práticas) | < 30 dias | próximo release |

## Escopo

### Em escopo

- Aplicativo Android AulaLogger (todas as versões em suporte)
- Site `aulalogger.com.br` e subdomínios oficiais
- Módulos nativos distribuídos (`aulalogger-native`)
- Scripts e workflows do repositório

### Fora de escopo

- Provedores cloud LLM (Anthropic, OpenAI, Google) — reporte direto a eles
- Sistema operacional Android — reporte ao Google
- Bibliotecas third-party (whisper.cpp, llama.cpp, sherpa-onnx, RNNoise) — reporte aos mantenedores upstream, mas nos avise
- Vulnerabilidades em forks ou versões modificadas não-oficiais

## Reconhecimento

Pesquisadores que reportarem vulnerabilidades responsavelmente terão crédito público:

- Hall of fame em [SECURITY-HALL-OF-FAME.md](SECURITY-HALL-OF-FAME.md) (a criar)
- Menção no CHANGELOG do release que contém a correção
- Opção de anonimato sempre disponível

## Disclosure responsável

Pedimos que você:

1. Dê tempo razoável para corrigirmos antes de divulgar publicamente
2. Não explore a vulnerabilidade além do necessário para demonstrar
3. Não acesse dados de outros usuários
4. Não execute ataques DoS

Em troca, comprometemo-nos a:

1. Responder rapidamente
2. Manter você informado do progresso
3. Dar crédito conforme você desejar
4. Não tomar ação legal contra disclosure ético

## Práticas de segurança no projeto

- Code review obrigatório em PRs
- Dependabot ativo para CVEs em dependências
- Dependências pinadas (`package-lock.json`, `gradle.lockfile`)
- Builds reprodutíveis (alvo: F-Droid compliance)
- APKs assinados com chave única, controlada
- Sem telemetria, sem coleta de dados
- API keys em EncryptedSharedPreferences
- Certificate pinning para APIs cloud

Mais detalhes em [docs/13-seguranca-testes-conformidade.md](docs/13-seguranca-testes-conformidade.md).
