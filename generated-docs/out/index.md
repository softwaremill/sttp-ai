# sttp-ai: Scala client for OpenAI, Claude, Gemini, and compatible APIs

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): the Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): typed API descriptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), Gemini (Google), and OpenAI-compatible APIs (e.g. Azure OpenAI, Ollama, Grok, Groq, OpenRouter). Use the power of ChatGPT, Claude, and Gemini inside your code!

sttp-ai uses [sttp client](https://github.com/softwaremill/sttp) to describe requests and responses as plain, type-safe Scala values — request bodies, response models, JSON schemas, and tool definitions are all derived from case classes via [Tapir](https://tapir.softwaremill.com) and [circe](https://circe.github.io/circe/), with minimal ceremony and no hand-written JSON.

## What can you do with sttp-ai?

* **Call model APIs directly** — [OpenAI](openai/basics.md) (chat completions, embeddings, audio, images, and more), [Claude](claude/basics.md) (Messages API), and [Gemini](gemini/basics.md) (Interactions API); each provider has a blocking sync client and a raw-request async client that works with any effect system
* **Use OpenAI-compatible providers** — [Azure OpenAI, Ollama, Grok, Groq, OpenRouter, vLLM and others](openai/compatible-apis.md) via the OpenAI client with a custom base URL
* **Stream responses** — [server-sent events streaming](openai/streaming.md) for fs2, ZIO, Akka Streams, Pekko Streams, and Ox
* **Get structured outputs** — [JSON-schema-constrained responses](other/json-schemas.md) parsed straight into your case classes ([OpenAI](openai/structured-outputs.md), [Claude](claude/structured-outputs.md), [Gemini](gemini/structured-outputs.md))
* **Call tools** — let the model invoke functions in your code ([OpenAI](openai/tool-calling.md), [Claude](claude/tool-calling.md), [Gemini](gemini/tool-calling.md))
* **Run an agent loop** — [autonomous tool-calling agents](agents/quickstart.md) with typed tools and typed results, working across all three providers, with tools loadable from [MCP servers](agents/mcp.md), testable offline with the [agent testkit](agents/testing.md)

## Why sttp-ai?

sttp-ai implements the native APIs of all three major providers — OpenAI, Claude, and Gemini — in one library, rather than funnelling everything through an OpenAI-compatibility shim. You bring your own effect system: cats-effect, ZIO, Akka/Pekko Streams, direct-style [Ox](https://github.com/softwaremill/ox), or plain blocking calls. Structured-output schemas and tool definitions are derived from case classes instead of written by hand, and the built-in agent loop gives you typed tools and typed results, with tools loadable from MCP servers. Cross-built for Scala 2.13 and Scala 3, with Scala Native support (Scala 3) for the core and provider modules.

```{eval-rst}
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart

.. toctree::
   :maxdepth: 2
   :caption: OpenAI

   openai/basics
   openai/streaming
   openai/structured-outputs
   openai/tool-calling
   openai/compatible-apis

.. toctree::
   :maxdepth: 2
   :caption: Claude

   claude/basics
   claude/messages
   claude/structured-outputs
   claude/tool-calling
   claude/streaming
   claude/models-and-errors

.. toctree::
   :maxdepth: 2
   :caption: Gemini

   gemini/basics
   gemini/interactions
   gemini/tool-calling
   gemini/structured-outputs
   gemini/streaming
   gemini/models-and-errors

.. toctree::
   :maxdepth: 2
   :caption: Agent loop

   agents/quickstart
   agents/configuration
   agents/tools
   agents/mcp
   agents/custom-backends
   agents/testing

.. toctree::
   :maxdepth: 2
   :caption: Other

   other/json-schemas
   other/timeouts-and-retries
   other/examples
```
