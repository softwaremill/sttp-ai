# sttp-ai: Scala client for OpenAI, Claude, and compatible APIs

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): The Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs (e.g. Ollama, Grok, OpenRouter). Use the power of ChatGPT and Claude inside your code!

sttp-ai uses sttp client to describe requests and responses used in OpenAI, Claude (Anthropic), and OpenAI-compatible endpoints.

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
   :caption: Agent loop

   agents/quickstart
   agents/configuration
   agents/tools
   agents/mcp

.. toctree::
   :maxdepth: 2
   :caption: Other

   other/backends
   other/examples
```
