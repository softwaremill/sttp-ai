//> using scala 3.3.8
//> using jvm 21
//> using dep com.softwaremill.sttp.ai::mcp:0.11.4
//> using dep com.softwaremill.sttp.ai::openai:0.11.4

package examples

import chimp.client.McpClient
import chimp.client.transport.ClientHttpTransport
import chimp.protocol.Implementation
import io.circe.Json
import io.circe.parser.parse
import sttp.ai.core.agent.{AgentFailure, AgentResult, AgentTool}
import sttp.ai.core.agent.mcp.McpTools
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.{DefaultSyncBackend, StringBody}
import sttp.client4.testing.ResponseStub
import sttp.model.{Header, StatusCode}
import sttp.model.Uri.UriContext
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

/** Real anonymous MCP tools, with a controlled OpenAI HTTP response seam. This checks wiring, not model quality. */
object ParallelSearchAgentExample {
  private given MonadError[Identity] = IdentityMonad

  private def json(text: String): Json = parse(text).fold(throw _, identity)

  private def completion(message: Json, finishReason: String): String = Json
    .obj(
      "id" -> Json.fromString("controlled-completion"),
      "object" -> Json.fromString("chat.completion"),
      "created" -> Json.fromInt(0),
      "model" -> Json.fromString("gpt-4o-mini"),
      "choices" -> Json.arr(Json.obj("index" -> Json.fromInt(0), "message" -> message, "finish_reason" -> Json.fromString(finishReason))),
      "usage" -> Json.obj("prompt_tokens" -> Json.fromInt(0), "completion_tokens" -> Json.fromInt(0), "total_tokens" -> Json.fromInt(0))
    )
    .noSpaces

  def run(tools: Seq[AgentTool[Identity, ?]]): AgentResult[Either[AgentFailure, String]] = {
    val sessionId = Json.fromString(java.util.UUID.randomUUID().toString)
    def call(id: String, name: String, arguments: Json): String = completion(
      Json.obj(
        "role" -> Json.fromString("assistant"),
        "content" -> Json.Null,
        "tool_calls" -> Json.arr(
          Json.obj(
            "id" -> Json.fromString(id),
            "type" -> Json.fromString("function"),
            "function" -> Json.obj("name" -> Json.fromString(name), "arguments" -> Json.fromString(arguments.noSpaces))
          )
        )
      ),
      "tool_calls"
    )

    // Only the model HTTP boundary is stubbed. MCP uses its own real backend in main.
    val modelBackend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      require(request.uri == uri"https://api.openai.com/v1/chat/completions", "Unexpected model endpoint")
      val body = request.body match {
        case StringBody(text, _, _) => json(text)
        case other                  => throw new IllegalArgumentException(s"Unexpected model body: $other")
      }
      val messages = body.hcursor.get[Vector[Json]]("messages").fold(throw _, identity)
      val results = messages.filter(_.hcursor.get[String]("role").contains("tool"))
      def result(id: String): Json = {
        val message = results
          .find(_.hcursor.get[String]("tool_call_id").contains(id))
          .getOrElse(
            throw new IllegalStateException(s"Missing tool result $id")
          )
        json(message.hcursor.get[String]("content").fold(throw _, identity))
      }
      val response = results.size match {
        case 0 =>
          val names = body.hcursor
            .get[Vector[Json]]("tools")
            .fold(throw _, identity)
            .map(
              _.hcursor.downField("function").get[String]("name").fold(throw _, identity)
            )
          require(names.toSet == Set("parallel_web_search", "parallel_web_fetch"), "Expected both loaded MCP tools")
          call(
            "search",
            "parallel_web_search",
            Json.obj(
              "objective" -> Json.fromString("Find sttp-ai documentation explaining MCP tools."),
              "search_queries" -> Json.arr(Json.fromString("sttp-ai MCP tools documentation")),
              "session_id" -> sessionId
            )
          )
        case 1 =>
          // Take an actual search result, restricted to the documentation host for this example.
          val found = result("search").hcursor.get[Vector[Json]]("results").fold(throw _, identity)
          val url = found
            .flatMap(_.hcursor.get[String]("url").toOption)
            .find(
              _.startsWith("https://sttp-ai.softwaremill.com/")
            )
            .getOrElse(throw new IllegalStateException("Search did not return an sttp-ai documentation URL"))
          call(
            "fetch",
            "parallel_web_fetch",
            Json.obj(
              "urls" -> Json.arr(Json.fromString(url)),
              "objective" -> Json.fromString("How are MCP tools loaded and executed?"),
              "session_id" -> sessionId
            )
          )
        case 2 =>
          val fetched = result("fetch")
          require(fetched.hcursor.get[Vector[Json]]("errors").fold(throw _, identity).isEmpty, "Fetch reported errors")
          val excerpts = fetched.hcursor
            .get[Vector[Json]]("results")
            .fold(throw _, identity)
            .flatMap(
              _.hcursor.get[Vector[String]]("excerpts").fold(throw _, identity)
            )
            .mkString("\n")
          require(excerpts.contains("McpTools.fromClient"), "Fetch must return useful MCP documentation")
          completion(Json.obj("role" -> Json.fromString("assistant"), "content" -> Json.fromString(excerpts)), "stop")
        case other => throw new IllegalStateException(s"Unexpected tool result count: $other")
      }
      ResponseStub.adjust(response, StatusCode.Ok)
    }
    try
      OpenAIAgent
        .synchronous(new OpenAI("unused-controlled-key"), "gpt-4o-mini")
        .maxIterations(4)
        .tools(tools)
        .build
        .run("Find and fetch sttp-ai's MCP documentation, then quote the returned excerpts.")(modelBackend)
    finally modelBackend.close()
  }

  def main(args: Array[String]): Unit = {
    val backend = DefaultSyncBackend()
    try {
      val transport = ClientHttpTransport[Identity](
        backend,
        uri"https://search.parallel.ai/mcp",
        headers = Seq(Header("User-Agent", "sttp-ai-parallel-example/0.11.4"))
      )
      val client = McpClient[Identity](transport, Implementation("sttp-ai-parallel-example", "0.11.4"))
      try {
        val result = run(McpTools.fromClient(client, namePrefix = Some("parallel")))
        require(result.toolCalls.map(_.toolName) == Seq("parallel_web_search", "parallel_web_fetch"), "Expected search then fetch")
        require(result.iterations == 3, "Expected both tool results to feed subsequent model turns")
        println(result.finalAnswer.fold(failure => throw new IllegalStateException(failure.toString), identity))
      } finally client.close()
    } finally backend.close()
  }
}
