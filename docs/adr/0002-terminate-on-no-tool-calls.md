# 2. Terminate the agent loop when the model returns no tool calls

Date: 2026-06-09

## Status

Accepted. Supersedes [ADR 0001](0001-agent-stop-signal.md).

## Context

[ADR 0001](0001-agent-stop-signal.md) chose a dedicated `finish` tool to signal termination. In practice, it made the code, configuration, and 
system prompt more complex. Providers offer structured output feature (OpenAI `response_format`, Claude `output_config`),
so a schema-shaped final answer does not need the tool either. Established agent loop projects such as those in 
[Pi](https://github.com/badlogic/pi-mono) and [Hermes Agent](https://github.com/nousresearch/hermes-agent) also rely on
the lack of tool calls for the final answer.

## Decision

Terminate the loop when the model responds **without any tool calls**; that response's text is the
final answer. A response with tool calls means "keep working" — execute them, append the results,
and continue.

Finish reasons:
* no tool calls → `NaturalStop` (or `TokenLimit` if `stopReason = MaxTokens`)
* `maxIterations` reached → `MaxIterations`

For typed results, the final answer is constrained via the provider's structured-output mechanism
(`responseSchema`), not a tool argument.

## Consequences

* One fewer model turn and no reserved tool slot; termination uses the unambiguous "no tool calls"
  signal already present in every response.
* 
