# Hosting the documentation

The site is served at **`https://docs.kontour.io/ui/`** from Cloudflare Pages,
behind Cloudflare Access. People sign in with GitHub and must be members of the
kontour-labs organisation. Agents send a service token in two headers.
`docs.kontour.io` is meant to hold Kontour's documentation in general; this
library's is the part under `/ui/`.

## What is uploaded

`docs/lay-out-site.py --deploy dist` assembles the upload, and the `site` job in
`.github/workflows/ci.yml` runs it on every push to `main` that touches anything
the site is built from:

| Path | What it is |
|---|---|
| `/ui/` | The Compose/Wasm site. Routes are URL fragments (`/ui/#/components/button`), so the host needs no rewrite rules |
| `/ui/api/` | The Dokka reference |
| `/ui/md/<path>.md` | Every page as the Markdown it was built from, for agents and for find-in-page |
| `/ui/llms.txt`, `/ui/llms-full.txt` | The index of those, one line a page; and all of them in one file |
| `/llms.txt` | The root index, pointing at each library's own |
| `/_headers` | `noindex` everywhere; a year's cache for the two content-hashed `.wasm` files and no other; plain text for the mirror |
| `/_redirects` | `/` goes to `/ui/`, with a 302 so no browser remembers it after the root gains a second library |

The `deploy` job uploads it with `wrangler pages deploy`. **A Direct Upload
replaces the whole project**, so while this library is the only one on the host
this upload is the entire site. When a second library's documentation arrives,
the upload has to be assembled from both, or each deploy will delete the other's.

There are no `Cross-Origin-Opener-Policy`/`Cross-Origin-Embedder-Policy` headers.
They exist to unlock `SharedArrayBuffer`, which a single-threaded Wasm app never
uses, and `require-corp` would only break a cross-origin asset someone adds
later.

## When CI runs

Pushes to `main`, `v*` tags and manual runs. Not on pull requests or other
branches: the repository is private, and its minutes are billed. The `changes`
job decides what a push has to run. [`testing.md`](testing.md#what-ci-actually-runs-and-what-it-does-not-run-twice)
has the table.

## Setting it up

Once, in this order. Nothing here is in the repository: it is all account
settings.

1. **Cloudflare Pages project.** Create one named `kontour-docs` as a *Direct
   Upload* project, not connected to Git: Workers & Pages → Create → Pages →
   Upload assets, with any placeholder folder; or
   `npx wrangler pages project create kontour-docs --production-branch main`.
   Another name works if the repository variable `CLOUDFLARE_PAGES_PROJECT` says
   so.
2. **Cloudflare API token.** My Profile → API Tokens → Create Token → Custom,
   with *Account → Cloudflare Pages → Edit* on the account. Note the account ID
   from the dashboard's sidebar.
3. **GitHub secrets.** On `kontour-labs/ui`, Settings → Secrets and variables →
   Actions: `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.
4. **Custom domain.** In the Pages project, Custom domains → `docs.kontour.io`.
   If `kontour.io` is a Cloudflare zone the record is made for you; otherwise
   add a `CNAME` from `docs` to `kontour-docs.pages.dev`.
5. **GitHub as an identity provider.** Create an OAuth app under the
   kontour-labs organisation (Settings → Developer settings → OAuth Apps) with
   homepage `https://<team>.cloudflareaccess.com` and callback
   `https://<team>.cloudflareaccess.com/cdn-cgi/access/callback`, where
   `<team>` is the Zero Trust team name. Add it in Zero Trust → Settings →
   Authentication → Login methods → GitHub. If the organisation restricts
   third-party applications, approve this one, or Access cannot see
   membership.
6. **Service token.** Zero Trust → Access → Service auth → Service Tokens →
   Create, named `docs-agents`. Keep the client ID and secret: they are shown
   once.
7. **The Access application.** Zero Trust → Access → Applications → Add →
   Self-hosted, with three public hostnames: `docs.kontour.io`,
   `kontour-docs.pages.dev` and `*.kontour-docs.pages.dev`. The last two matter:
   without them the site is public at its `pages.dev` address, whatever the
   custom domain asks. Two policies:
   - *Allow*: include *GitHub organization* `kontour-labs`.
   - *Service Auth*: include *Service Token* `docs-agents`.
8. **Deploy.** Merge to `main`; the first run deploys. Then check, in a private
   window, that `https://docs.kontour.io/ui/` asks for GitHub sign-in, and that
   an agent's request gets the index:

   ```sh
   curl -fsS \
     -H "CF-Access-Client-Id: $KONTOUR_DOCS_CLIENT_ID" \
     -H "CF-Access-Client-Secret: $KONTOUR_DOCS_CLIENT_SECRET" \
     https://docs.kontour.io/ui/llms.txt | head
   ```
9. **Make the repository private,** once the site is up: Settings → General →
   Change visibility. Then Settings → Pages → unpublish the old GitHub Pages site,
   and check branch protection requires no status checks, which pull requests
   no longer produce.

## Agents

[`ui-docs/content/agents.md`](../../ui-docs/content/agents.md) is the page an
agent reads. It covers what to fetch, the two headers and a `CLAUDE.md` snippet
for an app. For Claude Code on the web, the two values go in the cloud
environment's settings as environment variables, `KONTOUR_DOCS_CLIENT_ID` and
`KONTOUR_DOCS_CLIENT_SECRET`, and `docs.kontour.io` has to be allowed by the
environment's network access.

Building the site in such an environment also needs `codeload.github.com`
allowed. The Kotlin/JS toolchain fetches a package from there, and without it
`:ui-docs:wasmJsBrowserDistribution` fails before compiling anything.
