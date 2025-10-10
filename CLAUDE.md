# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

sttp-ai is a Scala library providing a non-official client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs. Built on sttp HTTP client with support for sync/async operations and various effect systems (cats-effect, ZIO, Akka/Pekko Streams, Ox).

**Key Features:**
- Native OpenAI API support (Chat, Completions, Embeddings, Audio, Images, etc.)
- Native Claude (Anthropic) API support with dedicated module
- OpenAI-compatible API support (Ollama, Grok, etc.)
- Streaming support for all major effect systems
- Cross-platform: Scala 2.13.16 and Scala 3.3.6

## Development Commands

### Essential Commands
```bash
# Compile
sbt compile                              # All modules
sbt core/compile                         # OpenAI module
sbt claude/compile                       # Claude module

# Test
sbt test                                 # Unit tests (excludes integration)
sbt "testOnly *OpenAIIntegrationSpec"   # OpenAI integration (requires OPENAI_API_KEY)
sbt "testOnly *ClaudeIntegrationSpec"   # Claude integration (requires ANTHROPIC_API_KEY)
./run-integration-tests.sh              # All integration tests

# Format (CRITICAL - always run after changes!)
sbt scalafmtAll                         # Format all code
sbt scalafmtCheck                       # Verify formatting
sbt Test/scalafmtCheck                  # Verify test formatting

# Documentation
sbt compileDocumentation                # Compile mdoc documentation
```

**If `jetbrains` MCP is available, USE `mcp__jetbrains__reformat_file` tool instead of running `sbt scalafmtAll` command.**

### Model Update Scripts
```bash
# Update OpenAI model definitions (automated workflow)
scala-cli model_update_scripts/scrape_models.scala                        # 1. Scrape models
scala-cli model_update_scripts/update_code_with_new_models.scala --apply  # 2. Update code
sbt scalafmtAll                                                           # 3. Format
```

## Architecture Patterns

### Dual API Support (OpenAI + Claude)

**Core Differences:**

| Aspect | OpenAI (`core/`) | Claude (`claude/`) |
|--------|-----------------|-------------------|
| **Client** | `OpenAI` / `OpenAISyncClient` | `ClaudeClient` / `ClaudeSyncClient` |
| **Return Type** | `Either[OpenAIException, A]` | `Either[ClaudeException, A]` |
| **Message Content** | Simple strings | `ContentBlock` arrays (rich content) |
| **System Messages** | Role-based in messages array | Separate `system` parameter |
| **Authentication** | `Authorization: Bearer <key>` | `x-api-key: <key>` + `anthropic-version` |
| **Package Structure** | `sttp.ai.openai.*` | `sttp.ai.claude.*` |

**Shared Patterns:**
- Both use **uPickle with SnakePickle** for JSON (snake_case conversion)
- Both have **comprehensive exception hierarchies** for API errors
- Both support **streaming via SSE** with same effect systems

### Streaming Architecture

Each streaming module (`streaming/{effect-system}/`) provides extensions for **both** APIs:

| Effect System | Module Location | OpenAI Extension | Claude Extension | Scala Version |
|--------------|----------------|-----------------|------------------|---------------|
| **fs2** | `streaming/fs2/` | `sttp.ai.openai.streaming.fs2.*` | `sttp.ai.claude.streaming.fs2.*` | 2.13, 3 |
| **zio** | `streaming/zio/` | `sttp.ai.openai.streaming.zio.*` | `sttp.ai.claude.streaming.zio.*` | 2.13, 3 |
| **akka** | `streaming/akka/` | `sttp.ai.openai.streaming.akka.*` | `sttp.ai.claude.streaming.akka.*` | 2.13 only |
| **pekko** | `streaming/pekko/` | `sttp.ai.openai.streaming.pekko.*` | `sttp.ai.claude.streaming.pekko.*` | 2.13, 3 |
| **ox** | `streaming/ox/` | `sttp.ai.openai.streaming.ox.*` | `sttp.ai.claude.streaming.ox.*` | 3 only |

**Pattern:** Extension methods add `.parseSSE` + `.parseClaudeStreamResponse`/`.parseOpenAIStreamResponse`

### Key Navigation Tips

- **OpenAI API endpoints**: `core/src/main/scala/sttp/ai/openai/requests/{api-category}/`
- **Claude API code**: `claude/src/main/scala/sttp/ai/claude/`
- **OpenAI models**: Search for `ChatCompletionModel`, `EmbeddingModel` in `core/` request bodies
- **Claude models**: `claude/src/main/scala/sttp/ai/claude/models/ClaudeModel.scala`
- **Streaming implementations**: `streaming/{effect-system}/src/main/scala/`
- **Examples**: `examples/src/main/scala/examples/` (runnable with scala-cli)
- **Tests**: Each module has `{module}/src/test/` following same package structure

**Request package mirrors OpenAI API structure**: `requests/{api-category}/` contains endpoint-specific request/response models.

## Code Style & Formatting

### Critical Formatting Workflow

**⚠️ MANDATORY: Run `sbt scalafmtAll` or `mcp__jetbrains__reformat_file` after EVERY code change!**

**When to format:**
- After creating/modifying files
- After implementing features
- After writing/modifying tests
- Before committing changes

**Why this is critical:**
- CI/CD fails without proper formatting
- Prevents merge conflicts
- Required before PR merge

### Code Style Rules

- **Formatting**: Scalafmt with max column 140, Scala 3 dialect
- **Imports**:
  - AVOID `import _root_.xxxx.yyyy`, USE `import xxxx.yyyy`
  - **Scala 3 syntax preferred**: `import package.*` (not `import package._`)
  - SortImports rule applied, RedundantBraces/Parens removed
- **Naming**: Snake_case for JSON fields (handled by SnakePickle)
- **Models**: Case objects extending sealed traits, companion values for easy access
- **Documentation**: Always use Scala 3 syntax (`@main`, `given`, `import package.*`)

## Testing Strategy

- **Unit tests**: `*/src/test/` for all modules
- **Integration tests**: Hit real APIs, cost-efficient (minimal inputs)
  - OpenAI: Requires `OPENAI_API_KEY`, 30s timeouts, rate limiting handled
  - Claude: Requires `ANTHROPIC_API_KEY`, minimal token usage
  - Auto-skip if API key not set
- **Cross-building**: sbt-projectmatrix for Scala 2.13.16 & 3.3.6

## Client Implementation Patterns

- **Sync Clients**: `OpenAISyncClient` / `ClaudeSyncClient` - Use `DefaultSyncBackend`, block on responses, may throw exceptions
- **Async Clients**: `OpenAI` / `ClaudeClient` - Raw sttp requests, choose backend (cats-effect, ZIO, etc.)
- **Custom Backends**: Pass backend to `.send(backend)`
- **OpenAI-Compatible**: Use `OpenAI` client with custom base URL for Ollama, Grok, etc.

## Debugging with Scratch Files (*.sc)

Scratch files are powerful debugging tools using scala-cli for rapid prototyping without full sbt builds.

### When to Use

**Ideal for:**
- JSON serialization debugging (test uPickle behavior)
- API request validation (verify structures before integration tests)
- Library behavior testing (test specific features/edge cases)
- Hypothesis validation (confirm assumptions)
- Cost-effective debugging (test locally before hitting paid APIs)

### Quick Patterns

```scala
// debug_serialization.sc - Test JSON output
//> using dep com.softwaremill.sttp.ai::claude:0.3.10+SNAPSHOT
import sttp.ai.claude.json.SnakePickle._

val obj = MyModel(...)
println(write(obj))  // Check JSON structure
```

### Best Practices

**Naming:**
- `debug_*.sc` - Debugging specific issues
- `test_*.sc` - Testing functionality
- `validate_*.sc` - Validation/verification

**Dependencies:**
- Use explicit versions: `//> using dep group::artifact:version`
- Use SNAPSHOT for unreleased: `//> using repository ivy2Local`

**Cleanup:**
- Remove scratch files after debugging
- **Never commit .sc files to repository**
- Document findings in code comments/issues

**Benefits:**
- Compile/run in seconds vs. minutes for sbt
- Test JSON locally before hitting paid APIs
- Isolate issues without dependency complexity

## Development Workflow Checklist

For every implementation phase:
- [ ] Write/modify code
- [ ] **Run `sbt scalafmtAll` (CRITICAL - never skip!)**
- [ ] Run `sbt scalafmtCheck` and `sbt Test/scalafmtCheck`
- [ ] Run `sbt compile`
- [ ] Run relevant tests
- [ ] Commit changes

## Important Reminders

- **Do what has been asked; nothing more, nothing less**
- **ALWAYS prefer editing existing files to creating new ones**
- **NEVER proactively create documentation (*.md) or README files** unless explicitly requested
- **ALWAYS use Scala 3 syntax** in docs, examples, and README
- **Format code after EVERY change** - think of it as part of "save"

## CI/CD

- **GitHub Actions**: SoftwareMill shared workflows
- **Java 21**: Build/test environment
- **Scala Steward**: Automated dependency updates
- **Auto-merge**: For dependency PRs from softwaremill-ci
- **Publishing**: Automatic releases on version tags

## Additional Resources

- **Examples**: See `examples/` directory for runnable scala-cli examples (OpenAI and Claude)
- **Integration Testing**: See `INTEGRATION_TESTING.md` for detailed API setup
- **API Documentation**:
  - OpenAI: https://platform.openai.com/docs/api-reference
  - Claude: https://docs.anthropic.com/claude/reference