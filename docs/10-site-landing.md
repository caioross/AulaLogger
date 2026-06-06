# 10 — Site / Landing page e documentação

> O site público do AulaLogger: landing page para apresentar o produto, página de download, blog opcional, documentação técnica e do usuário.

---

## 10.1. Stack do site

| Item | Escolha | Justificativa |
|------|---------|---------------|
| Framework | **Astro 5+** | Static-first, SEO excelente, integra MDX para docs, build rápido, zero JS por default. |
| Estilização | **Tailwind CSS 4** | Padrão moderno, integração perfeita com Astro. |
| Componentes interativos | **Astro Islands + React** (só onde precisar) | Menus, comparações interativas. |
| Conteúdo | **MDX** em `src/content/` | Type-safe content collections do Astro. |
| Hospedagem | **Cloudflare Pages** ou **Vercel** | Free tier generoso, deploy via GitHub. |
| Domínio | A definir (P7) | Sugestões: `aulalogger.com.br`, `aulalogger.app`. |
| Analytics | **Plausible** (self-hosted) ou **nenhum** | Privacy-first. Recomendo nenhum no v1. |
| Forms (contato/newsletter) | **Formspark** ou **mailto** | Simples. |
| Search nas docs | **Pagefind** | Static search, integra com Astro, sem servidor. |

**Por que Astro e não Next.js?**
- Site é majoritariamente estático (landing + docs).
- Astro gera HTML puro sem hydration desnecessária.
- DX melhor para conteúdo via MDX.
- Lighthouse 100 fácil de atingir.
- Build mais rápido.

---

## 10.2. Estrutura do site

```
site/
├── package.json
├── astro.config.mjs
├── tailwind.config.mjs
├── tsconfig.json
├── public/
│   ├── favicon.svg
│   ├── og-image.png
│   ├── apk/                                  ← APKs hospedados aqui
│   │   ├── aulalogger-1.0.0.apk
│   │   ├── aulalogger-1.0.0.apk.sha256
│   │   ├── aulalogger-latest.apk             ← symlink ou redirect pra última versão
│   │   └── ...
│   └── images/
├── src/
│   ├── content/
│   │   ├── config.ts                         (content collections)
│   │   ├── docs/
│   │   │   ├── 01-introducao.mdx
│   │   │   ├── 02-instalacao.mdx
│   │   │   ├── 03-primeiros-passos.mdx
│   │   │   ├── 04-gravando-aulas.mdx
│   │   │   ├── 05-transcricao.mdx
│   │   │   ├── 06-diarizacao.mdx
│   │   │   ├── 07-analise-ia.mdx
│   │   │   ├── 08-configuracoes.mdx
│   │   │   ├── 09-exportacao.mdx
│   │   │   ├── 10-troubleshooting.mdx
│   │   │   └── 99-faq.mdx
│   │   └── blog/
│   │       └── (posts opcionais)
│   ├── components/
│   │   ├── Hero.astro
│   │   ├── Features.astro
│   │   ├── DownloadCard.astro
│   │   ├── ScreenshotGallery.astro
│   │   ├── ComparisonTable.astro
│   │   ├── InstallSteps.astro
│   │   ├── Footer.astro
│   │   ├── Header.astro
│   │   └── DocsSidebar.astro
│   ├── layouts/
│   │   ├── Base.astro
│   │   ├── Landing.astro
│   │   └── Docs.astro
│   └── pages/
│       ├── index.astro                       (landing)
│       ├── download.astro                     (página de download)
│       ├── docs/
│       │   ├── index.astro                    (índice docs)
│       │   └── [...slug].astro                (página dinâmica de doc)
│       ├── blog/
│       │   ├── index.astro
│       │   └── [...slug].astro
│       ├── privacy.astro                      (política de privacidade)
│       ├── terms.astro                        (termos)
│       ├── changelog.astro                    (releases)
│       └── 404.astro
└── README.md
```

---

## 10.3. Landing page (estrutura)

### Hero
```
            AulaLogger
   Grave, transcreva e analise suas aulas.
       Tudo no seu celular. Sem nuvem.

      [Baixar para Android]    [Ver no GitHub]

  ╭──────────────────────────────────────╮
  │                                       │
  │     [screenshot da tela de gravação] │
  │                                       │
  ╰──────────────────────────────────────╯
```

### Seção "O que faz"
```
Gravação que não falha.
Aulas de 4h+ sem perder um segundo.
[ícone] [ícone] [ícone]

Transcrição hiper-detalhada.
Whisper local, com identificação de quem falou.

Análise pedagógica com IA.
Resumo, tópicos, métricas, sem mandar nada pra nuvem.

100% offline. 100% privado.
Seu áudio nunca sai do seu aparelho.
```

### Seção "Para quem é"
```
Professores presenciais.
Instrutores online.
Tutores particulares.
Palestrantes.
Coaches.
Qualquer um que fala muito e quer registro inteligente.
```

### Seção "Por que não outro app?"
Tabela comparativa:

| Recurso | AulaLogger | Otter.ai | Gravador comum |
|---------|------------|----------|------------------|
| 100% offline | ✅ | ❌ | ✅ |
| Áudio de 4h+ | ✅ | ✅ | ⚠️ |
| Transcrição | ✅ | ✅ | ❌ |
| Identifica quem falou | ✅ | ✅ | ❌ |
| Análise pedagógica | ✅ | ⚠️ | ❌ |
| Privacidade do aluno | ✅ | ❌ | ✅ |
| Open source | ✅ | ❌ | ❌ |
| Grátis | ✅ | ⚠️ | ⚠️ |

### Seção "Como funciona"
3 passos visuais:
1. **Aperta gravar** — app captura o áudio com confiabilidade total.
2. **Termina a aula** — transcrição e diarização rodam automaticamente.
3. **Recebe insights** — resumo, métricas, alertas pedagógicos.

### Seção "Download"
```
  Baixe para Android

  Versão estável: 1.2.0  •  liberada em 03/05/2026
  Compatível com Android 10+ • 60MB

  [Baixar APK]
  [Verificar SHA-256]
  [Instalar via F-Droid]
  [Releases no GitHub]

  ⓘ Como instalar APK fora da Play Store?
     [link para guia]
```

### Footer
- Privacidade · Termos · Changelog · Docs · GitHub · Sponsor
- Open source com licença GPL-3.0 (ou MIT — decisão pendente)
- "Feito com ❤️ por Caio"

---

## 10.4. Página de download

```
[/download]

  AulaLogger para Android

  ┌─────────────────────────────────────────────┐
  │  Versão estável                              │
  │  v1.2.0 — 03/05/2026                          │
  │  Para Android 10+ • 60 MB                     │
  │                                                │
  │  [⬇ Baixar APK]                               │
  │                                                │
  │  SHA-256: a1b2c3d4...                         │
  │  Assinatura: 0x1234...                         │
  └─────────────────────────────────────────────┘

  Outras formas de instalar:

  ┌─────────────────────────────────────────────┐
  │ 📦 F-Droid                                    │
  │  Atualizações automáticas via loja open source │
  │  [Adicionar repositório]                      │
  └─────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────┐
  │ 🐙 GitHub Releases                            │
  │  Histórico completo de versões                 │
  │  [Ir para releases]                           │
  └─────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────┐
  │ ⚙️ Versão beta                                │
  │  v1.3.0-beta.1 — 28/04/2026                   │
  │  Funcionalidades novas, pode ter bugs          │
  │  [Baixar APK beta]                            │
  └─────────────────────────────────────────────┘

  [📖 Como instalar APK no Android passo-a-passo]
  
  Versões anteriores: [ver no GitHub]
```

### Guia "Como instalar APK"

```
1. Baixe o APK do botão acima.

2. Abra "Configurações" do Android.

3. Vá em "Apps" ou "Segurança".

4. Procure "Instalar apps desconhecidos".

5. Habilite para o navegador que você usou pra baixar.

6. Abra o arquivo APK baixado (gerenciador de arquivos ou notificação).

7. Aceite as permissões e pronto!

⚠️ A primeira vez que você abrir, peça para autorizar:
   - Microfone (para gravar)
   - Notificações (para o app continuar gravando em background)
   - Ignorar otimizações de bateria (para aulas longas não serem interrompidas)

[Vídeo: 1min mostrando o processo]
```

---

## 10.5. Documentação

### Estrutura

```
/docs
├── /docs/introducao
├── /docs/instalacao
├── /docs/primeiros-passos
├── /docs/gravando-aulas
├── /docs/transcricao
├── /docs/diarizacao
├── /docs/analise-ia
│   ├── /docs/analise-ia/automatica
│   ├── /docs/analise-ia/sob-demanda
│   ├── /docs/analise-ia/tempo-real
│   └── /docs/analise-ia/cloud
├── /docs/configuracoes
├── /docs/exportacao
├── /docs/troubleshooting
│   ├── /docs/troubleshooting/gravacao-interrompida
│   ├── /docs/troubleshooting/transcricao-lenta
│   ├── /docs/troubleshooting/fabricantes-agressivos
│   └── ...
├── /docs/faq
├── /docs/privacidade
└── /docs/desenvolvedor
    ├── /docs/desenvolvedor/contribuir
    ├── /docs/desenvolvedor/build-local
    └── /docs/desenvolvedor/arquitetura
```

### Layout de uma página de doc

```
┌──────────────────────────────────────────────────────────┐
│  AulaLogger                                          [☰]  │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────┐  ┌────────────────────────────────────┐ │
│  │ Sidebar       │  │ # Gravando aulas                   │ │
│  │               │  │                                     │ │
│  │ • Introdução  │  │ Nesta página você vai aprender...  │ │
│  │ • Instalação  │  │                                     │ │
│  │ • Primeiros   │  │ ## Iniciando uma gravação           │ │
│  │ - Gravando ⬅ │  │                                     │ │
│  │ • Transcrição │  │ Aperte o botão grande...            │ │
│  │ • ...         │  │                                     │ │
│  │               │  │ [screenshot]                        │ │
│  │               │  │                                     │ │
│  │               │  │ ## Marcadores                       │ │
│  │               │  │ ...                                 │ │
│  └──────────────┘  └────────────────────────────────────┘ │
│                                                            │
│                                          [Editar no GitHub]│
└──────────────────────────────────────────────────────────┘
```

Sidebar persistente, busca no topo (Pagefind), links "Editar no GitHub" em cada página (incentiva contribuições).

---

## 10.6. Política de privacidade

**Página obrigatória.** Mesmo sem coletar dados, precisamos declarar:

```markdown
# Política de Privacidade

Última atualização: [data]

## Resumo

O AulaLogger não coleta NENHUM dado seu. Tudo fica no seu celular.

## Detalhes

### O que o app armazena
- Áudio gravado: no seu celular, em pasta privada do app.
- Transcrições: no seu celular.
- Análises: no seu celular.
- Configurações: no seu celular, criptografadas quando sensíveis.

### O que sai do seu celular

**Nada, por padrão.**

Se você ativar a "IA em nuvem" nas configurações:
- Apenas o TEXTO da transcrição é enviado ao provedor que você escolheu (Claude, OpenAI ou Gemini).
- O áudio nunca sai.
- Você usa sua própria conta no provedor — o AulaLogger não tem servidor próprio.

### Telemetria

Zero. O app não tem analytics, crash reporting ou qualquer envio.

### Cookies do site

O site (aulalogger.com.br) não usa cookies de terceiros nem analytics.

### Direitos
- Apagar tudo: app tem opção "Apagar todos os dados" em Settings.
- Exportar tudo: app permite exportar suas aulas, transcrições e análises a qualquer momento.

### Contato
[email/github]
```

---

## 10.7. Página de changelog

Auto-gerada a partir do `CHANGELOG.md` no repo (parseado em build):

```
# Changelog

## v1.2.0 — 03/05/2026
### Novo
- Diarização automática com identificação do professor
- Cadastro de voz (enrollment)
- ...
### Melhorado
- ...
### Corrigido
- ...

## v1.1.0 — 15/04/2026
...
```

---

## 10.8. Seo e metadados

- Open Graph + Twitter Card em todas as páginas.
- Sitemap automático (Astro).
- robots.txt permitindo tudo (não temos nada secreto).
- JSON-LD: SoftwareApplication schema na landing.
- Lighthouse target: 100/100/100/100.

---

## 10.9. Internacionalização do site

Pode esperar: lançamos PT-BR primeiro. Estrutura preparada via Astro i18n para adicionar EN sem refactor.

---

## 10.10. Email/contato

- Email principal para suporte/contato (definir).
- Discussions do GitHub para bugs/features.
- Sem fórum próprio, sem Discord no início.

---

## 10.11. Plano de implementação do site

| Sprint | Entrega |
|--------|---------|
| Sprint 1 (sem 1) | Setup Astro + Tailwind + estrutura, landing "em breve" simples |
| Sprint 5 (sem 8) | Landing v1 completa, página de download, política de privacidade |
| Sprint 7 (sem 10) | Docs MDX iniciais (intro, instalação, primeiros passos) |
| Sprint 11 (sem 14) | Docs completos pra v1.1 (transcrição) |
| Sprint 14 (sem 17) | Docs pra v1.2 (diarização) |
| Sprint 18 (sem 21) | Docs pra v1.3 (IA) + blog opcional |
| Contínuo | Atualização docs a cada release |

### CI/CD do site

- Push em `main` na pasta `site/` → GitHub Actions → build Astro → deploy Cloudflare Pages.
- PRs criam preview URLs.
- Release de APK no GitHub: workflow copia APK para `site/public/apk/` antes do deploy.

---

## 10.12. Branding visual (esboço)

A finalizar com você (P6). Diretrizes:

- **Tom:** acadêmico moderno, técnico, calmo.
- **Não:** infantil, corporativo frio, "tech-bro".
- **Cores principais:** indigo/violeta (calmo, técnico) + cinza escuro de base.
- **Fonte:** Inter ou Geist (sans), JetBrains Mono para código.
- **Logo:** sugiro algo como um microfone estilizado fundido com waveform, minimalista, monocromático em SVG.
- **Tom de voz:** direto, claro, sem hype, sem jargão de marketing.

Pode ser desenvolvido em paralelo com a landing por designer (humano ou via IA generativa) na semana 5–7.
