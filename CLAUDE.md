# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

sttp-ai is a Scala library providing a non-official client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs. It's built on top of the sttp HTTP client library and supports both sync and async operations with various effect systems (cats-effect, ZIO, Akka/Pekko Streams, Ox).

**Key Features:**
- Native OpenAI API support (Chat, Completions, Embeddings, Audio, Images, etc.)
- Native Claude (Anthropic) API support with dedicated module
- OpenAI-compatible API support (Ollama, Grok, etc.)
- Streaming support for all major effect systems
- Cross-platform: Scala 2.13 and Scala 3

## Project Structure

### Root Directory Layout
```
sttp-ai/
├── core/                    # OpenAI API client library
├── claude/                  # Claude (Anthropic) API client library
├── streaming/               # Effect-system specific streaming implementations
│   ├── fs2/                # cats-effect/fs2 streaming (OpenAI + Claude)
│   ├── zio/                # ZIO streaming (OpenAI + Claude)
│   ├── akka/               # Akka Streams (Scala 2.13 only, OpenAI + Claude)
│   ├── pekko/              # Pekko Streams (OpenAI + Claude)
│   └── ox/                 # Ox direct-style streaming (Scala 3 only, OpenAI + Claude)
├── examples/               # Runnable examples using scala-cli
├── model_update_scripts/   # Automation for updating OpenAI model definitions
├── project/                # sbt build configuration
├── generated-docs/         # Generated documentation (mdoc output)
└── .github/workflows/      # CI/CD GitHub Actions
```

### Core Module Structure (`core/src/main/scala/sttp/openai/`)
```
sttp.ai.openai/
├── OpenAI.scala                 # Raw sttp requests returning Either[OpenAIException, A]
├── OpenAISyncClient.scala       # High-level sync client that may throw exceptions
├── OpenAIExceptions.scala       # Exception hierarchy for API errors
├── json/                        # JSON serialization utilities
│   ├── SnakePickle.scala       # Snake_case conversion for uPickle
│   └── SttpUpickleApiExtension.scala
└── requests/                    # API endpoint definitions (mirrors OpenAI API structure)
```

### Claude Module Structure (`claude/src/main/scala/sttp/ai/claude/`)
```
sttp.ai.claude/
├── ClaudeClient.scala           # Raw sttp requests returning Either[ClaudeException, A]
├── ClaudeSyncClient.scala       # High-level sync client that may throw exceptions
├── ClaudeExceptions.scala       # Claude-specific exception hierarchy
├── config/
│   └── ClaudeConfig.scala      # Configuration (API key, version, base URL)
├── json/
│   └── SnakePickle.scala       # Snake_case conversion for uPickle (Claude variant)
├── models/                      # Claude-specific models
│   ├── ContentBlock.scala      # Rich content blocks (text, images, tool results)
│   ├── Message.scala           # User/Assistant messages
│   ├── Tool.scala              # Tool definitions and schemas
│   ├── ClaudeModel.scala       # Available Claude models
│   └── Usage.scala             # Token usage tracking
├── requests/
│   └── MessageRequest.scala    # Message API request builder
└── responses/                   # API response models
    ├── MessageResponse.scala
    ├── MessageStreamResponse.scala
    └── ModelsResponse.scala
```

**Key Architectural Differences:**
- **Claude uses ContentBlock** for rich message content (text, images, tool use/results)
- **OpenAI uses simple strings** in message content
- **Claude has system parameter** separate from messages
- **OpenAI uses system role** in messages array
- **Both support streaming** via dedicated streaming modules

### Requests Package Organization (`requests/`)
The `requests/` package closely mirrors OpenAI's API structure. Each directory contains request/response models and configurations:

```
requests/
├── completions/             # Text completion APIs
│   ├── chat/               # Chat completions (most complex)
│   │   ├── message/        # Message types, content, tools
│   │   ├── ChatRequestBody.scala
│   │   ├── ChatRequestResponseData.scala
│   │   ├── ChatChunkRequestResponseData.scala  # Streaming responses
│   │   └── ToolCall.scala
│   ├── CompletionsRequestBody.scala
│   └── CompletionsResponseData.scala
├── audio/                   # Audio APIs
│   ├── speech/             # Text-to-speech
│   ├── transcriptions/     # Speech-to-text
│   └── translations/       # Audio translation
├── images/                  # Image generation APIs
│   ├── creation/           # Image generation
│   ├── edit/               # Image editing
│   └── variations/         # Image variations
├── embeddings/             # Text embeddings API
├── assistants/             # OpenAI Assistants API
├── threads/                # Conversation threads
│   ├── messages/          # Thread messages
│   └── runs/              # Thread runs and tool calls
├── vectorstore/            # Vector stores and file management
│   └── file/              # Vector store file operations
├── files/                  # File upload/management
├── models/                 # Model listing and management
├── moderations/            # Content moderation
├── finetuning/            # Model fine-tuning
├── batch/                 # Batch API requests
├── admin/                 # Admin/organization APIs
├── responses/             # Response generation API
└── upload/                # Multipart upload handling
```

### Streaming Modules Pattern
Each streaming module provides extension methods for both OpenAI and Claude streaming:

```
streaming/{effect-system}/src/main/scala/
├── sttp/openai/streaming/{effect}/package.scala     # OpenAI streaming extensions
└── sttp/ai/claude/streaming/{effect}/               # Claude streaming extensions
    └── Claude{Effect}Streaming.scala
```

**Supported Effect Systems:**

| Effect System | OpenAI Stream Type | Claude Stream Type | Scala Version |
|--------------|-------------------|-------------------|---------------|
| **fs2** | `Stream[F, ChatChunkResponse]` | `Stream[F, MessageStreamResponse]` | 2.13, 3 |
| **zio** | `ZStream[Any, OpenAIException, ChatChunkResponse]` | `ZStream[Any, ClaudeException, MessageStreamResponse]` | 2.13, 3 |
| **akka** | `Source[ChatChunkResponse, NotUsed]` | `Source[MessageStreamResponse, NotUsed]` | 2.13 only |
| **pekko** | `Source[ChatChunkResponse, NotUsed]` | `Source[MessageStreamResponse, NotUsed]` | 2.13, 3 |
| **ox** | `Flow[ChatChunkResponse]` | `Flow[MessageStreamResponse]` | 3 only |

**Streaming Pattern:**
- Extension methods add `.parseSSE` and `.parseClaudeStreamResponse`/`.parseOpenAIStreamResponse`
- Server-Sent Events (SSE) parsing built-in
- Both APIs use similar streaming patterns with different response types

### Build Configuration (`project/`)
```
project/
├── Dependencies.scala       # Centralized version and dependency management
├── plugins.sbt            # sbt plugins configuration
└── build.properties       # sbt version
```

### Navigation Tips
- **OpenAI API endpoint code**: Look in `core/src/main/scala/sttp/openai/requests/{api-category}/`
- **Claude API code**: Look in `claude/src/main/scala/sttp/ai/claude/`
- **OpenAI model definitions**: Search for `ChatCompletionModel`, `EmbeddingModel`, etc. in `core/` request body files
- **Claude model definitions**: See `ClaudeModel.scala` in `claude/src/main/scala/sttp/ai/claude/models/`
- **Streaming implementation**:
  - OpenAI: `streaming/{effect-system}/src/main/scala/sttp/openai/streaming/{effect}/`
  - Claude: `streaming/{effect-system}/src/main/scala/sttp/ai/claude/streaming/{effect}/`
- **Examples**: All runnable examples are in `examples/src/main/scala/examples/`
- **Tests**: Each module has tests in `{module}/src/test/` following same package structure

## Development Commands

### Build and Test
```bash
# Compile the project (all modules)
sbt compile

# Compile specific module
sbt core/compile
sbt claude/compile

# Run all tests (excluding integration tests)
sbt test

# Run tests for specific module
sbt core/test
sbt claude/test

# Run integration tests (requires OPENAI_API_KEY and/or ANTHROPIC_API_KEY)
./run-integration-tests.sh

# Run specific integration test
sbt "testOnly *OpenAIIntegrationSpec"
sbt "testOnly *ClaudeIntegrationSpec"

# Compile documentation
sbt compileDocumentation

# Format code (CRITICAL - always run after changes)
sbt scalafmtAll

# Verify formatting
sbt scalafmtCheck
sbt Test / scalafmtCheck
```

If `jetbrains` MCP is available USE `mcp__jetbrains__reformat_file` tool instead of running `sbt scalafmtAll` command.

### ⚠️ CRITICAL: Code Formatting Workflow

**ALWAYS run `sbt scalafmtAll` or `mcp__jetbrains__reformat_file` after implementing each step or phase!**

The project uses Scalafmt for consistent code formatting. You MUST run formatting after:
- Creating new files
- Modifying existing files
- Adding new functionality
- Completing any implementation phase

**Workflow Example:**
1. Implement feature/fix
2. Run `sbt scalafmtAll` or `mcp__jetbrains__reformat_file` ← NEVER SKIP THIS STEP
3. Run `sbt scalafmtCheck` and `sbt Test / scalafmtCheck` to verify if needed
4. Run `sbt compile` to verify compilation
5. Run tests
6. Commit changes

**Why this matters:**
- CI/CD pipeline will fail if code is not properly formatted
- Maintains consistent code style across the entire codebase
- Prevents formatting-related merge conflicts
- Required before any PR can be merged

### Cross-platform Building
The project uses sbt-projectmatrix for cross-building:
- Scala 2.13.16 and Scala 3.3.6 support
- Core module supports both versions
- Streaming modules (fs2, zio, pekko, ox) have specific version requirements
- Akka module only supports Scala 2.13
- Ox module only supports Scala 3

### Testing Strategy
- Unit tests for all modules in `*/src/test`
- Integration tests that hit real APIs:
  - OpenAI integration tests require `OPENAI_API_KEY`
  - Claude integration tests require `ANTHROPIC_API_KEY`
  - Tests automatically skip if respective API key not set
  - Cost-efficient: minimal inputs to reduce API costs
- Use `sbt "testOnly *OpenAIIntegrationSpec"` for OpenAI integration tests
- Use `sbt "testOnly *ClaudeIntegrationSpec"` for Claude integration tests

## Architecture

### Dual API Support Pattern

The library supports two distinct AI APIs with parallel architectures:

**OpenAI Module (`core/`):**
- **OpenAI**: Raw sttp-client4 requests returning `Either[OpenAIException, A]`
- **OpenAISyncClient**: High-level synchronous client with blocking methods that may throw `OpenAIException`
- **Requests Package**: All API endpoint definitions organized by OpenAI API categories
  - `completions.chat` - Chat completions API (most used)
  - `audio` - Speech synthesis, transcriptions, translations
  - `images` - Image generation, editing, variations
  - `embeddings` - Text embeddings
  - `files`, `assistants`, `threads` - OpenAI platform features

**Claude Module (`claude/`):**
- **ClaudeClient**: Raw sttp-client4 requests returning `Either[ClaudeException, A]`
- **ClaudeSyncClient**: High-level synchronous client with blocking methods that may throw `ClaudeException`
- **ContentBlock Architecture**: Rich message content supporting text, images, and tool interactions
- **MessageRequest**: Streamlined API with `.simple()`, `.withSystem()`, `.withTools()` builders
- **Authentication**: Uses `x-api-key` header + `anthropic-version` (different from OpenAI)

**Shared Patterns:**
- **JSON Handling**: Both use uPickle with SnakePickle for snake_case conversion
- **Error Handling**: Comprehensive exception hierarchies for different API errors
- **Streaming Support**: Same effect systems, different response types

### Streaming Architecture
Each streaming module provides extension methods for both APIs:
- **OpenAI**: Extension methods for `ChatChunkResponse` streaming
- **Claude**: Extension methods for `MessageStreamResponse` streaming
- Backend-specific implementations (HttpClientFs2Backend, HttpClientZioBackend, etc.)
- Server-Sent Events (SSE) parsing built-in

### Client Implementation Patterns
- **Sync Clients**: Use DefaultSyncBackend, block on responses, may throw exceptions
- **Async Clients**: Use raw client classes with chosen backend (cats-effect, ZIO, etc.)
- **Custom Backends**: Support for any sttp backend through `.send(backend)`
- **OpenAI-Compatible**: Use OpenAI client with custom base URL for Ollama, Grok, etc.

## Model Management

The project includes automated scripts for updating OpenAI model definitions:

### Model Update Workflow
1. **Scrape Models**: `scala-cli model_update_scripts/scrape_models.scala`
    - Uses Playwright + Firefox to scrape OpenAI docs
    - Generates `models.json` with current model mappings
2. **Update Code**: `scala-cli model_update_scripts/update_code_with_new_models.scala --apply`
    - Updates Scala case objects with new models
    - Maintains alphabetical ordering
    - Only adds models (manual removal required)
3. **Format**: Run `sbt scalafmtAll` after updates

## Code Style

- **Formatting**: Uses Scalafmt with max column 140, Scala 3 dialect
- **Naming**: Snake case for JSON fields (handled by SnakePickle)
- **Imports**:
  - SortImports rule applied, RedundantBraces/Parens removed
  - AVOID using "import _root_.xxxx.yyyy", just USE "import xxxx.yyyy"
  - **Scala 3 syntax preferred**: Use `import package.*` instead of `import package._`
- **Case Objects**: Models defined as case objects extending sealed traits
- **Companion Values**: Some models maintain values sets for easy access
- **Documentation**:
  - All examples in README should use Scala 3 syntax
  - Use `@main` instead of `extends App`
  - Use `given` instead of `implicit val` where applicable

## Dependencies

- **sttp-client4**: HTTP client foundation (v4.0.11)
- **upickle/ujson**: JSON serialization (v4.3.2)
- **tapir**: API specification and JSON schema generation (v1.11.44)
- **scalatest**: Testing framework (v3.2.19)
- **Effect Libraries**: fs2, ZIO, Akka/Pekko Streams, Ox for streaming

## Examples

The `examples/` module contains runnable examples using scala-cli:
- **OpenAI Examples**:
  - Basic chat completion
  - Streaming with different backends (fs2, ZIO, Ox)
  - Structured outputs with JSON Schema
  - Function calling with automatic schema generation
  - OpenAI-compatible APIs (Ollama, Grok)
- **Claude Examples**:
  - Basic message requests
  - Streaming with different effect systems
  - Tool calling with native Claude schemas
  - Image analysis with ContentBlock

## CI/CD

- **GitHub Actions**: Uses SoftwareMill's shared workflows
- **Java 21**: Build and test environment
- **Scala Steward**: Automated dependency updates
- **Auto-merge**: For dependency PRs from softwaremill-ci
- **Publishing**: Automatic releases on version tags

## Integration Testing

Integration tests hit real APIs but are designed to be cost-efficient:

**OpenAI Integration Tests:**
- Require `OPENAI_API_KEY` environment variable
- Automatically skip if key not set
- Minimal inputs to reduce API costs
- 30-second timeouts
- Rate limiting handling

**Claude Integration Tests:**
- Require `ANTHROPIC_API_KEY` environment variable
- Automatically skip if key not set
- Cost-efficient: minimal token usage
- Test core functionality: messages, streaming, models API

See `INTEGRATION_TESTING.md` for detailed setup.

# 🚨 IMPORTANT DEVELOPMENT REMINDERS

## Code Formatting is MANDATORY

**NEVER forget to run `sbt scalafmtAll` after ANY code changes!**

This is not optional - it's a required part of the development workflow:

1. **After creating new files** → `sbt scalafmtAll`
2. **After modifying existing files** → `sbt scalafmtAll`
3. **After implementing any feature** → `sbt scalafmtAll`
4. **After writing/modifying tests** → `sbt Test / scalafmt` (CRITICAL!)
5. **Before committing changes** → `sbt scalafmtAll`
6. **Before creating PRs** → `sbt scalafmtAll`

**Memory aid:** Think of `sbt scalafmtAll` as part of the "save" operation - you haven't properly completed your work until ALL code (including tests) is formatted.

## Development Checklist

For every implementation phase:
- [ ] Write/modify code
- [ ] Run `sbt scalafmtAll` (CRITICAL - never skip)
- [ ] Run `sbt scalafmtCheck` and `sbt Test/scalafmtCheck` (verify formatting)
- [ ] Run `sbt compile`
- [ ] Run relevant tests
- [ ] Commit changes

**Why this is critical:**
- Unformatted code will cause CI failures
- Inconsistent formatting creates merge conflicts
- Team productivity suffers from formatting inconsistencies
- PRs cannot be merged with formatting violations

## Debugging with Scratch Files (*.sc)

Scratch files (`.sc` extension) are powerful debugging tools for rapid prototyping and issue isolation. They compile and run independently using scala-cli, providing immediate feedback without the overhead of the full sbt build cycle.

### When to Use Scratch Files

**Ideal for:**
- **JSON serialization debugging** - Test uPickle behavior and field serialization
- **API request validation** - Verify request/response structures before integration tests
- **Library behavior testing** - Quickly test specific library features or edge cases
- **Hypothesis validation** - Confirm assumptions about code behavior
- **Cost-effective API debugging** - Test locally before hitting paid APIs

**Examples from Claude API implementation:**
```scala
// debug_tool_schema.sc - Test ToolInputSchema JSON serialization
//> using dep com.softwaremill.sttp.ai.openai::claude:0.3.10+SNAPSHOT
import sttp.ai.claude.models._
import sttp.ai.claude.json.SnakePickle._

val weatherTool = Tool(...)
val json = write(weatherTool)
println("Tool JSON:")
println(json)
// Immediately see if 'type' field is present
```

### Common Debugging Patterns

#### 1. JSON Serialization Testing
```scala
// test_serialization.sc
//> using dep com.lihaoyi::upickle:4.3.2
import upickle.default._

case class Test(name: String, value: String = "default")
object Test { implicit val rw: ReadWriter[Test] = macroRW }

val test = Test("hello")
val json = write(test)
println(s"JSON: $json") // Reveals if default values are omitted
```

#### 2. API Request Structure Validation
```scala
// validate_request.sc
//> using dep com.softwaremill.sttp.ai.openai::claude:SNAPSHOT
import sttp.ai.claude.models._

val request = MessageRequest.simple(...)
val requestJson = write(request)
println("Request structure:")
println(requestJson)
// Verify request matches API specification
```

#### 3. Library Behavior Investigation
```scala
// test_upickle_behavior.sc
//> using dep com.lihaoyi::upickle:4.3.2

// Test how AttributeTagged handles discriminators
object TestPickle extends upickle.AttributeTagged {
  override def tagName: String = "type"
}

// Test serialization behavior
```

### Best Practices

**Naming Conventions:**
- `debug_*.sc` - For debugging specific issues
- `test_*.sc` - For testing specific functionality
- `validate_*.sc` - For validation and verification

**Dependencies:**
- Always use explicit versions: `//> using dep group::artifact:version`
- Use SNAPSHOT versions when testing unreleased changes
- Add `//> using repository ivy2Local` for local artifacts

**Cleanup:**
- Remove scratch files after debugging is complete
- Never commit .sc files to the repository
- Document findings in code comments or issues

**Integration with Development Workflow:**

1. **Issue Identification**: Use scratch files to isolate and reproduce problems
2. **Hypothesis Testing**: Create focused tests for specific theories
3. **Solution Validation**: Verify fixes before running expensive integration tests
4. **Documentation**: Use findings to improve code comments and documentation

### Benefits

**Speed & Efficiency:**
- Compile and run in seconds vs. minutes for full sbt builds
- No need to wait for complete project compilation
- Immediate feedback on specific issues

**Cost-Effective:**
- Test JSON serialization locally before hitting paid APIs
- Avoid repeated failed integration test runs
- Debug without consuming API credits

**Isolation:**
- Focus on single issues without dependency complexity
- Test specific library behaviors in isolation
- Validate assumptions independently

### Example Debugging Session

During the Claude API implementation, scratch files helped identify the root cause:

1. **Problem**: `"messages.0.content.0.type: Field required"` error
2. **Hypothesis**: uPickle might be omitting default values
3. **Test**: Created `debug_defaults.sc` to test default value serialization
4. **Result**: Confirmed default values are omitted in JSON
5. **Solution**: Remove default value and use helper method
6. **Validation**: Created `test_tool_fixed.sc` to verify fix
7. **Integration**: Run full tests once fix was confirmed

This approach saved significant development time and API costs while providing precise problem identification and solution validation.

**Remember**: Scratch files are disposable debugging tools - use them freely for investigation, then clean them up once issues are resolved.

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
ALWAYS use Scala 3 syntax in docs, examples, and README (use `import package.*` instead of `import package._`, `@main` instead of `extends App`, etc.)
