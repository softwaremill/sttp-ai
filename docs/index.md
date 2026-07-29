# sttp-ai: Scala client for OpenAI, Claude, Gemini, and compatible APIs

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): the Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): typed API descriptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), Gemini (Google), and OpenAI-compatible APIs (e.g. Ollama, Groq, OpenRouter). Use the power of ChatGPT, Claude, and Gemini inside your code!

sttp-ai uses [sttp client](https://github.com/softwaremill/sttp) to describe requests and responses used in OpenAI, Claude (Anthropic), Gemini (Google), and OpenAI-compatible endpoints.

## What can you do with sttp-ai?

* **Call model APIs directly** — [OpenAI](openai/basics.md) (chat completions, embeddings, audio, images, and more), [Claude](claude/basics.md) (Messages API), and [Gemini](gemini/basics.md) (Interactions API); each provider has a blocking sync client and a raw-request async client that works with any effect system
* **Use OpenAI-compatible providers** — [Ollama, Groq, OpenRouter, vLLM and others](openai/compatible-apis.md) via the OpenAI client with a custom base URL
* **Stream responses** — [server-sent events streaming](openai/streaming.md) for fs2, ZIO, Akka Streams, Pekko Streams, and Ox
* **Get structured outputs** — [JSON-schema-constrained responses](other/json-schemas.md) parsed straight into your case classes ([OpenAI](openai/structured-outputs.md), [Claude](claude/structured-outputs.md), [Gemini](gemini/structured-outputs.md))
* **Call tools** — let the model invoke functions in your code ([OpenAI](openai/tool-calling.md), [Claude](claude/tool-calling.md), [Gemini](gemini/tool-calling.md))
* **Run an agent loop** — [autonomous tool-calling agents](agents/quickstart.md) with typed tools and typed results, working across all three providers, with tools loadable from [MCP servers](agents/mcp.md)

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

.. toctree::
   :maxdepth: 2
   :caption: Other

   other/json-schemas
   other/examples
```
