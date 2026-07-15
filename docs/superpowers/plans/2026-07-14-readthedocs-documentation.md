# Read the Docs Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish sttp-ai's documentation as a Read the Docs site (Sphinx + `sphinx_rtd_theme`), splitting the current README into structured pages, following the chimp/sttp pattern.

**Architecture:** MyST markdown sources plus Sphinx config live in `docs/`. The existing mdoc sbt project (`docs` in `build.sbt`) is repointed from `README.md` to `docs/`, compiling into `generated-docs/out/` (committed to git). `.readthedocs.yaml` tells Read the Docs to build Sphinx from `generated-docs/out/conf.py`. CI's existing `sbt -v compileDocumentation` keeps verifying snippets.

**Tech Stack:** Sphinx 7.3.7, sphinx_rtd_theme 2.0.0, myst-parser 2.0.0, mdoc (already configured via sbt MdocPlugin), Python 3.12 venv for local builds.

**Spec:** `docs/superpowers/specs/2026-07-14-readthedocs-design.md`

## Global Constraints

- Canonical site URL: `https://sttp-ai.softwaremill.com/`.
- `docs/adr/`, `docs/plans/`, `docs/superpowers/` stay in place, excluded from both mdoc and Sphinx.
- The sbt task name `compileDocumentation` must not change (CI depends on it).
- The mdoc `docs` project's `dependsOn(openai, fs2, zio, ox, pekko)` stays unchanged. If a ported `mdoc:compile-only` snippet fails to resolve a Claude class, add `claude` to `dependsOn` — do not change the snippet.
- Work happens on the existing `readthedocs-docs` branch.
- **README line numbers in this plan refer to `README.md` as of commit `33fc235` (branch point). Do NOT edit `README.md` until Task 8** — all earlier tasks only read from it.
- Every commit that changes anything under `docs/` (except `docs/adr|plans|superpowers`) must also include the regenerated `generated-docs/out/` (from Task 2 onward).
- Regenerate `generated-docs/out/` with `sbt "docs/mdoc"`. If sbt cannot resolve project `docs` (projectMatrix may suffix the id), run `sbt projects` and use the listed docs project id (e.g. `docs3`) — the same id `compileDocumentation` resolves via `docs.jvm(scala3.head)`.
- Sphinx local build must complete with **zero warnings**; treat any warning as a failure to fix before committing.

## Porting rules (apply to every content task)

1. **Copy content verbatim** from the given README line range; light edits only where the split requires (e.g. a sentence referencing "see section above" that now lives on another page).
2. **Promote headings** so each page's top heading is `#` (h1). Preserve relative depth below it.
3. **Version strings:** replace every literal `0.5.2` in ported content with `@VERSION@` (mdoc substitutes it; the `VERSION` mdoc variable is added in Task 2).
4. **Keep mdoc modifiers as-is**: fences tagged ```` ```scala mdoc:compile-only ```` stay tagged; plain ```` ```scala ```` fences stay plain.
5. **Internal links:** README anchor links (`[...](#some-heading)`) pointing to content that now lives on another page become relative MyST file links, e.g. `[streaming](../claude/streaming.md)`. Links within the same page stay as anchors. External links unchanged.
6. **Drop the README Table of Contents** (lines 16–46) entirely — site navigation replaces it.

## File Structure

```
.readthedocs.yaml                 # new — RtD build config (Task 2)
build.sbt                         # modified — mdoc repoint (Task 2)
.gitignore                        # modified — ignore generated-docs/out/{.venv,_build} (Task 1)
README.md                         # slimmed (Task 8)
docs/
  conf.py, requirements.txt, Makefile, watch.sh, README.md, .gitignore   # Task 1
  index.md                        # Task 1, toctrees appended in Tasks 3–7
  quickstart.md                   # Task 3
  openai/{basics,streaming,structured-outputs,compatible-apis}.md        # Task 4
  claude/{basics,messages,structured-outputs,tool-calling,streaming,models-and-errors}.md  # Task 5
  agents/{quickstart,configuration,tools}.md                             # Task 6
  other/{backends,examples}.md                                           # Task 7
generated-docs/out/               # mdoc output, committed (Task 2 onward)
```

---

### Task 1: Sphinx infrastructure in `docs/`

**Files:**
- Create: `docs/conf.py`, `docs/requirements.txt`, `docs/Makefile`, `docs/watch.sh`, `docs/README.md`, `docs/.gitignore`, `docs/index.md`
- Modify: `.gitignore` (repo root)

**Interfaces:**
- Produces: a Sphinx project rooted at `docs/` that later tasks add pages to; `docs/index.md` whose `{eval-rst}` block Tasks 3–7 append toctrees into.

- [ ] **Step 1: Create `docs/conf.py`**

```python
# -*- coding: utf-8 -*-
#
# sttp-ai documentation build configuration file.

# https://about.readthedocs.com/blog/2024/07/addons-by-default/
import os

# Define the canonical URL if you are using a custom domain on Read the Docs
html_baseurl = os.environ.get(
    "READTHEDOCS_CANONICAL_URL",
    "https://sttp-ai.softwaremill.com/",
)

# Tell Jinja2 templates the build is running on Read the Docs
if os.environ.get("READTHEDOCS", "") == "True":
    if "html_context" not in globals():
        html_context = {}
    html_context["READTHEDOCS"] = True

# -- General configuration ------------------------------------------------

extensions = ['myst_parser', 'sphinx_rtd_theme', 'sphinxcontrib.mermaid', 'sphinx_llms_txt']

myst_enable_extensions = ['attrs_block']

llms_txt_title = "sttp-ai"
llms_txt_summary = "Scala client for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs"
llms_txt_full_file = True

# The suffix(es) of source filenames.
source_suffix = {
    '.rst': 'restructuredtext',
    '.md': 'markdown',
}

# The master toctree document.
master_doc = 'index'

# General information about the project.
project = u'sttp-ai'
copyright = u'2026, SoftwareMill'
author = u'SoftwareMill'

# The short X.Y version.
version = u'0.5'
# The full version, including alpha/beta/rc tags.
release = u'0.5'

language = 'en'

# List of patterns, relative to source directory, that match files and
# directories to ignore when looking for source files.
exclude_patterns = [
    '_build', 'Thumbs.db', '.DS_Store',
    '.venv', 'venv', 'env',
    '**/site-packages/**',
    '**/node_modules/**',
    '_templates',
    'requirements.txt',
    'README.md',
    'includes/*',
    'adr',
    'plans',
    'superpowers',
]

pygments_style = 'default'

# -- Options for HTML output ----------------------------------------------

html_theme = 'sphinx_rtd_theme'

htmlhelp_basename = 'sttpaidoc'

highlight_language = 'scala'

# configure edit on github: https://docs.readthedocs.io/en/latest/guides/vcs.html
html_context = {
    'display_github': True,
    'github_user': 'softwaremill',
    'github_repo': 'sttp-ai',
    'github_version': 'master',
    'conf_py_path': '/docs/',
}
```

- [ ] **Step 2: Create `docs/requirements.txt`**

```
sphinx_rtd_theme==2.0.0
sphinx==7.3.7
sphinx-autobuild==2024.4.16
myst-parser==2.0.0
sphinxcontrib-mermaid==0.9.2
sphinx-llms-txt
```

- [ ] **Step 3: Create `docs/Makefile`**

```makefile
# Minimal makefile for Sphinx documentation
#

# You can set these variables from the command line.
SPHINXOPTS    =
SPHINXBUILD   = python -msphinx
SPHINXPROJ    = sttp-ai
SOURCEDIR     = .
BUILDDIR      = _build

# Put it first so that "make" without argument is like "make help".
help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

.PHONY: help Makefile

# Catch-all target: route all unknown targets to Sphinx using the new
# "make mode" option.  $(O) is meant as a shortcut for $(SPHINXOPTS).
%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)
```

(Note: recipe lines must be indented with a TAB character, not spaces.)

- [ ] **Step 4: Create `docs/watch.sh`** (and `chmod +x docs/watch.sh`)

```bash
#!/bin/bash
sphinx-autobuild . _build/html
```

- [ ] **Step 5: Create `docs/README.md`**

```markdown
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
sbt "docs/mdoc"
```

Commit both `docs/` (the source) and `generated-docs/out/` (the mdoc output) — if `generated-docs/out/` is stale, the published site won't reflect your changes.

## Notes

- `@VERSION@` and other mdoc variables are **not** substituted in the local watch mode. For a fully-rendered preview, run `sbt "docs/mdoc"` from the repo root and serve `generated-docs/out/` instead.
- Scala code snippets are verified by `sbt compileDocumentation` (also runs in CI).
- `adr/`, `plans/`, and `superpowers/` in this directory are internal project docs — they are excluded from the published site.
```

(The three nested code fences above are part of the file content — use four-backtick outer fencing when creating the file, or write it with a heredoc.)

- [ ] **Step 6: Create `docs/.gitignore`**

```
_build
_build_html
.venv
```

- [ ] **Step 7: Create `docs/index.md`** (toctrees are appended by Tasks 3–7)

```markdown
# sttp-ai: Scala client for OpenAI, Claude, and compatible APIs

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): The Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs. Use the power of ChatGPT and Claude inside your code!

sttp-ai uses sttp client to describe requests and responses used in OpenAI, Claude (Anthropic), and OpenAI-compatible endpoints.
```

- [ ] **Step 8: Add to repo-root `.gitignore`** (append at end of file)

```
# Local Sphinx build artifacts
generated-docs/out/.venv/
generated-docs/out/_build/
```

- [ ] **Step 9: Verify the Sphinx build works**

```bash
cd docs
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
make html
```

Expected: `build succeeded.` with **0 warnings**; `docs/_build/html/index.html` exists and contains the intro text.

- [ ] **Step 10: Commit**

```bash
cd /Users/adamrybicki/SML/sttp-ai
git add docs/conf.py docs/requirements.txt docs/Makefile docs/watch.sh docs/README.md docs/.gitignore docs/index.md .gitignore
git commit -m "docs: add Sphinx infrastructure for Read the Docs site"
```

---

### Task 2: Repoint mdoc pipeline and add `.readthedocs.yaml`

**Files:**
- Modify: `build.sbt:151-168` (the `compileDocumentation` task and `docs` project)
- Create: `.readthedocs.yaml`
- Create (generated): `generated-docs/out/**`

**Interfaces:**
- Consumes: `docs/` Sphinx project from Task 1.
- Produces: `sbt "docs/mdoc"` writes `generated-docs/out/` (committed); `sbt compileDocumentation` verifies snippets (unchanged name, used by CI and every later task).

- [ ] **Step 1: Replace the `docs` project definition in `build.sbt`**

Replace the existing block (currently `mdocIn := file("README.md")`, `mdocOut := file("generated-docs/README.md")`) with:

```scala
val compileDocumentation: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")
compileDocumentation :=
  (docs.jvm(scala3.head) / mdoc).toTask(" --out target/sttp-ai-docs").value

lazy val docs = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("docs"),
    moduleName := "sttp-ai-docs",
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    mdocOut := file("generated-docs/out"),
    mdocExtraArguments := Seq(
      "--clean-target",
      "--disable-using-directives",
      "--exclude", ".venv",
      "--exclude", "_build",
      "--exclude", "adr",
      "--exclude", "plans",
      "--exclude", "superpowers"
    ),
    publishArtifact := false,
    name := "docs",
    evictionErrorLevel := Level.Info
  )
  .dependsOn(openai, fs2, zio, ox, pekko)
  .jvmPlatform(scalaVersions = scala3)
```

Only `mdocIn`, `mdocVariables`, `mdocOut`, and `mdocExtraArguments` change; everything else is verbatim from the current definition.

- [ ] **Step 2: Create `.readthedocs.yaml`** (repo root; identical to chimp/sttp)

```yaml
version: 2

sphinx:
  configuration: generated-docs/out/conf.py

python:
  install:
    - requirements: generated-docs/out/requirements.txt

build:
  os: ubuntu-22.04
  tools:
    python: "3.12"
```

- [ ] **Step 3: Run the verification task**

Run: `sbt compileDocumentation`
Expected: success (processes `docs/index.md`; no Scala snippets yet).

- [ ] **Step 4: Generate the committed output**

Run: `rm -f generated-docs/README.md` (stale output of the old pipeline; untracked, may exist locally), then `sbt "docs/mdoc"`
Expected: `generated-docs/out/` now contains `index.md`, `conf.py`, `requirements.txt`, `Makefile`, `watch.sh`, `README.md`, `.gitignore` — and does **not** contain `adr/`, `plans/`, or `superpowers/`. Verify:

```bash
ls generated-docs/out/ && test ! -e generated-docs/out/adr && test ! -e generated-docs/out/plans && test ! -e generated-docs/out/superpowers && echo OK
```

Expected: file listing followed by `OK`.

- [ ] **Step 5: Commit**

```bash
git add build.sbt .readthedocs.yaml generated-docs/out
git commit -m "build: repoint mdoc to docs/ sources, add Read the Docs config"
```

---

### Task 3: Getting started — `docs/quickstart.md`

**Files:**
- Create: `docs/quickstart.md`
- Modify: `docs/index.md` (append toctree)
- Regenerate: `generated-docs/out/`

**Interfaces:**
- Consumes: pipeline from Task 2; README lines 51–77.
- Produces: `quickstart.md` page; the `{eval-rst}` toctree block in `index.md` that Tasks 4–7 extend.

- [ ] **Step 1: Create `docs/quickstart.md`** from README lines 51–77, applying the porting rules. Top heading becomes `# Quickstart`; `### For OpenAI/OpenAI-compatible APIs` → `## For OpenAI/OpenAI-compatible APIs`; `### For Claude (Anthropic) API` → `## For Claude (Anthropic) API`; every `0.5.2` → `@VERSION@`.

- [ ] **Step 2: Append the toctree to `docs/index.md`**

```markdown

```{eval-rst}
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
```
```

(Outer fence shown for plan readability — the file gains a blank line plus the ` ```{eval-rst} ` block.)

- [ ] **Step 3: Verify**

Run: `sbt compileDocumentation` — Expected: success.
Run: `cd docs && source .venv/bin/activate && make html` — Expected: `build succeeded.`, 0 warnings, quickstart page in the nav.

- [ ] **Step 4: Regenerate and commit**

```bash
sbt "docs/mdoc"
git add docs/quickstart.md docs/index.md generated-docs/out
git commit -m "docs: add quickstart page"
```

---

### Task 4: OpenAI pages

**Files:**
- Create: `docs/openai/basics.md`, `docs/openai/streaming.md`, `docs/openai/structured-outputs.md`, `docs/openai/compatible-apis.md`
- Modify: `docs/index.md` (append toctree)
- Regenerate: `generated-docs/out/`

**Interfaces:**
- Consumes: README line ranges below; toctree block pattern from Task 3.
- Produces: the four `openai/*` pages other pages may link to relatively.

- [ ] **Step 1: Create the four pages** (porting rules apply to each):

| Page | README lines | Top heading (h1) |
|---|---|---|
| `docs/openai/basics.md` | 78–143 | `# OpenAI API` |
| `docs/openai/streaming.md` | 1226–1374 | `# Streaming` |
| `docs/openai/structured-outputs.md` | 1375–1660 | `# Structured outputs / JSON Schema` |
| `docs/openai/compatible-apis.md` | 1031–1225 | `# OpenAI-compatible APIs` |

Note: `streaming.md` and `structured-outputs.md` source sections physically sit under the README's "OpenAI-Compatible APIs" heading but are general OpenAI features — port them as standalone pages with the h1 above. If their intros implicitly lean on the compatible-APIs context, add a one-line lead-in sentence (light edit).

- [ ] **Step 2: Append the toctree to `docs/index.md`** — add the directive below as an additional `.. toctree::` inside the single existing `{eval-rst}` block created in Task 3 (chimp's index.md uses one block with multiple directives; Tasks 5–7 do the same):

```
.. toctree::
   :maxdepth: 2
   :caption: OpenAI

   openai/basics
   openai/streaming
   openai/structured-outputs
   openai/compatible-apis
```

- [ ] **Step 3: Verify**

Run: `sbt compileDocumentation` — Expected: success (this now compile-checks the ported `mdoc:compile-only` snippets; fix any failure per Global Constraints).
Run: `cd docs && source .venv/bin/activate && make html` — Expected: `build succeeded.`, 0 warnings.

- [ ] **Step 4: Regenerate and commit**

```bash
sbt "docs/mdoc"
git add docs/openai docs/index.md generated-docs/out
git commit -m "docs: add OpenAI documentation pages"
```

---

### Task 5: Claude pages

**Files:**
- Create: `docs/claude/basics.md`, `docs/claude/messages.md`, `docs/claude/structured-outputs.md`, `docs/claude/tool-calling.md`, `docs/claude/streaming.md`, `docs/claude/models-and-errors.md`
- Modify: `docs/index.md` (append toctree)
- Regenerate: `generated-docs/out/`

**Interfaces:**
- Consumes: README line ranges below.
- Produces: the six `claude/*` pages.

- [ ] **Step 1: Create the six pages** (porting rules apply):

| Page | README lines | Top heading (h1) |
|---|---|---|
| `docs/claude/basics.md` | 144–233 | `# Claude API` |
| `docs/claude/messages.md` | 234–326 | `# Messages API` |
| `docs/claude/structured-outputs.md` | 327–464 | `# Structured outputs` |
| `docs/claude/tool-calling.md` | 465–530 | `# Tool calling` |
| `docs/claude/streaming.md` | 531–607 | `# Streaming` |
| `docs/claude/models-and-errors.md` | 608–685 | `# Models, errors and the sync client` |

`models-and-errors.md` contains four README `###` sections (Models API, Error Handling, Key Differences from OpenAI API, Synchronous Claude Client) — each becomes an `##` under the single h1.

- [ ] **Step 2: Append the toctree to `docs/index.md`**

```
.. toctree::
   :maxdepth: 2
   :caption: Claude

   claude/basics
   claude/messages
   claude/structured-outputs
   claude/tool-calling
   claude/streaming
   claude/models-and-errors
```

- [ ] **Step 3: Verify**

Run: `sbt compileDocumentation` — Expected: success.
Run: `cd docs && source .venv/bin/activate && make html` — Expected: `build succeeded.`, 0 warnings.

- [ ] **Step 4: Regenerate and commit**

```bash
sbt "docs/mdoc"
git add docs/claude docs/index.md generated-docs/out
git commit -m "docs: add Claude documentation pages"
```

---

### Task 6: Agent loop pages

**Files:**
- Create: `docs/agents/quickstart.md`, `docs/agents/configuration.md`, `docs/agents/tools.md`
- Modify: `docs/index.md` (append toctree)
- Regenerate: `generated-docs/out/`

**Interfaces:**
- Consumes: README line ranges below.
- Produces: the three `agents/*` pages.

- [ ] **Step 1: Create the three pages** (porting rules apply):

| Page | README lines | Top heading (h1) |
|---|---|---|
| `docs/agents/quickstart.md` | 686–739 | `# Agent loop` |
| `docs/agents/configuration.md` | 740–870 | `# Agent configuration` |
| `docs/agents/tools.md` | 871–954 | `# Agent tools and results` |

`configuration.md` covers the README's "Core Components" range: Agent Configuration, Hooks (beforeToolCall / afterToolCall), Exception Handling. `tools.md` covers Tool Definition, Agent Result, and `runAs[T]`.

- [ ] **Step 2: Append the toctree to `docs/index.md`**

```
.. toctree::
   :maxdepth: 2
   :caption: Agent loop

   agents/quickstart
   agents/configuration
   agents/tools
```

- [ ] **Step 3: Verify**

Run: `sbt compileDocumentation` — Expected: success.
Run: `cd docs && source .venv/bin/activate && make html` — Expected: `build succeeded.`, 0 warnings.

- [ ] **Step 4: Regenerate and commit**

```bash
sbt "docs/mdoc"
git add docs/agents docs/index.md generated-docs/out
git commit -m "docs: add agent loop documentation pages"
```

---

### Task 7: Other pages

**Files:**
- Create: `docs/other/backends.md`, `docs/other/examples.md`
- Modify: `docs/index.md` (append toctree)
- Regenerate: `generated-docs/out/`

**Interfaces:**
- Consumes: README line ranges below.
- Produces: the two `other/*` pages; completes the site's page set.

- [ ] **Step 1: Create the two pages** (porting rules apply):

| Page | README lines | Top heading (h1) |
|---|---|---|
| `docs/other/backends.md` | 955–1021 | `# Backends and effect systems` |
| `docs/other/examples.md` | 1022–1030 | `# Running examples` |

- [ ] **Step 2: Append the toctree to `docs/index.md`**

```
.. toctree::
   :maxdepth: 2
   :caption: Other

   other/backends
   other/examples
```

- [ ] **Step 3: Verify**

Run: `sbt compileDocumentation` — Expected: success.
Run: `cd docs && source .venv/bin/activate && make html` — Expected: `build succeeded.`, 0 warnings.

- [ ] **Step 4: Regenerate and commit**

```bash
sbt "docs/mdoc"
git add docs/other docs/index.md generated-docs/out
git commit -m "docs: add backends and examples pages"
```

---

### Task 8: Slim the README and final verification

**Files:**
- Modify: `README.md` (full replacement below)
- Regenerate: `generated-docs/out/` (unchanged expected; regenerate to confirm)

**Interfaces:**
- Consumes: all pages from Tasks 3–7 (README now defers to the site).
- Produces: the final slimmed README.

- [ ] **Step 1: Replace `README.md` with** (README is no longer mdoc-processed, so version strings stay literal — update `0.5.2` to the latest released version if it has moved):

````markdown
![sttp-ai](https://github.com/softwaremill/sttp-ai/raw/master/banner.png)

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/open-source)
[![CI](https://github.com/softwaremill/sttp-ai/workflows/CI/badge.svg)](https://github.com/softwaremill/sttp-ai/actions?query=workflow%3ACI+branch%3Amaster)

[![sttp.ai:core](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/core_3/badge.svg?subject=sttp.ai:core)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/core_3/)
[![sttp.ai:openai](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/openai_3/badge.svg?subject=sttp.ai:openai)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/openai_3/)
[![sttp.ai:claude](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/claude_3/badge.svg?subject=sttp.ai:claude)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/claude_3/)

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): The Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs. Use the power of ChatGPT and Claude inside your code!

sttp-ai uses sttp client to describe requests and responses used in OpenAI, Claude (Anthropic), and OpenAI-compatible endpoints.

## Documentation

**Full documentation is available at [sttp-ai.softwaremill.com](https://sttp-ai.softwaremill.com).**

## Quickstart

### For OpenAI/OpenAI-compatible APIs

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "openai" % "0.5.2"
```

### For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "0.5.2"

// For streaming support, add one or more:
"com.softwaremill.sttp.ai" %% "claude-streaming-fs2" % "0.5.2"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "claude-streaming-zio" % "0.5.2"    // ZIO
"com.softwaremill.sttp.ai" %% "claude-streaming-akka" % "0.5.2"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "claude-streaming-pekko" % "0.5.2"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "claude-streaming-ox" % "0.5.2"    // Ox direct-style (Scala 3 only)
```

sttp-openai is available for Scala 2.13 and Scala 3

Then head to the [documentation](https://sttp-ai.softwaremill.com) for usage examples: OpenAI and Claude clients, streaming, structured outputs, tool calling, and the agent loop.

## Contributing

If you have a question, or hit a problem, feel free to post on our community https://softwaremill.community/c/open-source/

Or, if you encounter a bug, something is unclear in the code or documentation, don't hesitate and open an issue on GitHub.

For running integration tests against the real OpenAI API, see [Integration Testing Guide](INTEGRATION_TESTING.md).

## Commercial Support

We offer commercial support for sttp and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2023-2025 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
````

- [ ] **Step 2: Content-completeness check** — walk the original README's heading list (`git show 33fc235:README.md | grep -n "^#"`) and confirm every section is either on a docs page (Tasks 3–7 tables) or retained in the slimmed README (Intro, Quickstart, Contributing, Commercial Support, Copyright). Expected mapping — every heading accounted for, none silently dropped. If any range was missed, add it to the matching page now.

- [ ] **Step 3: Full verification**

Run: `sbt compileDocumentation` — Expected: success.
Run: `sbt "docs/mdoc"` then `git status --short generated-docs/out` — Expected: no changes (README is not a docs source).
Run: `cd docs && source .venv/bin/activate && make html` — Expected: `build succeeded.`, 0 warnings.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: slim README, defer to the documentation site"
```

---

## Manual follow-ups (outside this plan)

- Create the `sttp-ai` project on readthedocs.org, connect it to `softwaremill/sttp-ai`.
- Configure the custom domain `sttp-ai.softwaremill.com` on Read the Docs and point DNS at it.
