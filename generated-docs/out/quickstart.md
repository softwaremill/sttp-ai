# Quickstart

## For OpenAI/OpenAI-compatible APIs

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "openai" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"
```

## For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"

// For streaming support, add one or more:
"com.softwaremill.sttp.ai" %% "claude-streaming-fs2" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "claude-streaming-zio" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"    // ZIO
"com.softwaremill.sttp.ai" %% "claude-streaming-akka" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "claude-streaming-pekko" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "claude-streaming-ox" % "0.5.1+14-45313544+20260714-0930-SNAPSHOT"    // Ox direct-style (Scala 3 only)
```

sttp-openai is available for Scala 2.13 and Scala 3
