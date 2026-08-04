# Timeouts and retries

All three client configs — `OpenAIConfig`, `ClaudeConfig`, `GeminiConfig` — carry two resilience settings:

```scala
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.config.OpenAIConfig
import scala.concurrent.duration.*

val config = OpenAIConfig(
  apiKey = "your-api-key",
  timeout = 2.minutes, // default: 10 minutes
  maxRetries = 5 // default: 3
)
val client = OpenAISyncClient(config)
```

## Timeout

`timeout` is applied to every request the clients build (as sttp's per-request `readTimeout`), including streaming
requests. It defaults to 10 minutes, matching the official OpenAI and Anthropic SDKs.

The exact semantics depend on the sttp backend: on the default Java HttpClient backends it is the time until response
headers arrive — so long-lived SSE streams are not cut off mid-stream — while some backends (e.g. OkHttp) treat it as
the maximum time between bytes. Set `timeout = Duration.Inf` to disable the timeout entirely.

## Retries

Retries are implemented as an sttp **backend wrapper**, `sttp.ai.core.http.RetryingBackend`, in the style of sttp's
`FollowRedirectsBackend`. The **sync clients** (`OpenAISyncClient`, `ClaudeSyncClient`, `GeminiSyncClient`) wrap their
backend with it automatically to honor `maxRetries`: `maxRetries = 3` means up to 3 retries after the initial attempt
(4 attempts total); `0` disables retrying.

Only failures that are safe or explicitly sanctioned to retry are retried — the same set the official OpenAI and
Anthropic SDKs use:

| Failure | Retried? | Why |
|---|---|---|
| Connection error (request never sent) | yes | The server never saw the request, so a retry cannot duplicate work. |
| HTTP 408, 409, 429, 5xx | yes | The status explicitly signals a transient condition. |
| Read timeout / read error | no | The server may have processed the request; retrying could double-bill. |
| Any other 4xx (400, 401, 404, …) | no | The request itself is at fault; retrying cannot help. |
| Requests with a streaming (`InputStream`) body | no | The body cannot be re-sent. |

Backoff between attempts is exponential — 500ms, 1s, 2s, 4s, capped at 8s. If a retried response carries a
`Retry-After` header in delta-seconds form (typical for 429) within 0–30 seconds, that delay is used instead; a
`Retry-After` outside that window falls back to the exponential backoff rather than retrying earlier than the server
instructed. After the last retry, the final error surfaces unchanged.

### Retries with async clients and agent loops

The **async clients** (`OpenAI`, `ClaudeClient`, `GeminiClient`) return raw sttp requests that you send yourself, and
the agent loops send through the `Backend` you pass them — so `maxRetries` from the config cannot apply there
automatically. Wrap the backend you use in `RetryingBackend` instead: it is generic in the effect, and sleeping
between attempts is delegated to a function you supply from your effect system:

```scala
import sttp.ai.core.http.RetryingBackend

// cats-effect
val backend: Backend[IO] = RetryingBackend(httpClientCatsBackend, maxRetries = 3, IO.sleep)

// ZIO
val zioBackend: Backend[Task] = RetryingBackend(httpClientZioBackend, maxRetries = 3, d => ZIO.sleep(zio.Duration.fromScala(d)))
```

One caveat: response bodies that are effectful streams cannot be replayed once the body handler has run. A retried
response's body is closed when it is `AutoCloseable` (e.g. an `InputStream`), and error-status responses — the only
ones retried — are read eagerly by this library's response handlers, so the library's own requests are always safe;
only be careful when sending your own requests with `...Always`-style streaming response handlers through the
wrapper.
