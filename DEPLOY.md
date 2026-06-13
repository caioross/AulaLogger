# Deploy do site (`site/`)

O site é um projeto **Astro estático** que vive na subpasta [`site/`](site/) — **não** na raiz
do repositório. Isso é o que costuma quebrar o deploy: a plataforma tenta buildar a raiz,
não acha o projeto e devolve **404**. A configuração abaixo resolve isso.

> O app Android (`app/`) não tem nada a ver com o deploy do site. Só `site/` é publicado.

---

## Vercel (recomendado)

O arquivo [`vercel.json`](vercel.json) na **raiz** já configura tudo. Ao importar o
repositório no Vercel, **funciona sem nenhum ajuste no painel** — basta:

1. **New Project → Import** o repositório `caioross/AulaLogger`.
2. Em **Root Directory**, deixe o **padrão (`./`)**. ⚠️ **Não** mude para `site` — se mudar,
   o Vercel passa a ler `site/vercel.json` (que não existe) e ignora o da raiz.
3. **Deploy.**

O `vercel.json` faz:

```jsonc
{
  "installCommand": "npm --prefix site install",          // instala deps em site/
  "buildCommand":   "npm --prefix site run build && rm -rf site/dist/apk", // builda e poda APKs
  "outputDirectory": "site/dist"                            // serve o build estático
}
```

Resultado: deploy de **~350 KB** (era ~668 MB por causa dos APKs — ver nota abaixo),
com cache imutável para os assets em `/_astro/*`.

### Domínio

O `astro.config.mjs` usa `site: 'https://aulalogger.com.br'` (canonical, Open Graph,
sitemap). Em **Settings → Domains** do Vercel, adicione `aulalogger.com.br`. Enquanto usar
só a URL `*.vercel.app`, o site funciona normalmente — apenas as URLs absolutas de SEO
apontam para o domínio final.

### Se já existe um projeto Vercel que deu 404

Apague as **Build & Output Settings** sobrescritas no painel (deixe "Override" desligado) e
confirme **Root Directory = `./`**. O `vercel.json` tem precedência e cuida do resto.
Depois, **Redeploy**.

---

## Cloudflare Pages (alternativa, via GitHub Actions)

Já existe o workflow [`.github/workflows/site-deploy.yml`](.github/workflows/site-deploy.yml),
disparado em push para `main` que toque em `site/**`. Precisa dos secrets
`CLOUDFLARE_API_TOKEN` e `CLOUDFLARE_ACCOUNT_ID`. O build também poda `dist/apk` (limite de
25 MB/arquivo do Cloudflare Pages).

> Usando Vercel? Pode desativar esse workflow para evitar deploy duplicado.

---

## Nota: APKs legados em `site/public/apk/`

Há ~430 MB de APKs **versionados** em `site/public/apk/`. A distribuição hoje é via
**GitHub Releases** (a página de download busca a release mais recente pela API) — esses
arquivos **não são mais usados pelo site** e estouram os limites de tamanho do Vercel e do
Cloudflare. Por isso o build os remove do deploy.

**Recomendado:** removê-los do repositório para builds/clones mais rápidos (continuam
disponíveis no histórico do Git e como GitHub Releases):

```bash
git rm -r --cached site/public/apk
echo "site/public/apk/" >> .gitignore
git commit -m "chore: remove APKs legados do site (distribuídos via GitHub Releases)"
```

---

## Rodar localmente

```bash
cd site
npm install
npm run dev      # http://localhost:4321
npm run build    # build estático em site/dist
npm run preview  # serve o build
```
