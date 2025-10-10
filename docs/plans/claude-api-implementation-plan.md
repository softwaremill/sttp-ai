# Implementation Plan: Claude API Support for sttp-openai

## 1. Project Structure

### New Submodule Structure
```
claude/
├── src/
│   ├── main/scala/sttp/ai/claude/
│   │   ├── ClaudeClient.scala
│   │   ├── auth/
│   │   │   ├── ApiKeyAuth.scala
│   │   │   └── AuthConfig.scala
│   │   ├── requests/
│   │   │   ├── CompletionRequest.scala
│   │   │   ├── MessageRequest.scala
│   │   │   └── StreamingRequest.scala
│   │   ├── responses/
│   │   │   ├── CompletionResponse.scala
│   │   │   ├── MessageResponse.scala
│   │   │   ├── StreamingResponse.scala
│   │   │   └── ErrorResponse.scala
│   │   ├── models/
│   │   │   ├── ClaudeModel.scala
│   │   │   ├── Message.scala
│   │   │   └── Usage.scala
│   │   ├── streaming/
│   │   │   ├── ClaudeStreamProcessor.scala
│   │   │   └── StreamingSupport.scala
│   │   └── config/
│   │       ├── ClaudeConfig.scala
│   │       └── HttpConfig.scala
│   └── test/scala/sttp/ai/claude/
│       ├── ClaudeClientSpec.scala
│       ├── unit/
│       ├── integration/
│       └── fixtures/
├── build.sbt
└── README.md
```

### Build Configuration Changes
- Add new submodule to main `build.sbt` using projectMatrix
- Configure dependencies: sttp, circe for JSON, scala-test
- Set up cross-compilation with **Scala 3.x as primary target**, then Scala 2.13
- Configure publishing settings to match existing modules
- Add Claude streaming modules for each effect system (fs2, zio, akka, pekko, ox)

### Dependencies and Build Setup
```scala
// In build.sbt, add Claude module
lazy val claude = (projectMatrix in file("claude"))
  .jvmPlatform(
    scalaVersions = scala3 ++ scala2  // Scala 3 FIRST priority
  )
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Libraries.tapirApispecDocs,
      Libraries.uJsonCirce,
      Libraries.uPickle
    ) ++ Libraries.sttpApispec ++ Libraries.sttpClient ++ Seq(Libraries.scalaTest)
  )

// Add to aggregates
lazy val allAgregates = core.projectRefs ++
  claude.projectRefs ++  // Add Claude module
  fs2.projectRefs ++
  zio.projectRefs ++
  // ... existing modules

// Claude streaming modules
lazy val claudeFs2 = (projectMatrix in file("claude-streaming/fs2"))
  .jvmPlatform(scalaVersions = scala3 ++ scala2)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Libraries.sttpClientFs2)
  .dependsOn(claude % "compile->compile;test->test")

lazy val claudeOx = (projectMatrix in file("claude-streaming/ox"))
  .jvmPlatform(scalaVersions = scala3)  // Scala 3 only
  .settings(commonSettings)
  .settings(libraryDependencies ++= Libraries.sttpClientOx)
  .dependsOn(claude % "compile->compile;test->test")
```

## 2. API Implementation Requirements

### Claude API Endpoints to Implement
1. **Messages API** (`/v1/messages`)
    - POST for creating messages/completions
    - Streaming support via Server-Sent Events
2. **Models API** (`/v1/models`)
    - GET for listing available models
3. **Count Tokens** (if available in Claude API)

### Request/Response Models
```scala
case class MessageRequest(
  model: String,
  messages: List[Message],
  system: Option[String] = None,      // Claude-specific system messages
  maxTokens: Int,
  temperature: Option[Double] = None,
  topP: Option[Double] = None,
  topK: Option[Int] = None,
  stopSequences: Option[List[String]] = None,
  stream: Option[Boolean] = None,
  tools: Option[List[Tool]] = None    // Claude-specific tool structure
)

case class Message(
  role: String, // "user", "assistant" (no "system" - use system parameter)
  content: List[ContentBlock] // Claude uses content blocks, not simple strings
)

case class ContentBlock(
  `type`: String,    // "text" or "image"
  text: Option[String] = None,
  source: Option[ImageSource] = None
)

case class ImageSource(
  `type`: String,    // "base64"
  mediaType: String, // "image/jpeg", "image/png", etc.
  data: String       // base64 encoded image data
)

case class MessageResponse(
  id: String,
  `type`: String,
  role: String,
  content: List[ContentBlock],
  model: String,
  stopReason: Option[String],
  stopSequence: Option[String],
  usage: Usage
)

case class Tool(
  name: String,
  description: String,
  inputSchema: ToolInputSchema // Claude-specific tool schema
)

case class ToolInputSchema(
  `type`: String = "object",
  properties: Map[String, PropertySchema],
  required: Option[List[String]] = None
)
```

### Authentication Mechanism
- API Key based authentication via `x-api-key` header
- Required `anthropic-version` header (e.g., "2023-06-01")
- Support for environment variable configuration
- Optional explicit configuration in client initialization

### Error Handling
- Map Claude API error codes to typed error classes
- Handle rate limiting (429), authentication (401), validation (400) errors
- Provide detailed error messages with request context

## 3. Core Components to Implement

### ClaudeClient Implementation
```scala
trait ClaudeClient[F[_]] {
  def createMessage(request: MessageRequest): F[Either[ClaudeError, MessageResponse]]
  def createMessageStream(request: MessageRequest): F[Either[ClaudeError, Stream[F, MessageResponse]]]
  def listModels(): F[Either[ClaudeError, List[ClaudeModel]]]
}

class ClaudeClientImpl[F[_]: Async](
  config: ClaudeConfig,
  backend: SttpBackend[F, Any]
) extends ClaudeClient[F]
```

### Configuration Management
```scala
case class ClaudeConfig(
  apiKey: String,
  anthropicVersion: String = "2023-06-01", // Required header
  baseUrl: String = "https://api.anthropic.com",
  timeout: Duration = 60.seconds,
  maxRetries: Int = 3,
  organization: Option[String] = None
)

object ClaudeConfig {
  def fromEnv: ClaudeConfig = // Read from environment variables
  def apply(apiKey: String): ClaudeConfig = // Minimal config
  def apply(apiKey: String, anthropicVersion: String): ClaudeConfig = // With version
}
```

### Streaming Support
- Implement Server-Sent Events parsing for streaming responses
- Provide both callback-based and stream-based APIs
- Handle connection errors and reconnection logic
- Support cancellation of streaming requests

### Rate Limiting and Retry
- Implement exponential backoff for retries
- Respect rate limit headers from Claude API
- Configurable retry policies
- Circuit breaker pattern for API failures

## 4. Unit Testing Strategy

### Test Structure (Claude-specific focus)
```
claude/src/test/scala/sttp/ai/claude/
├── unit/
│   ├── ClaudeClientSpec.scala           # Claude-specific client tests
│   ├── requests/MessageRequestSpec.scala # Claude API structure tests
│   ├── responses/MessageResponseSpec.scala # Claude response parsing
│   ├── auth/ClaudeAuthSpec.scala        # Claude authentication (x-api-key + anthropic-version)
│   └── config/ClaudeConfigSpec.scala    # Claude configuration validation
├── integration/
│   ├── ClaudeApiIntegrationSpec.scala   # Real Claude API integration tests
│   └── StreamingIntegrationSpec.scala   # Claude streaming tests
├── fixtures/
│   ├── ClaudeResponses.scala            # Claude-specific response examples
│   └── TestData.scala                   # Claude test data
└── mocks/
    ├── MockClaudeBackend.scala          # Claude API mocks
    └── MockResponses.scala              # Claude mock responses
```

### Mock Strategy
- Create mock sttp backend for unit tests
- Provide canned responses for different scenarios
- Mock streaming responses using test data
- Simulate error conditions and edge cases

### Specific Test Cases (Claude API focused)
1. **Claude Authentication Tests**
    - Valid API key + anthropic-version header authentication
    - Invalid API key handling
    - Missing API key scenarios
    - Missing or invalid anthropic-version header

2. **Claude Request/Response Tests**
    - JSON serialization/deserialization for Claude API format
    - ContentBlock structure validation (text vs image)
    - System parameter vs messages structure
    - Claude-specific tool calling format
    - Required vs optional fields validation

3. **Claude Streaming Tests**
    - Claude-specific Server-Sent Events parsing
    - Connection interruption handling
    - Stream cancellation
    - Claude streaming response format validation

4. **Claude Error Handling Tests**
    - Claude-specific rate limiting responses
    - Claude API error code mapping
    - Network failure scenarios
    - Claude-specific validation errors

### Test Data Requirements
- Sample Claude API responses for all endpoints
- Error response examples
- Streaming response samples
- Edge case data (empty responses, malformed JSON)

## 5. Integration Testing Plan

### Test Environment Setup
- Configure test API keys via environment variables
- Set up rate limiting considerations for CI/CD
- Create test data cleanup procedures
- Mock external dependencies where necessary

### Test Scenarios
1. **Basic API Functionality**
    - Create simple messages
    - List available models
    - Handle authentication

2. **Streaming Tests**
    - Stream message responses
    - Handle stream interruptions
    - Test stream cancellation

3. **Error Scenarios**
    - Rate limit handling
    - Invalid requests
    - Network timeouts

### CI/CD Integration
- Add Claude module to existing CI pipeline
- Configure secrets for API keys
- Set up test reporting
- Include integration tests in PR validation
- Configure nightly extended test runs

## 6. Claude Code Optimization Instructions

### Code Organization Best Practices
- Follow existing sttp-openai patterns for consistency
- Use Scala 3 features where beneficial (enum, extension methods)
- Implement proper type safety with sealed traits for errors
- Use cats-effect or similar for async operations

### Documentation Requirements
- Scaladoc for all public APIs
- README with usage examples
- Migration guide from other Claude clients
- Configuration reference documentation

### Code Review Checkpoints
1. **Architecture Review**: Client interface design, error handling approach
2. **Implementation Review**: Request/response models, JSON codecs
3. **Testing Review**: Test coverage, integration test scenarios
4. **Documentation Review**: API docs, usage examples
5. **Performance Review**: Memory usage, connection pooling

### Performance Considerations
- Implement connection pooling for HTTP client
- Optimize JSON parsing for large responses
- Implement proper backpressure for streaming
- Monitor memory usage with large context windows

## 7. Implementation Phases

### Phase 1: Core Foundation (Week 1-2)
**Deliverables:**
- Project structure and build configuration with Scala 3 priority
- Basic ClaudeClient interface and implementation
- Authentication with both x-api-key and anthropic-version headers
- Accurate Claude API request/response models (ContentBlock, system parameter, tools)
- Claude-specific configuration management

**Dependencies:** None
**Testing:** Unit tests for Claude-specific models and configuration

### Phase 2: Basic API Implementation (Week 3-4)
**Deliverables:**
- Claude Messages API implementation with proper ContentBlock support
- Claude Models API implementation
- Claude-specific error handling and response parsing
- Image content support via ContentBlock structure
- Basic integration tests with real Claude API

**Dependencies:** Phase 1 complete
**Testing:** Integration tests with Claude API using minimal cost requests

### Phase 3: Streaming Support (Week 5-6)
**Deliverables:**
- Claude-specific Server-Sent Events parsing for streaming responses
- Claude streaming message responses with ContentBlock support
- Connection management and error handling for Claude API
- Claude streaming modules for fs2, zio, akka, pekko, ox
- Streaming integration tests with Claude API

**Dependencies:** Phase 2 complete
**Testing:** Claude streaming-specific test scenarios

### Phase 4: Implementation Completion and Cleanup ✅ COMPLETED
**Deliverables:**
- ✅ Core Claude API implementation finalized with complete feature set
- ✅ Clean, focused codebase without premature optimizations
- ✅ All Phase 1-3 features fully integrated and tested
- ✅ Production-ready Claude client with essential functionality
- ✅ Comprehensive error handling for all Claude API scenarios
- ✅ Tool calling support through existing ContentBlock and Tool models
- ✅ Code cleanup removing unused advanced utilities (rate limiting, circuit breakers)

**Implementation Decision:**
Advanced features like rate limiting, circuit breakers, and performance optimizations were initially implemented but subsequently removed to maintain a clean, focused codebase. These features will be added later when specifically requested by users, following the principle of adding complexity only when needed.

**Current Status:** COMPLETE - The Claude API implementation is production-ready with all essential features.

**Dependencies:** Phase 3 complete
**Testing:** ✅ Core functionality tested, integration tests passing

### Phase 5: Documentation and Polish ✅ COMPLETED
**Deliverables:**
- ✅ Complete Claude API documentation with examples
- ✅ Claude-specific usage examples and guides
- ✅ Performance benchmarks against Claude API
- ✅ Final integration testing with Claude API
- ✅ Claude Code formatting compliance (mandatory `sbt scalafmt`)

**Implementation Issue Found and Resolved:**
During Phase 5 completion, a critical JSON serialization bug was discovered in the ClaudeBasicExample execution:

**Bug Report:**
- Error: `"messages.0.content.0.type: Field required"`
- Root Cause: Claude module was using `upickle.default._` instead of `SnakePickle` for JSON serialization
- Impact: ContentBlock discriminated union types missing required `type` field for Claude API

**Resolution Applied:**
- Created `claude/src/main/scala/sttp/ai/claude/json/SnakePickle.scala` (copied from core module)
- Updated all Claude source and test files to use `SnakePickle` instead of `upickle.default._`
- Ensured proper discriminator field (`type`) serialization for sealed traits
- Files modified: 10 total (ClaudeClient.scala, all model files, all request/response files, test files)

**Result:** Claude API calls now properly serialize ContentBlock objects with required `type` fields, fixing the API validation errors.

**Dependencies:** Phase 4 complete
**Testing:** ✅ Documentation testing, Claude example validation, JSON serialization fix verification

## 8. Migration and Compatibility

### Compatibility with Existing OpenAI Implementation
- Maintain separate package namespaces (`sttp.ai.openai` vs `sttp.ai.claude`)
- Share common utilities where beneficial (HTTP client configuration, error handling patterns)
- Ensure no dependency conflicts between modules
- Allow users to include both modules simultaneously

### Shared Utilities and Common Interfaces
```scala
// Shared in common module
trait AiClient[F[_], Request, Response] {
  def sendRequest(request: Request): F[Either[AiError, Response]]
}

trait StreamingSupport[F[_], Response] {
  def streamResponse: Stream[F, Response]
}

// Common error hierarchy
sealed trait AiError
case class AuthenticationError(message: String) extends AiError
case class RateLimitError(retryAfter: Option[Duration]) extends AiError
case class ValidationError(details: List[String]) extends AiError
```

### Version Compatibility Requirements
- Support Scala 2.13.x and 3.x
- Maintain compatibility with sttp 3.x
- Follow semantic versioning for releases
- Provide migration guides for breaking changes

### Integration Points
- Common configuration patterns
- Shared HTTP client configuration
- Unified error handling where appropriate
- Consistent streaming interfaces
- Common testing utilities and fixtures
- 