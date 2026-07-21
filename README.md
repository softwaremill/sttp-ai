![sttp-ai](https://github.com/softwaremill/sttp-ai/raw/master/banner.png)

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/open-source)
[![CI](https://github.com/softwaremill/sttp-ai/workflows/CI/badge.svg)](https://github.com/softwaremill/sttp-ai/actions?query=workflow%3ACI+branch%3Amaster)

[![sttp.ai:core](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/core_3/badge.svg?subject=sttp.ai:core)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/core_3/)
[![sttp.ai:openai](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/openai_3/badge.svg?subject=sttp.ai:openai)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/openai_3/)
[![sttp.ai:claude](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/claude_3/badge.svg?subject=sttp.ai:claude)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/claude_3/)

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): The Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs. Use the power of ChatGPT and Claude inside your code!

sttp-ai uses sttp client to describe requests and responses used in OpenAI, Claude (Anthropic), and OpenAI-compatible endpoints.

## Documentation

**Full documentation is available at [sttp-ai.softwaremill.com](https://sttp-ai.softwaremill.com).**

## Quickstart

### For OpenAI/OpenAI-compatible APIs

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "openai" % "0.5.4"
```

### For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "0.5.4"

// For streaming support, add one or more:
"com.softwaremill.sttp.ai" %% "claude-streaming-fs2" % "0.5.4"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "claude-streaming-zio" % "0.5.4"    // ZIO
"com.softwaremill.sttp.ai" %% "claude-streaming-akka" % "0.5.4"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "claude-streaming-pekko" % "0.5.4"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "claude-streaming-ox" % "0.5.4"    // Ox direct-style (Scala 3 only)
```

sttp-openai is available for Scala 2.13 and Scala 3

Then head to the [documentation](https://sttp-ai.softwaremill.com) for usage examples: OpenAI and Claude clients, streaming, structured outputs, tool calling, and the agent loop.

## Contributing

If you have a question, or hit a problem, feel free to post on our community https://softwaremill.community/c/open-source/

Or, if you encounter a bug, something is unclear in the code or documentation, don't hesitate and open an issue on GitHub.

For running integration tests against the real OpenAI API, see [Integration Testing Guide](INTEGRATION_TESTING.md).

## Commercial Support

We offer commercial support for sttp and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2023-2025 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
