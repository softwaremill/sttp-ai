# 4. Cross-build the Scala 2 modules for Scala 2.12

Date: 2026-08-12

## Status

Proposed.

## Context

Some consumers run large Scala 2.12 codebases where a 2.13 migration is a multi-month project, but
adopting an AI client library is needed now. sttp-ai publishes only 2.13 and 3 artifacts, while every
dependency the build pins (sttp client4, tapir-apispec-docs, circe, circe-generic-extras, sttp-apispec,
pekko, akka 2.6, scalatest) already publishes `_2.12` artifacts at those exact versions — so the gap is
in this build, not the ecosystem. Existing 2.13 and 3 support must not regress, in behaviour or in
compiler-warning hygiene.

## Decision

Add Scala 2.12.20 to the `scala2` build-matrix row, cross-building every module that already builds for
2.13: `core`, `openai`, `claude`, `gemini`, `agent-testkit`, and the fs2/zio/pekko/akka streaming
modules. The Scala 3-only modules (`mcp`, `ox`, `examples`, `docs`) and the Scala Native targets are
unchanged.

Shared sources are kept cross-compatible rather than duplicated:

* 2.13-only stdlib calls are replaced with equivalents available on both (`String.toLongOption`,
  `Either.orElse`, `Seq.distinctBy`).
* `sttp.ai.core.compat.unused` papers over `scala.annotation.unused`, which does not exist on 2.12:
  an alias to the real annotation on 2.13/3, an inert annotation on 2.12, with the unused-warning
  message silenced on 2.12 rows only so 2.13 keeps full checking.
* Immutable collections are materialised where 2.12's default `scala.collection.Seq` is not accepted
  (sttp's `multipartBody`, pekko/akka `Source`), and type ascriptions added where 2.12's weaker
  inference fails.
* `Attachment` references `assistants.Tool` fully qualified: 2.12 resolves same-package members ahead
  of explicit imports ([scala/bug#4695](https://github.com/scala/bug/issues/4695), fixed in 2.13), which
  silently bound the wrong `Tool` type there.

## Consequences

* 2.12 users get the full Scala 2 surface, including the akka streaming module that exists for them.
* Shared sources are restricted to the 2.12-compatible subset of the standard library; the 2.12 CI rows
  enforce this on every PR, so the constraint is discovered at review time, not by consumers.
* Dependency updates must keep `_2.12` artifacts available. When a pinned dependency drops 2.12, that is
  the exit criterion: the 2.12 row is removed in the next breaking release rather than holding the
  dependency back.
* The import-precedence difference is a silent-miscompilation hazard unique to 2.12 (code compiles with a
  different meaning, not an error); the cross-version test suite is the guard against it.
* Per-site unused-warning suppression is unavailable on 2.12; that warning category is silenced row-wide
  there as the least-bad option.
