# 5. Jev module: questions typed by their answers

Date: 2026-09-25

## Status

Accepted.

## Context

Jev (TypeSafe AI) does not generate text. A request carries one `state` and a map of typed questions
(noul, choice, score); the response carries one typed answer per question. The map keys are not shown to
the model; they only correlate answers with questions. Choice options and score levels are closed sets
defined by the caller. Server limits (at most 255 options, 10 levels) depend on the model version.

## Decision

* `Question[+A]` carries the answer type as a type parameter. A tuple of questions is answered by a tuple
  of answers whose type is computed by the `Answers[Qs]` match type, so `val (a, b) = client.ask(state,
  (noul, choice)).answers` is exactly typed. Choice options are arbitrary Scala values (`Choice.of` maps an
  enum's cases by their declared names) and are decoded back to those values.
* Wire keys are positional (`"0".."n-1"`); callers never name questions. Server errors refer to the same
  positions.
* The one purely client-side invariant, distinct option names, is a `require` in `Choice` (duplicates would
  collapse into one JSON key). Server limits are not duplicated client-side: the server's own message reaches
  the caller as `InvalidRequestException`.
* `model` is a `JevConfig` field: Scala forbids default arguments on overloaded methods, and `ask` is
  overloaded (single question, tuple).
* `JevSyncClient` throws like the other sync clients; `JevClient` returns `Request[Either[JevException, _]]`.
* Scala 3 only (match types, union `Entry = String | Json`, `Mirror`); flat `sttp.ai.jev` package, as the
  module is small.

## Consequences

* Typed access to answers comes from the tuple position; helpers over generic questions must take
  `Question[A]` so the result type is stated.
* Two unchecked casts live inside the client (tuple elements to `Question[?]`, decoded list to
  `Answers[Qs]`); both are justified by the `<:<` evidence and the match type.
* If a server limit changes, no library release is needed.
* No Scala 2 artifacts of this module.
