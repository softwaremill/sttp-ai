# 1. Use a dedicated `finish` tool to terminate the agent loop

Date: 2026-01-15

## Context

When designing the agent execution loop, we need a reliable and unambiguous way to determine when an agent has finished its work. Two common approaches are used in existing agent frameworks:

1. signaling completion via a dedicated tool call (e.g. `finish`), or
2. inferring completion from a structured output format.

Both approaches are viable, but they differ in robustness, configurability, and interaction with user-provided system prompts.

## Decision

We chose to use a dedicated `finish` tool as the canonical mechanism for terminating the agent loop.

This decision was driven by the following considerations:

* A `finish` tool provides an explicit and unambiguous termination signal, eliminating the need to infer completion from model output.
* Tool invocation is handled natively by the runtime/library, avoiding fragile output parsing and reducing retries.
* The tool definition is controlled by the runtime and is not affected by user-provided system prompts, making the behavior more stable and predictable.
* The agent loop can enforce clear invariants (e.g. “the agent must terminate via `finish`”), simplifying validation and reasoning about control flow.

As a result, the `finish` tool offers better reliability and clearer semantics for loop termination than relying on structured output conventions.
