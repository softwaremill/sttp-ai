# sttp-ai documentation

Source for the sttp-ai documentation site, built with Sphinx + MyST and hosted on Read the Docs.

## Run locally

From this folder:

```
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./watch.sh
```

Open <http://127.0.0.1:8000>. Edits to `.md` files live-reload in the browser.

Next time, just:

```
source .venv/bin/activate
./watch.sh
```

## Publishing changes

Read the Docs builds from `generated-docs/out/`, **not** from this `docs/` folder. After editing the docs, regenerate that output with mdoc:

```
sbt 'set every version := "0.5.1"' "docs3/mdoc"
```

Pin `version` to the latest released version (as above) so the published install coordinates don't show a `-SNAPSHOT` dev version; plain `sbt "docs3/mdoc"` is fine for local checks.

Commit both `docs/` (the source) and `generated-docs/out/` (the mdoc output) — if `generated-docs/out/` is stale, the published site won't reflect your changes.

## Notes

- `0.5.3` and other mdoc variables are **not** substituted in the local watch mode. For a fully-rendered preview, run `sbt "docs3/mdoc"` from the repo root and serve `generated-docs/out/` instead.
- Scala code snippets are verified by `sbt compileDocumentation` (also runs in CI).
- `adr/`, `plans/`, and `superpowers/` in this directory are internal project docs — they are excluded from the published site.
