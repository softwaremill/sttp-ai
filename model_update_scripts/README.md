# OpenAI Model Update Scripts

This directory contains two Scala scripts for automatically updating OpenAI model definitions in the codebase. The process is split into two separate stages for better control and review.

## Prerequisites

- **Commit your changes first**: It's best to commit everything before using these scripts to easily review changes with `git diff`
- Scala CLI installed

## Scripts Overview

### 1. `scrape_models.scala` - Web Scraping Stage
Scrapes OpenAI's documentation website to extract current model information and generates a JSON mapping file.

**Usage:**
```bash
scala-cli scrape_models.scala
```

**Options:**
- `--debug` - Enable debug logging for detailed output
- `--models <model1,model2,...>` - Filter specific models (e.g., "GPT-4o,GPT-3.5")  
- `--output <file.json>` - Output file path (default: `models.json`)

**What it does:**
- Uses Playwright with Firefox to scrape OpenAI's model documentation pages (Firefox specifically is used to bypass Cloudflare protection on OpenAI docs)
- Extracts model names, supported endpoints, and model snapshots
- Generates a structured JSON file with endpoint-to-models mapping

**Note:** LLMs aren't great at producing future-proof HTML DOM selectors, even with samples. Manual adjustments to the scraping logic may be needed when OpenAI changes their documentation structure.

### 2. `update_code_with_new_models.scala` - Code Update Stage
Reads the JSON mapping and updates Scala case class definitions in the codebase.

**Usage:**
```bash
scala-cli update_code_with_new_models.scala
```

**Options:**
- `--input <models.json>` - Input JSON file (default: `models.json`)
- `--config <config.yaml>` - Configuration file (default: `model_update_config.yaml`)
- `--dry-run` - Preview changes without applying them (default behavior)
- `--apply` - Apply changes to files
- `--debug` - Enable debug logging

**What it does:**
- Reads endpoint mappings from JSON file
- Updates case object definitions in specified Scala files
- Maintains alphabetical ordering of all model case objects in single blocks
- Updates values sets where configured
- Converts model names to proper Scala identifiers

**Important:** This script only adds new models - it doesn't remove old/deprecated models. Model removal must be done manually.

## Configuration

The `model_update_config.yaml` file defines:
- **Endpoints mapping**: Which files and classes to update for each API endpoint
- **Name conversion rules**: How to convert model names to Scala identifiers
- **Insert markers**: Where to place new case objects in each file
- **Values sets**: Optional companion value sets to maintain
- **Capability tags**: Optional `Capability` mixins to render on each endpoint's model constants (see below)

### Capability tags

The `capabilities` section maps an endpoint (e.g. `"v1/chat/completions"`) to a list of glob `rules` plus a
`defaults` fallback. Each rule has a `pattern` (glob over the model id, `*` matches any characters) and a
`capabilities` list; **the first matching rule (top to bottom) wins** and gives the model's complete capability
set. A model id matching no rule falls back to `defaults`. This section is hand-curated - it is never scraped
or inferred from the model listing.

On `--apply`, every constant in an endpoint's managed block - both existing and newly added - is re-rendered
with `with Capability.X` mixins resolved fresh from this config, in canonical order (`Vision`, `ToolCalling`,
`StructuredOutput`, `Reasoning`); a model with all four capabilities is rendered with the `with Capability.All`
shorthand instead. Any hand-written mixins already in the file are discarded; the yaml config is
the source of truth. Endpoints with no `capabilities` entry render with no mixin clause, unchanged from before.

Note: the script only manages the mixin clauses. Before adding a `capabilities` entry for a new endpoint, the
target file must already import `sttp.ai.core.model.Capability` and its model class must extend
`sttp.ai.core.model.AIModel` — otherwise the generated `with Capability.X` clauses won't compile.

## Default Behavior

- **JSON file**: `models.json` is used by default
- **Dry run**: Both scripts run in preview mode by default
- **Stable formatting**: `models.json` is formatted consistently for clean git diffs
- **Single blocks**: All model case classes are maintained in one continuous block per file

## Workflow

1. **Commit your code** to have a clean baseline
2. **Scrape models**: `scala-cli scrape_models.scala` 
3. **Review changes**: Check `git diff models.json` to see what models changed
4. **Preview updates**: `scala-cli update_code_with_new_models.scala` (dry-run by default)
5. **Apply changes**: `scala-cli update_code_with_new_models.scala --apply`
6. **Format code**: Run `scala fmt` formatter on modified files
7. **Review final changes**: Use `git diff` to see all code modifications

