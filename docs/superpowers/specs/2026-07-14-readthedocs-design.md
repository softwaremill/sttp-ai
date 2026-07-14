# Read the Docs documentation for sttp-ai — design

Date: 2026-07-14
Status: approved

## Goal

Add a published documentation site for sttp-ai on Read the Docs, built with Sphinx and the
`sphinx_rtd_theme`, following the pattern already established in the chimp and sttp projects.
The site's content is the current README (1675 lines), split into a structured set of pages.

## Context

- chimp and sttp share an identical pipeline: MyST markdown sources plus Sphinx config in
  `docs/`; an mdoc sbt project compiles them (verifying Scala snippets) into
  `generated-docs/out/`, which is committed to git; `.readthedocs.yaml` tells Read the Docs to
  build Sphinx from `generated-docs/out/conf.py`.
- sttp-ai already has half the machinery: an mdoc `docs` project (a `projectMatrix` in
  `generated-docs/`) that processes only `README.md`, with CI running
  `sbt -v compileDocumentation`.
- sttp-ai's `docs/` directory currently holds internal content: `adr/`, `plans/`, and (from this
  work) `superpowers/`. These stay where they are and are excluded from both mdoc and Sphinx.

## Decisions

1. **Content scope**: split the README into structured pages (not a minimal port, not a rewrite).
   Content is ported — same prose and snippets, reorganized; light edits only where page splits
   require them.
2. **Internal docs**: `docs/adr/`, `docs/plans/`, `docs/superpowers/` remain in place, excluded
   from the published site and from mdoc processing.
3. **README fate**: slim it down to intro + installation + quickstart + link to the site.
4. **Canonical URL**: `https://sttp-ai.softwaremill.com/` (custom domain configured on the Read
   the Docs side; `conf.py` declares it as the default canonical URL).
5. **Pipeline**: the full chimp/sttp pattern — mdoc compiles `docs/` into the committed
   `generated-docs/out/`, Read the Docs builds from there. Chosen over building directly from
   `docs/` because it keeps `@VERSION@` substitution working on the published site and stays
   consistent with the sibling projects.

## Site structure

MyST markdown pages under `docs/`, organized into toctree captions **Getting started**,
**OpenAI**, **Claude**, **Agent loop**, **Other** (index.md styled after chimp's):

```
docs/
  index.md                     # intro + toctrees + module installation overview
  quickstart.md                # OpenAI + Claude quickstart snippets
  openai/
    basics.md                  # basic usage, client options
    streaming.md               # streaming completions
    structured-outputs.md      # JSON schema, tapir derivation
    compatible-apis.md         # Ollama, Grok, available client implementations
  claude/
    basics.md                  # features, basic usage, configuration
    messages.md                # text, system messages, images, advanced params
    structured-outputs.md      # createMessageAs[T], schema definition
    tool-calling.md            # custom + predefined tools
    streaming.md               # fs2, ZIO, Ox
    models-and-errors.md       # models API, error handling, sync client,
                               # differences from OpenAI
  agents/
    quickstart.md              # agent loop quick start
    configuration.md           # agent config, hooks, exception handling
    tools.md                   # tool definition, agent result, runAs[T]
  other/
    backends.md                # custom backend, effect systems (cats-effect, ZIO)
    examples.md                # running examples
```

Every current README section must map to one of these pages or be deliberately retained in the
slimmed README — no content silently dropped.

## Build pipeline & sbt changes

The existing `docs` project in `build.sbt` is repointed:

- `mdocIn := file("docs")` (was `README.md`)
- `mdocOut := file("generated-docs/out")` (was `generated-docs/README.md`)
- `mdocExtraArguments`: keep `--clean-target` and `--disable-using-directives`; add
  `--exclude .venv --exclude _build --exclude adr --exclude plans --exclude superpowers`
- add `mdocVariables := Map("VERSION" -> version.value)`; pages use `@VERSION@` instead of
  hard-coded version strings
- dependencies unchanged (`openai, fs2, zio, ox, pekko`); still a `projectMatrix` on Scala 3
- the `compileDocumentation` task name stays, so CI needs no changes

`generated-docs/out/` is committed to git (it is what Read the Docs builds). The old
`generated-docs/README.md` output is removed. mdoc copies non-Scala files (`conf.py`,
`requirements.txt`) through to the output, as in chimp.

## Sphinx + Read the Docs config

Copied from chimp and adapted:

- `docs/conf.py` — extensions `myst_parser`, `sphinx_rtd_theme`, `sphinxcontrib.mermaid`,
  `sphinx_llms_txt`; `html_baseurl` defaulting to `https://sttp-ai.softwaremill.com/`;
  project `sttp-ai`; copyright SoftwareMill; `highlight_language = 'scala'`; GitHub edit links
  for `softwaremill/sttp-ai` on branch `master`; `exclude_patterns` extended with `adr`,
  `plans`, `superpowers`; `llms_txt_*` settings adapted to sttp-ai
- `docs/requirements.txt` — pinned versions copied from chimp (`sphinx==7.3.7`,
  `sphinx_rtd_theme==2.0.0`, `myst-parser==2.0.0`, `sphinx-autobuild==2024.4.16`,
  `sphinxcontrib-mermaid==0.9.2`, `sphinx-llms-txt`)
- `docs/Makefile`, `docs/watch.sh`, `docs/README.md`, `docs/.gitignore` — copied from chimp
  with names adjusted; the docs README explains local preview via `sphinx-autobuild` and the
  rule that `generated-docs/out/` must be regenerated (`sbt compileDocumentation`) and
  committed for the published site to update
- `.readthedocs.yaml` at repo root — identical to chimp/sttp: Sphinx configuration
  `generated-docs/out/conf.py`, Python requirements `generated-docs/out/requirements.txt`,
  ubuntu-22.04, Python 3.12

## README slimming

The README shrinks to: badges/banner, intro paragraph, module installation coordinates, the two
short quickstart snippets (OpenAI + Claude), a prominent link to
`https://sttp-ai.softwaremill.com`, and the existing contributing / commercial-support /
copyright tail. Since mdoc no longer processes the README, its snippets are not
compile-verified — keeping them quickstart-only limits rot risk.

## Verification

- `sbt compileDocumentation` passes — all ported snippets compile against current modules.
- A local Sphinx build (venv + `make html`, or `sphinx-autobuild` via `watch.sh`) completes
  with no broken cross-reference warnings, and the toctree renders all pages.
- Content-completeness check: walk the README's heading list and confirm each section landed in
  a docs page or was deliberately kept in the README.

## Out of scope / manual follow-ups

- Creating the `sttp-ai` project on readthedocs.org and connecting it to the GitHub repo.
- Pointing `sttp-ai.softwaremill.com` DNS at Read the Docs and configuring the custom domain.
  Both require RtD/DNS access and happen outside the repo.
