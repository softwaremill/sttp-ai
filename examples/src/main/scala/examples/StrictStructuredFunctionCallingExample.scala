//> using dep com.softwaremill.sttp.ai::openai:0.5.2
//> using dep ch.qos.logback:logback-classic:1.5.38

package examples

/** Manual playground for the strict structured outputs for function calling feature (`strict = true`).
  *
  * It sends the instruction "Say hello" but constrains the model with a JSON-Schema that requires the reply to be a number. When
  * `strict = true` is set, the schema wins and the model must produce something that validates – hence we expect a number, not the word
  * "hello".
  *
  * Run from the project root folder with: OPENAI_API_KEY=… sbt "examples3/runMain examples.StrictStructuredFunctionCallingExample"
  */
object StrictStructuredFunctionCallingExample extends App {
  import io.circe.Json
  import io.circe.syntax.*
  import sttp.client4.{DefaultSyncBackend, SyncBackend}
  import sttp.ai.openai.OpenAI
  import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel, ResponseFormat}
  import sttp.ai.openai.requests.completions.chat.message.{Content, Message, ToolChoice}
  import sttp.ai.openai.requests.completions.chat.message.Tool.Function

  val apiKey = sys.env.getOrElse("OPENAI_API_KEY", sys.error("OPENAI_API_KEY env variable not set"))

  val getNumberTool = Function(
    description = Some("Convert given text to upper-case"),
    name = "uppercase_text",
    parameters = Some(
      Map(
        "type" := "object",
        "properties" -> Json.obj(
          "text" -> Json.obj("type" := "number")
        ),
        "required" := Seq("text"),
        "additionalProperties" := false
      )
    ),
    strict = Some(true)
  )

  val chatBody = ChatBody(
    model = ChatCompletionModel.GPT4oMini,
    messages = Seq(Message.User(Content.TextContent("Please uppercase the word 'hello'"))),
    tools = Some(Seq(getNumberTool)),
    toolChoice = Some(ToolChoice.Function("uppercase_text"))
  )

  val backend: SyncBackend = DefaultSyncBackend()
  val openAI = new OpenAI(apiKey)

  println("Sending request …")
  val responseEither = openAI.createChatCompletion(chatBody).send(backend).body

  responseEither match {
    case Left(err) =>
      System.err.println(s"OpenAI returned an error: $err")
    case Right(resp) =>
      val choice = resp.choices.head

      val maybeArgsRaw: Option[String] = choice.message.toolCalls.collectFirst {
        case sttp.ai.openai.requests.completions.chat.ToolCall.FunctionToolCall(_, fn) => fn.arguments
      }

      maybeArgsRaw match {
        case Some(jsonStr) =>
          println(s"Function call arguments: $jsonStr")
          val parsed = io.circe.parser.parse(jsonStr).getOrElse(Json.Null)
          val maybeNum = parsed.asObject.flatMap(_("text")).flatMap(_.asNumber.map(_.toDouble))

          maybeNum match {
            case Some(n) => println(s"Success, numeric value provided: $n")
            case None    => println("Failure: arguments didn't contain numeric 'text' field.")
          }

        case None =>
          println("Model did not return a function call. Full message: ")
          println(choice.message)
      }
  }

  backend.close()
}
