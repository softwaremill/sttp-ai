# Refactoring Plan: Reduce OpenAI/Claude Duplication

**Status**: Draft
**Created**: 2025-10-10
**Analysis Source**: code-duplication-finder agent

## Executive Summary

This document outlines a refactoring plan to reduce code duplication between the `openai/` and `claude/` modules while preserving API-specific behaviors. Analysis reveals 20-30% potential code reduction through shared abstractions.

### Key Findings
- **Structural duplication**: Client implementations, exception hierarchies, request/response handling
- **Function-level duplication**: Error mapping, streaming methods, JSON serialization
- **Block-level duplication**: Authentication headers, configuration, SnakePickle utilities

### Proposed Solution
Create a new `core/` module (or repurpose existing) with shared abstractions using Scala 3 features (type classes, extension methods) to maintain flexibility while reducing boilerplate.

---

## Duplication Analysis Details

### 1. Structural Duplications (High Priority)

#### 1.1 Client Structure

**Current State:**

Both `OpenAI` and `ClaudeClient` follow nearly identical patterns:

```scala
// openai/src/main/scala/sttp/ai/openai/OpenAI.scala
class OpenAI(authToken: String, baseUrl: String = "https://api.openai.com/v1") {
  def createChatCompletion(request: ChatCompletionRequest): Request[Either[OpenAIException, ChatCompletionResponse]] = {
    authRequest
      .post(uri"$baseUrl/chat/completions")
      .body(write(request))
      .response(asJson_parseErrors[ChatCompletionResponse])
  }

  def listModels(): Request[Either[OpenAIException, ListModelsResponse]] = {
    authRequest
      .get(uri"$baseUrl/models")
      .response(asJson_parseErrors[ListModelsResponse])
  }

  // Streaming methods...
  // Input stream methods...
}

// claude/src/main/scala/sttp/ai/claude/ClaudeClient.scala
class ClaudeClient(config: ClaudeClientConfig) {
  def createMessage(request: MessageRequest): Request[Either[ClaudeException, MessageResponse]] = {
    authRequest
      .post(uri"${config.baseUrl}/messages")
      .body(write(request))
      .response(asJson_parseErrors[MessageResponse])
  }

  def listModels(): Request[Either[ClaudeException, ListModelsResponse]] = {
    authRequest
      .get(uri"${config.baseUrl}/models")
      .response(asJson_parseErrors[ListModelsResponse])
  }

  // Streaming methods...
  // Input stream methods...
}
```

**Issues:**
- Identical method structure (create request, list models, streaming)
- Duplicated `asJson_parseErrors` implementation
- Similar error handling patterns
- Configuration patterns differ only in authentication details

#### 1.2 JSON Parsing and Error Handling

**Current State:**

Both modules have nearly identical JSON parsing with error mapping:

```scala
// Pattern repeated in both OpenAI and ClaudeClient
private def asJson_parseErrors[A: Reader]: ResponseAs[Either[CustomException, A], Any] = {
  asJsonEither(implicitly[Reader[ErrorResponse]], implicitly[Reader[A]])
    .mapLeft(mapErrorToException)
    .mapLeft {
      case HttpError(body, statusCode) =>
        read[ErrorResponse](body) match {
          case errorResponse => mapToCustomException(errorResponse)
        }
      case DeserializationException(body, error) =>
        new DeserializationException(...)
    }
}
```

**Issues:**
- Error mapping logic duplicated
- Exception hierarchy mapping duplicated
- Only difference is target exception type

#### 1.3 Exception Hierarchies

**Current State:**

```scala
// openai/src/main/scala/sttp/ai/openai/OpenAIException.scala
sealed abstract class OpenAIException(message: String, cause: Throwable) extends Exception(message, cause)
case class AuthenticationException(...) extends OpenAIException(...)
case class RateLimitException(...) extends OpenAIException(...)
case class InvalidRequestException(...) extends OpenAIException(...)
// ... more subtypes

// claude/src/main/scala/sttp/ai/claude/ClaudeException.scala
sealed abstract class ClaudeException(message: String, cause: Throwable) extends Exception(message, cause)
case class AuthenticationException(...) extends ClaudeException(...)
case class RateLimitException(...) extends ClaudeException(...)
case class InvalidRequestException(...) extends ClaudeException(...)
// ... more subtypes
```

**Issues:**
- Nearly identical exception hierarchies
- Same error categories (auth, rate limit, invalid request)
- Only difference is base exception type

---

### 2. Function-Level Duplications (Medium Priority)

#### 2.1 Error Mapping

**Current State:**

```scala
// Similar in both modules
private def mapErrorToException(errorResponse: ErrorResponse): CustomException = {
  errorResponse.error.`type` match {
    case "authentication_error" | "invalid_api_key" =>
      AuthenticationException(errorResponse)
    case "rate_limit_error" =>
      RateLimitException(errorResponse)
    case "invalid_request_error" =>
      InvalidRequestException(errorResponse)
    // ... more cases
  }
}
```

**Issues:**
- Logic is identical, only return type differs
- Error type strings are similar across APIs

#### 2.2 Streaming Request Methods

**Current State:**

```scala
// OpenAI
def createChatCompletionAsBinaryStream[S](
  streams: Streams[S],
  request: ChatCompletionRequest
): StreamRequest[Either[OpenAIException, S#BinaryStream], S] = {
  authRequest
    .post(uri"$baseUrl/chat/completions")
    .body(write(request.copy(stream = Some(true))))
    .response(asBinaryStream(streams))
}

// Claude
def createMessageAsBinaryStream[S](
  streams: Streams[S],
  request: MessageRequest
): StreamRequest[Either[ClaudeException, S#BinaryStream], S] = {
  authRequest
    .post(uri"${config.baseUrl}/messages")
    .body(write(request.copy(stream = Some(true))))
    .response(asBinaryStream(streams))
}
```

**Issues:**
- Identical pattern for enabling streaming
- Same binary stream handling
- Only differences: endpoint URL and request type

#### 2.3 SnakePickle JSON Handling

**Current State:**

Both modules have `json/SnakePickle.scala` with identical content:

```scala
// openai/src/main/scala/sttp/ai/openai/json/SnakePickle.scala
object SnakePickle extends AttributeTagged {
  override def camelizeKeys: Boolean = false
  override def snakeKeys: Boolean = true
}

// claude/src/main/scala/sttp/ai/claude/json/SnakePickle.scala
object SnakePickle extends AttributeTagged {
  override def camelizeKeys: Boolean = false
  override def snakeKeys: Boolean = true
}
```

**Issues:**
- Completely duplicated code
- No API-specific behavior

---

### 3. Block-Level Duplications (Low-Medium Priority)

#### 3.1 Authentication Headers

**Current State:**

```scala
// OpenAI
private val authRequest = basicRequest
  .header("Authorization", s"Bearer $authToken")
  .header("content-type", "application/json")

// Claude
private val authRequest = basicRequest
  .header("x-api-key", config.apiKey)
  .header("anthropic-version", config.apiVersion)
  .header("content-type", "application/json")
```

**Issues:**
- Similar pattern, different headers
- **Note**: This is intentional duplication due to API differences

#### 3.2 Configuration Patterns

**Current State:**

```scala
// OpenAI
class OpenAI(authToken: String, baseUrl: String = "https://api.openai.com/v1")

// Claude
case class ClaudeClientConfig(
  apiKey: String,
  baseUrl: String = "https://api.anthropic.com/v1",
  apiVersion: String = "2023-06-01"
)
class ClaudeClient(config: ClaudeClientConfig)
```

**Issues:**
- Different configuration approaches (constructor params vs config object)
- Could be unified with a common config trait

---

## Proposed Solution Architecture

### Phase 1: Create Shared Core Module (High Priority)

#### 1.1 Generic AI Client Trait

```scala
// core/src/main/scala/sttp/ai/core/AIClient.scala
trait AIClient[Req, Resp, Err <: Exception] {
  def baseUrl: String

  def createRequest(request: Req): Request[Either[Err, Resp]]

  def listModels(): Request[Either[Err, ModelsResponse]]

  def createStreamingRequest[S](
    streams: Streams[S],
    request: Req
  ): StreamRequest[Either[Err, S#BinaryStream], S]

  def createInputStreamRequest(request: Req): Request[Either[Err, InputStream]]
}
```

#### 1.2 Shared JSON Support

```scala
// core/src/main/scala/sttp/ai/core/json/SnakePickle.scala
object SnakePickle extends AttributeTagged {
  override def camelizeKeys: Boolean = false
  override def snakeKeys: Boolean = true
}
```

**Migration:**
- Move SnakePickle to core
- Update imports in openai/ and claude/
- Remove duplicated files

#### 1.3 Generic Error Handling

```scala
// core/src/main/scala/sttp/ai/core/error/AIErrorMapper.scala
trait AIErrorMapper[Err <: Exception] {
  def mapError(errorResponse: ErrorResponse): Err

  def mapHttpError(body: String, statusCode: StatusCode): Err

  def mapDeserializationError(body: String, error: ujson.ParseException): Err
}

// core/src/main/scala/sttp/ai/core/error/AIException.scala
sealed abstract class AIException(message: String, cause: Throwable)
  extends Exception(message, cause)

trait AIExceptionTypes[E <: AIException] {
  def authenticationError(msg: String, cause: Throwable = null): E
  def rateLimitError(msg: String, cause: Throwable = null): E
  def invalidRequestError(msg: String, cause: Throwable = null): E
  // ... more error types
}
```

#### 1.4 Generic JSON Response Parsing

```scala
// core/src/main/scala/sttp/ai/core/http/ResponseHandlers.scala
trait ResponseHandlers[Err <: Exception] {
  def errorMapper: AIErrorMapper[Err]

  def asJson_parseErrors[A: Reader]: ResponseAs[Either[Err, A], Any] = {
    asJsonEither(implicitly[Reader[ErrorResponse]], implicitly[Reader[A]])
      .mapLeft(errorMapper.mapHttpError)
      .mapLeft {
        case HttpError(body, statusCode) =>
          read[ErrorResponse](body) match {
            case errorResponse => errorMapper.mapError(errorResponse)
          }
        case DeserializationException(body, error) =>
          errorMapper.mapDeserializationError(body, error)
      }
  }
}
```

---

### Phase 2: Refactor OpenAI Module (High Priority)

#### 2.1 Update OpenAI Client

```scala
// openai/src/main/scala/sttp/ai/openai/OpenAI.scala
import sttp.ai.core.AIClient
import sttp.ai.core.http.ResponseHandlers
import sttp.ai.core.json.SnakePickle._  // Shared SnakePickle

class OpenAI(authToken: String, val baseUrl: String = "https://api.openai.com/v1")
  extends AIClient[ChatCompletionRequest, ChatCompletionResponse, OpenAIException]
  with ResponseHandlers[OpenAIException] {

  override val errorMapper: AIErrorMapper[OpenAIException] = OpenAIErrorMapper

  private val authRequest = basicRequest
    .header("Authorization", s"Bearer $authToken")
    .header("content-type", "application/json")

  override def createRequest(request: ChatCompletionRequest): Request[Either[OpenAIException, ChatCompletionResponse]] = {
    authRequest
      .post(uri"$baseUrl/chat/completions")
      .body(write(request))
      .response(asJson_parseErrors[ChatCompletionResponse])
  }

  // Other methods use shared implementations from traits...
}

// openai/src/main/scala/sttp/ai/openai/error/OpenAIErrorMapper.scala
object OpenAIErrorMapper extends AIErrorMapper[OpenAIException] {
  override def mapError(errorResponse: ErrorResponse): OpenAIException = {
    errorResponse.error.`type` match {
      case "authentication_error" | "invalid_api_key" =>
        OpenAIAuthenticationException(errorResponse)
      case "rate_limit_error" =>
        OpenAIRateLimitException(errorResponse)
      // ... more cases
    }
  }

  // Implement other methods...
}
```

#### 2.2 Update Exception Hierarchy

```scala
// openai/src/main/scala/sttp/ai/openai/OpenAIException.scala
import sttp.ai.core.error.AIException

sealed abstract class OpenAIException(message: String, cause: Throwable)
  extends AIException(message, cause)

case class OpenAIAuthenticationException(...) extends OpenAIException(...)
case class OpenAIRateLimitException(...) extends OpenAIException(...)
// ... other subtypes
```

---

### Phase 3: Refactor Claude Module (High Priority)

Apply the same patterns as OpenAI refactoring:

```scala
// claude/src/main/scala/sttp/ai/claude/ClaudeClient.scala
import sttp.ai.core.AIClient
import sttp.ai.core.http.ResponseHandlers
import sttp.ai.core.json.SnakePickle._

class ClaudeClient(config: ClaudeClientConfig)
  extends AIClient[MessageRequest, MessageResponse, ClaudeException]
  with ResponseHandlers[ClaudeException] {

  override val baseUrl: String = config.baseUrl
  override val errorMapper: AIErrorMapper[ClaudeException] = ClaudeErrorMapper

  private val authRequest = basicRequest
    .header("x-api-key", config.apiKey)
    .header("anthropic-version", config.apiVersion)
    .header("content-type", "application/json")

  override def createRequest(request: MessageRequest): Request[Either[ClaudeException, MessageResponse]] = {
    authRequest
      .post(uri"$baseUrl/messages")
      .body(write(request))
      .response(asJson_parseErrors[MessageResponse])
  }

  // Other methods...
}
```

---

### Phase 4: Streaming Abstractions (Medium Priority)

#### 4.1 Generic Streaming Support

```scala
// core/src/main/scala/sttp/ai/core/streaming/StreamingSupport.scala
trait StreamingSupport[Req, Err <: Exception] {
  def baseUrl: String
  def authRequest: RequestT[Empty, Either[String, String], Any]

  def createBinaryStream[S](
    streams: Streams[S],
    request: Req,
    endpoint: String
  )(using enableStream: Req => Req): StreamRequest[Either[Err, S#BinaryStream], S] = {
    authRequest
      .post(uri"$baseUrl$endpoint")
      .body(write(enableStream(request)))
      .response(asBinaryStream(streams))
  }

  def createInputStream(
    request: Req,
    endpoint: String
  )(using enableStream: Req => Req): Request[Either[Err, InputStream]] = {
    authRequest
      .post(uri"$baseUrl$endpoint")
      .body(write(enableStream(request)))
      .response(asBinaryStream.mapRight(_.asInputStream))
  }
}
```

#### 4.2 API-Specific Implementations

```scala
// openai/src/main/scala/sttp/ai/openai/OpenAI.scala
class OpenAI(...)
  extends AIClient[...]
  with StreamingSupport[ChatCompletionRequest, OpenAIException] {

  given Conversion[ChatCompletionRequest, ChatCompletionRequest] =
    _.copy(stream = Some(true))

  def createChatCompletionAsBinaryStream[S](
    streams: Streams[S],
    request: ChatCompletionRequest
  ): StreamRequest[Either[OpenAIException, S#BinaryStream], S] = {
    createBinaryStream(streams, request, "/chat/completions")
  }
}
```

---

### Phase 5: Configuration Unification (Low Priority)

```scala
// core/src/main/scala/sttp/ai/core/config/AIClientConfig.scala
trait AIClientConfig {
  def apiKey: String
  def baseUrl: String
  def authHeaders: Map[String, String]
}

// openai/src/main/scala/sttp/ai/openai/OpenAIConfig.scala
case class OpenAIConfig(
  apiKey: String,
  baseUrl: String = "https://api.openai.com/v1"
) extends AIClientConfig {
  override def authHeaders: Map[String, String] = Map(
    "Authorization" -> s"Bearer $apiKey",
    "content-type" -> "application/json"
  )
}

// claude/src/main/scala/sttp/ai/claude/ClaudeClientConfig.scala
case class ClaudeClientConfig(
  apiKey: String,
  baseUrl: String = "https://api.anthropic.com/v1",
  apiVersion: String = "2023-06-01"
) extends AIClientConfig {
  override def authHeaders: Map[String, String] = Map(
    "x-api-key" -> apiKey,
    "anthropic-version" -> apiVersion,
    "content-type" -> "application/json"
  )
}
```

---

## Implementation Roadmap

### Phase 1: Foundation (High Priority)
**Estimated Effort**: 1-2 weeks

1. ✅ Create `core/` module structure
2. ✅ Implement `AIClient` trait
3. ✅ Move `SnakePickle` to core
4. ✅ Implement `AIErrorMapper` trait
5. ✅ Implement `ResponseHandlers` trait
6. ✅ Update build.sbt dependencies

**Deliverables:**
- Working core module with shared abstractions
- Comprehensive unit tests for core utilities
- Updated documentation

### Phase 2: OpenAI Refactoring (High Priority)
**Estimated Effort**: 1 week

1. ✅ Update OpenAI client to extend core traits
2. ✅ Implement OpenAI-specific error mapper
3. ✅ Update exception hierarchy
4. ✅ Update imports to use core SnakePickle
5. ✅ Run full test suite
6. ✅ Run integration tests

**Deliverables:**
- Refactored OpenAI module
- All tests passing
- No breaking API changes

### Phase 3: Claude Refactoring (High Priority)
**Estimated Effort**: 1 week

1. ✅ Update Claude client to extend core traits
2. ✅ Implement Claude-specific error mapper
3. ✅ Update exception hierarchy
4. ✅ Update imports to use core SnakePickle
5. ✅ Run full test suite
6. ✅ Run integration tests

**Deliverables:**
- Refactored Claude module
- All tests passing
- No breaking API changes

### Phase 4: Streaming Abstractions (Medium Priority)
**Estimated Effort**: 3-5 days

1. ✅ Implement generic streaming support in core
2. ✅ Refactor OpenAI streaming methods
3. ✅ Refactor Claude streaming methods
4. ✅ Update streaming module extensions (fs2, zio, akka, pekko, ox)
5. ✅ Run streaming tests

**Deliverables:**
- Unified streaming abstractions
- All streaming tests passing

### Phase 5: Configuration Unification (Low Priority)
**Estimated Effort**: 2-3 days

1. ✅ Create `AIClientConfig` trait
2. ✅ Update OpenAI config
3. ✅ Update Claude config
4. ✅ Update client constructors
5. ✅ Deprecate old constructors if breaking changes

**Deliverables:**
- Unified configuration approach
- Migration guide for users

---

## Testing Strategy

### Unit Tests
- Test all core abstractions independently
- Test error mapping for both APIs
- Test JSON serialization/deserialization
- Test streaming helpers

### Integration Tests
- Run existing OpenAI integration tests
- Run existing Claude integration tests
- Verify no regression in API behavior
- Test streaming with real API calls

### Compatibility Tests
- Verify Scala 2.13 compatibility
- Verify Scala 3 compatibility
- Test all effect system integrations (fs2, zio, akka, pekko, ox)

---

## Migration Guide for Users

### Breaking Changes
**None expected** - refactoring is internal only

### Deprecations
If configuration unification introduces breaking changes:

```scala
// Old way (deprecated)
val openai = new OpenAI("api-key")

// New way (recommended)
val config = OpenAIConfig(apiKey = "api-key")
val openai = new OpenAI(config)
```

Provide 2-3 release cycles before removing deprecated constructors.

---

## Risks and Mitigation

### Risk 1: Breaking API Changes
**Mitigation**:
- Maintain backward compatibility
- Use deprecation warnings
- Comprehensive testing

### Risk 2: Performance Regression
**Mitigation**:
- Benchmark critical paths
- Ensure trait methods are inlined
- Monitor compilation times

### Risk 3: Increased Complexity
**Mitigation**:
- Clear documentation
- Simple trait hierarchies
- Avoid over-abstraction

### Risk 4: Scala 2/3 Compatibility Issues
**Mitigation**:
- Test both versions continuously
- Avoid Scala 3-only features in core (or use conditional compilation)
- Use cross-building from start

---

## Success Metrics

1. **Code Reduction**: 20-30% reduction in duplicated code
2. **Test Coverage**: Maintain or improve current coverage (target: >80%)
3. **Build Times**: No significant increase (<5%)
4. **API Compatibility**: Zero breaking changes in public API
5. **Integration Tests**: 100% passing for both OpenAI and Claude

---

## Intentional Duplications to Preserve

The following duplications are **intentional** and should **NOT** be refactored:

1. **Authentication headers**: Different between OpenAI (Bearer token) and Claude (x-api-key)
2. **Message structures**: OpenAI uses simple strings, Claude uses ContentBlock arrays
3. **API-specific request/response models**: Each API has unique fields and requirements
4. **Endpoint URLs**: Different base URLs and paths
5. **API versions**: Claude requires version header, OpenAI doesn't

These differences reflect fundamental API design choices and should remain API-specific.

---

## Open Questions

1. Should we create a new `core` module or repurpose existing `openai` module as the core?
   - **Recommendation**: Create new `core` module for clarity

2. Should configuration unification be part of this refactoring or a separate effort?
   - **Recommendation**: Separate effort (Phase 5, low priority)

3. How to handle future APIs (e.g., Gemini, Mistral)?
   - **Recommendation**: Core abstractions should accommodate new APIs easily

---

## Conclusion

This refactoring will significantly reduce code duplication while preserving API-specific behaviors. The phased approach allows for incremental progress with continuous validation through tests.

**Next Steps:**
1. Review and approve this plan
2. Create GitHub issue/project for tracking
3. Begin Phase 1 implementation
4. Regular check-ins to assess progress and adjust plan as needed
