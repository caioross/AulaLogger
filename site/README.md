# site/ — Landing page e documentação do AulaLogger

Site estático construído com Astro + Tailwind, hospedado em Cloudflare Pages.

## Setup

```bash
npm install
npm run dev      # http://localhost:4321
npm run build    # build estático em dist/
npm run preview  # preview do build
```

## Estrutura

```
site/
├── src/
│   ├── pages/                # Rotas (file-based)
│   │   ├── index.astro       # Landing
│   │   ├── download.astro
│   │   ├── privacy.astro
│   │   ├── changelog.astro
│   │   └── 404.astro
│   ├── components/
│   │   ├── Header.astro
│   │   └── Footer.astro
│   ├── layouts/
│   │   └── Base.astro
│   └── styles/
│       └── global.css
├── public/
│   ├── favicon.svg
│   ├── robots.txt
│   └── apk/                  # APKs publicados (gerados pelo workflow de release)
├── astro.config.mjs
├── tailwind.config.mjs
└── package.json
```

## Deploy

Push em `main` na pasta `site/` dispara o workflow `.github/workflows/site-deploy.yml`,
que faz build e publica em Cloudflare Pages.

## Próximas seções

- `src/content/docs/` — documentação em MDX (a partir de v1.1)
- `src/content/blog/` — blog opcional
