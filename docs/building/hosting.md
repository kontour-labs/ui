# Hosting the documentation

The site is served at **`https://docs.kontour.io/ui/`** from Cloudflare Pages,
in public: no sign-in for people, and no headers for agents. `docs.kontour.io` is
meant to hold Kontour's documentation in general; this
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
| `/ui/icons/manifest.txt`, `/ui/icons/<hash>/` | The Icons page's data: every Tabler icon's name, category and tags, and their paths in chunks of 128, fetched as rows scroll into view. Laid out by `docs/icondata.py` from the `@tabler/icons` release the library is built from, the directory named for its content |
| `/llms.txt` | The root index, pointing at each library's own |
| `/_headers` | A year's public cache for the two content-hashed `.wasm` files and the content-named icon data, and no other; plain text for the mirror. Nothing asks search engines to stay away |
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
branches: the jobs run on Blacksmith's runners, whose minutes are metered. The `changes`
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
5. **Deploy.** Merge to `main`; the first run deploys. Then check, in a private
   window, that `https://docs.kontour.io/ui/` opens with no sign-in, and that an
   agent's plain request gets the index:

   ```sh
   curl -fsS https://docs.kontour.io/ui/llms.txt | head
   ```

## Agents

[`ui-docs/content/agents.md`](../../ui-docs/content/agents.md) is the page an
agent reads: what to fetch, and a `CLAUDE.md` snippet for an app. Nothing has to
be configured for an agent to read the site; an agent in a sandbox with an
allowlist needs `docs.kontour.io` allowed.

Building the site in a sandboxed environment needs `codeload.github.com`
allowed. The Kotlin/JS toolchain fetches a package from there, and without it
`:ui-docs:wasmJsBrowserDistribution` fails before compiling anything.
