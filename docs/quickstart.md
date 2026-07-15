# Quickstart

## For OpenAI/OpenAI-compatible APIs

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "openai" % "@VERSION@"
```

## For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "@VERSION@"

// For streaming support, add one or more:
"com.softwaremill.sttp.ai" %% "claude-streaming-fs2" % "@VERSION@"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "claude-streaming-zio" % "@VERSION@"    // ZIO
"com.softwaremill.sttp.ai" %% "claude-streaming-akka" % "@VERSION@"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "claude-streaming-pekko" % "@VERSION@"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "claude-streaming-ox" % "@VERSION@"    // Ox direct-style (Scala 3 only)
```

sttp-openai is available for Scala 2.13 and Scala 3
