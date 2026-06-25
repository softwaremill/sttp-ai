# 3. Replace uPickle with circe for JSON

Date: 2026-06-25

## Status

Accepted.

## Context

JSON ran on uPickle (`SnakePickle`). SoftwareMill's consumers of the library are based on circe,
so uPickle forced a second JSON library on them.

## Decision

Use circe as the single JSON library. Codecs are split into `CirceConfiguration` (shared snake_case
config), `*DerivedCodecs` (generated, per Scala version), shared circe-core `*ManualCodecs` (custom
discriminators, string enums, `Option`-collapsing), and `CirceHelpers` helpers. The public API
type-class changes from `SnakePickle.ReadWriter[T]` to `io.circe.Codec[T]`.

## Consequences

* Request bodies use `deepDropNullValues` to omit `None`. Trade-off: any explicit `null` in JSON Schemas
defined by the user will be dropped (tapir schemas don't hit this edge case).
* Source-breaking renames (wire formats unchanged): assistants `Tool.{FunctionTool→Function,
  CodeInterpreterTool→CodeInterpreter, FileSearchTool→FileSearch}`; chat `Message.{System,User,
  Assistant,Tool}` and `Tool.{Function,Custom}`; Claude `ContentBlock` swap `WebSearchToolResultContent→
  WebSearchToolResult` and inner `WebSearchToolResult→WebSearchToolResultBlock`.
