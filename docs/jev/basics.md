# Jev API

[Jev](https://docs.typesafe.ai) is TypeSafe AI's model. It does not generate text. You send it a `state` (text or JSON) and a set of typed questions, and get back one typed answer per question. A **noul** is a yes/no question, answered with the probability of "yes".

## Jev Features

- **Typed answers** — ask a tuple of questions, get a tuple of answers, each with its own type
- **Enum choices** — `Choice.of[E]` turns an enum's cases into options and decodes the answer back to a case
- **Structured entries** — the state, instructions, options and levels can be text or JSON
- **Data-driven lists** — `askAll` asks a `Seq` of questions built at runtime
- **Gateways** — any server that follows the TypeSafe OpenAPI spec, e.g. OpenRouter or Vercel AI Gateway, via `baseUrl` and `model`
- **Sync and async clients** — `JevSyncClient` blocks and throws; `JevClient` returns sttp requests to send with any backend
- **Scala 3 only** — JVM and Scala Native

## Basic Usage (Jev)

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::jev:@VERSION@

import sttp.ai.jev.*

enum Team:
  case Billing, Technical, Sales

object Main:
  def main(args: Array[String]): Unit =
    // Reads the TYPESAFE_API_KEY environment variable
    val client = JevSyncClient.fromEnv
    try
      val ticket = "I've been trying to connect my Stripe account for 3 days and it keeps failing. Please help ASAP."

      // Throws a JevException subclass on error
      val response = client.ask(
        ticket,
        (
          Choice.of[Team]("Which team should handle this ticket"),
          Score("How frustrated the customer is", "Calm", "Frustrated but civil", "Very angry"),
          Noul("The customer needs a reply today")
        )
      )
      val (team, frustration, urgent) = response.answers

      println(team.choice)            // a Team
      println(frustration.mostLikely) // level index: 0, 1 or 2
      println(urgent.probability)     // 0 to 1
      println(s"Usage: ${response.usage}")
    finally client.close()
```

The answer types follow the question types, position by position: `ChoiceAnswer[Team]`, `ScoreAnswer`, `NoulAnswer`.

A single question is answered with a single answer:

```scala mdoc:compile-only
import sttp.ai.jev.*

def isSpam(client: JevSyncClient, email: String): Double =
  client.ask(email, Noul("This email is spam")).answers.probability
```

## Questions

- `Noul(instructions, whenTrue, whenFalse)` — probability that `instructions` hold. `whenTrue` / `whenFalse` optionally describe what a yes and a no look like. Answer: `NoulAnswer(probability)`.
- `Choice` — picks one option. Answer: `ChoiceAnswer(choice, confidence, probabilities)`; `confidence` (0 to 1) is the server's certainty in the answer. Build it with:
  - `Choice.described(instructions, "name" -> "description", ...)` — named, described options
  - `Choice.strings(instructions, names)` — bare names
  - `Choice.of[E](instructions, describe)` — every case of an enum (cases without parameters only)
  - `Choice.from(instructions, values)(name, describe)` — any values, named by a function
- `Score(instructions, level0, level1, ...)` — position on levels ordered low to high. Answer: `ScoreAnswer(score, confidence, probabilities)`; `score` is the expected level index, `mostLikely` the most probable one.

Every text argument (state, instructions, descriptions, levels) is an `Entry`: a `String` or a circe `Json` object or array:

```scala mdoc:compile-only
import io.circe.Json
import sttp.ai.jev.*

val priority = Choice.described(
  "How to prioritize this ticket",
  "p1" -> Json.obj("meaning" -> Json.fromString("Service down"), "examples" -> Json.arr(Json.fromString("site returns 500"))),
  "p2" -> "Degraded, workaround exists",
  "p3" -> "Question or cosmetic issue"
)
```

## Lists of questions

When the questions are only known at runtime, use `askAll`. Answers come back in the same order. A mixed list is a `Seq[Question[Answer]]`, so the answers need a pattern match:

```scala mdoc:compile-only
import sttp.ai.jev.*

def check(client: JevSyncClient, text: String, rules: Seq[String]): Unit =
  val questions: Seq[Question[Answer]] = Score("Tone", "Friendly", "Neutral", "Hostile") +: rules.map(Noul(_))
  client.askAll(text, questions).answers.foreach {
    case NoulAnswer(p)      => println(s"rule holds: $p")
    case s: ScoreAnswer     => println(s"tone level: ${s.mostLikely}")
    case c: ChoiceAnswer[?] => println(c.choice)
  }
```

A list of one question type (e.g. `Seq[Noul]`) is answered with that answer type (`Seq[NoulAnswer]`).

Notes:
- Generic helpers should take a `Question[A]` and return `A`; with `Question[?]` the answer type is lost.
- Server error messages name questions by their 0-based position in the request, e.g. `questions.2.score.criteria`.

## Async Usage

`JevClient` methods return a plain sttp `Request` whose body is an `Either[JevException, SystemOneResponse[_]]`. Nothing is thrown; send it with any sttp backend:

```scala mdoc:compile-only
import sttp.ai.jev.*
import sttp.client4.*

object AsyncMain:
  def main(args: Array[String]): Unit =
    val backend: SyncBackend = DefaultSyncBackend()
    val client = JevClient.fromEnv

    val request = client.ask("The package arrived broken.", (Noul("The customer wants a refund"), Choice.strings("Product", Seq("tv", "phone"))))

    request.send(backend).body match
      case Right(response) =>
        val (refund, product) = response.answers
        println(s"${refund.probability} ${product.choice}")
      case Left(error) => println(s"Jev API error: ${error.getMessage}")

    backend.close()
```

## Gateways and compatible servers

Any server that follows the TypeSafe OpenAPI spec works: set `baseUrl` and `model`.

```scala mdoc:compile-only
import sttp.ai.jev.*
import sttp.model.Uri

val openRouter = JevConfig(
  apiKey = sys.env("OPENROUTER_API_KEY"),
  baseUrl = Uri.unsafeParse("https://openrouter.ai/api"),
  model = JevModel.CustomModel("~typesafe/jev-latest")
)

val vercel = JevConfig(
  apiKey = sys.env("AI_GATEWAY_API_KEY"),
  baseUrl = Uri.unsafeParse("https://ai-gateway.vercel.sh/typesafe"),
  model = JevModel.CustomModel("typesafe-ai/jev")
)
```

The same can be set with `TYPESAFE_BASE_URL` and `TYPESAFE_DEFAULT_MODEL`. `listModels()` needs the server to implement `GET /v1/models` as in the spec (Vercel does; OpenRouter returns its own catalogue there).

## Jev Configuration

```scala
case class JevConfig(
  apiKey: String,                                            // Your TypeSafe API key
  baseUrl: Uri = Uri.unsafeParse("https://api.typesafe.ai"),
  model: JevModel = JevModel.JevLatest,                      // Used by every request; JevModel.CustomModel("jev-1.13.0") pins a version
  timeout: Duration = 10.minutes,                            // Request timeout
  maxRetries: Int = 3,                                       // Max retry attempts, honored by JevSyncClient
  organization: Option[String] = None                        // Unused by Jev, present for parity
)
```

**Environment Variables** (read by `fromEnv`):
- `TYPESAFE_API_KEY` — your API key (required; `JEV_API_KEY` is used if it is not set or empty)
- `TYPESAFE_BASE_URL` — custom base URL (optional)
- `TYPESAFE_DEFAULT_MODEL` — model id (optional)

## Errors

All errors extend `JevExceptions.JevException`, which carries the server's `requestId` when present.

| Exception | Cause |
|---|---|
| `InvalidRequestException` | 400, 422 |
| `AuthenticationException` | 401, 403 |
| `RateLimitException` | 429 |
| `OverloadedException` | 529 (retry with backoff) |
| `APIException` | any other non-2xx status |
| `DeserializationJevException` | a 2xx body that could not be decoded |

Design notes: see [ADR 0005](https://github.com/softwaremill/sttp-ai/blob/master/docs/adr/0005-jev-typed-questions.md).
