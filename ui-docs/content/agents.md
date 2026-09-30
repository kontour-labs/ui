# Agents

How an agent reads this documentation: Claude Code, or anything else that
fetches a URL and reads what comes back.

## Not the site

The site is one Compose canvas. Fetch `https://docs.kontour.io/ui/` and what
comes back is an HTML shell and a script tag, with none of the text in it, so
there is nothing to read. Every page is also published as the Markdown it was
built from, and that is the version to read:

| | What it is |
|---|---|
| `https://docs.kontour.io/ui/llms.txt` | The index: every page, one line each, with its first sentence. Start here |
| `https://docs.kontour.io/ui/md/<path>.md` | One page, samples included, e.g. `md/components/button.md` or `md/tokens.md` |
| `https://docs.kontour.io/ui/llms-full.txt` | Every page in one file, about 700 KB, for searching rather than reading |
| `https://docs.kontour.io/ui/api/` | The API reference, generated from the KDoc. Already HTML text |

The path in a Markdown URL is the path in the site's own address, so
`#/components/nav-bar` on the site is `md/components/nav-bar.md` here. Links
between pages are relative and resolve the same way.

## Getting in

The site is private, behind Cloudflare Access. A person signs in with GitHub;
an agent sends a service token in two headers on every request:

```sh
curl -fsS \
  -H "CF-Access-Client-Id: $KONTOUR_DOCS_CLIENT_ID" \
  -H "CF-Access-Client-Secret: $KONTOUR_DOCS_CLIENT_SECRET" \
  https://docs.kontour.io/ui/llms.txt
```

The two values are the `docs-agents` service token's, kept in the agent's
environment under those names. For Claude Code on the web they go in the cloud
environment's settings as environment variables, and `docs.kontour.io` has to be
allowed by its network access. On a laptop they are ordinary environment
variables.

A fetch tool that cannot add headers, such as Claude Code's `WebFetch`, gets
the Access sign-in page instead of the documentation. Use `curl` from the
shell.

An agent working in this repository needs none of this: `ui-docs/content` is
the same Markdown, and reading it there is faster.

## Upgrading

`md/changelog.md` lists what each release changed, newest first, and
`md/migrating.md` has what an upgrade has to change, as code before and after.
Moving an app from one version to another is:

1. Read every entry after the version the app is on, up to and including the
   one it is moving to.
2. Make each **Breaking** change it lists, as its section in `md/migrating.md`
   writes it.
3. Check each **Changed** line against the screens that use it.

The compiler finds the rest.

## A `CLAUDE.md` for an app built on the library

Paste this into the app's `CLAUDE.md`, and set the two variables in the
agent's environment:

> **Kontour UI.** This app's components come from `io.kontour:ui`. Its
> documentation is private, at `https://docs.kontour.io/ui/`, and the site
> itself cannot be read by an agent. Read the Markdown instead, with the
> service token in two headers:
> `curl -fsS -H "CF-Access-Client-Id: $KONTOUR_DOCS_CLIENT_ID" -H "CF-Access-Client-Secret: $KONTOUR_DOCS_CLIENT_SECRET" https://docs.kontour.io/ui/llms.txt`
> lists every page, and the same request for `…/ui/md/<path>.md` returns one.
> Before writing a screen, find the component for it in the index and read its
> page: prefer the library's component to a hand-built one, and its tokens
> (`Theme.spacing`, `Theme.colours`, `Theme.typography`) to literal values.
> When the app moves to a newer `io.kontour` version, read `…/ui/md/changelog.md`
> for every release in between, and make each **Breaking** change it lists, as
> `…/ui/md/migrating.md` writes it.
