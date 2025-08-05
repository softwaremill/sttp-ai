package sttp.openai.requests.responses

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.{Schema, SchemaType}
import sttp.openai.fixtures.ResponsesFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.{Tool, ToolChoice}
import sttp.openai.requests.responses.ResponsesRequestBody.Format.JsonSchema
import sttp.openai.requests.responses.ResponsesRequestBody._
import ujson.{Obj, Str}

class ResponsesDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given responses request as case class" should "be properly serialized to Json" in {

    // given
    val givenRequest = ResponsesRequestBody(
      background = Some(false),
      include = Some(List("code_interpreter_call.outputs", "message.output_text.logprobs")),
      input = Some(Left(Input.Text("What is the capital of France?"))),
      instructions = Some("You are a helpful assistant"),
      maxOutputTokens = Some(1000),
      maxToolCalls = Some(5),
      metadata = Some(Map("key1" -> "value1", "key2" -> "value2")),
      model = Some("gpt-4o"),
      parallelToolCalls = Some(true),
      previousResponseId = Some("prev_resp_123"),
      prompt = Some(
        PromptConfig(
          id = "prompt_123",
          variables = Some(Map("var1" -> "val1")),
          version = Some("1.0")
        )
      ),
      promptCacheKey = Some("cache_key_123"),
      reasoning = Some(
        ReasoningConfig(
          effort = Some("high"),
          summary = Some("detailed")
        )
      ),
      safetyIdentifier = Some("safety_123"),
      serviceTier = Some("auto"),
      store = Some(true),
      stream = Some(false),
      temperature = Some(0.7),
      text = Some(
        TextConfig(
          format = Some(
            JsonSchema(
              name = "response_schema",
              schema = Some(Schema(SchemaType.String)),
              description = Some("Response format"),
              strict = Some(true)
            )
          )
        )
      ),
      toolChoice = Some(ToolChoice.ToolAuto),
      tools = Some(List(Tool.CodeInterpreterTool)),
      topLogprobs = Some(5),
      topP = Some(0.9),
      truncation = Some("disabled"),
      user = Some("user123")
    )

    val jsonRequest = ujson.read(ResponsesFixture.jsonRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given responses request with text format" should "be properly serialized to Json" in {
    import ResponsesRequestBody._

    // given
    val givenRequest = ResponsesRequestBody(
      model = Some("gpt-4o"),
      text = Some(
        TextConfig(
          format = Some(Format.Text)
        )
      )
    )

    val expectedJson = Obj(
      "model" -> Str("gpt-4o"),
      "text" -> Obj(
        "format" -> Obj(
          "type" -> Str("text")
        )
      )
    )

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe expectedJson
  }

  "Given responses request with input message containing text and image" should "be properly serialized to Json" in {
    import ResponsesRequestBody._
    import Input._
    import InputContentItem._

    // given
    val givenRequest = ResponsesRequestBody(
      model = Some("gpt-4.1"),
      input = Some(
        Right(InputMessage(
          content = List(
            InputText("what is in this image?"),
            InputImage(
              detail = "auto", // default detail level
              fileId = None,
              imageUrl = Some("https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg")
            )
          ),
          role = "user",
          status = None
        ) :: Nil)
      )
    )

    val expectedJson = ujson.read(ResponsesFixture.jsonRequestWithInputMessage)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe expectedJson
  }
  
  "Given responses request with input message containing text and file" should "be properly serialized to Json" in {
    import ResponsesRequestBody._
    import Input._
    import InputContentItem._

    // given
    val givenRequest = ResponsesRequestBody(
      model = Some("gpt-4.1"),
      input = Some(
        Right(InputMessage(
          content = List(
            InputText("what is in this file?"),
            InputFile(
              fileData = None,
              fileId = None,
              fileUrl = Some("https://www.berkshirehathaway.com/letters/2024ltr.pdf"),
              filename = None
            )
          ),
          role = "user",
          status = None
        ) :: Nil)
      )
    )

    val expectedJson = ujson.read(ResponsesFixture.jsonRequestWithInputFile)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe expectedJson
  }
  
}
