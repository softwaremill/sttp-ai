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

`maxRetries` is honored by the **sync clients** (`OpenAISyncClient`, `ClaudeSyncClient`, `GeminiSyncClient`), which
own the HTTP send. `maxRetries = 3` means up to 3 retries after the initial attempt (4 attempts total); `0` disables
retrying.

Only failures that are safe or explicitly sanctioned to retry are retried:

| Failure | Retried? | Why |
|---|---|---|
| Connection error (request never sent) | yes | The server never saw the request, so a retry cannot duplicate work. |
| HTTP 408, 429, 5xx | yes | The status explicitly signals a transient condition. |
| Read timeout / read error | no | The server may have processed the request; retrying could double-bill. |
| Any other 4xx (400, 401, 404, …) | no | The request itself is at fault; retrying cannot help. |

Backoff between attempts is exponential — 500ms, 1s, 2s, 4s, capped at 8s. If a retried response carries a
`Retry-After` header in delta-seconds form (typical for 429), that delay is used instead, capped at 30 seconds. After
the last retry, the final error surfaces unchanged.

The **async clients** (`OpenAI`, `ClaudeClient`, `GeminiClient`) return raw sttp requests that you send yourself, so
`maxRetries` cannot apply there. Use your effect system's retry tooling instead — for example cats-retry, ZIO
`Schedule`, or an sttp backend wrapper.
