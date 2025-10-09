package sttp.openai.requests.completions

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.Stop.SingleStop
import sttp.openai.utils.JsonUtils

class CompletionsDataSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given completions response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionModel.GPT35TurboInstruct
    import sttp.openai.requests.completions.CompletionsResponseData.CompletionsResponse._
    import sttp.openai.requests.completions.CompletionsResponseData._

    // given
    val jsonResponse = fixtures.CompletionsFixture.jsonSinglePromptResponse
    val expectedResponse: CompletionsResponse = CompletionsResponse(
      id = "cmpl-75C628xoevz3eE8zsTFDumZ5wqwmY",
      `object` = "text_completion",
      created = 1681472494,
      model = GPT35TurboInstruct,
      choices = Seq(
        Choices(
          text = "\n\nThis is indeed a test.",
          index = 0,
          finishReason = "stop",
          logprobs = None
        )
      ),
      usage = Usage(
        promptTokens = 5,
        completionTokens = 8,
        totalTokens = 13,
        completionTokensDetails = Some(
          CompletionTokensDetails(
            acceptedPredictionTokens = 3,
            audioTokens = 1,
            reasoningTokens = 4,
            rejectedPredictionTokens = 2
          )
        ),
        promptTokensDetails = Some(PromptTokensDetails(audioTokens = 2, cachedTokens = 1))
      )
    )

    // when
    val givenResponse: Either[Exception, CompletionsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given ollama completions response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionModel.CustomCompletionModel
    import sttp.openai.requests.completions.CompletionsResponseData.CompletionsResponse._
    import sttp.openai.requests.completions.CompletionsResponseData._

    // given
    val jsonResponse = fixtures.CompletionsFixture.ollamaPromptResponse
    val expectedResponse: CompletionsResponse = CompletionsResponse(
      id = "cmpl-712",
      `object` = "text_completion",
      created = 1733664264,
      model = CustomCompletionModel("llama3.2"),
      choices = Seq(
        Choices(
          text = "Greeting coding dawn\n\"Hello, world!\" echoes bright\nProgramming's start",
          index = 0,
          finishReason = "stop",
          logprobs = None
        )
      ),
      usage = Usage(
        promptTokens = 33,
        completionTokens = 17,
        totalTokens = 50
      )
    )

    // when
    val givenResponse: Either[Exception, CompletionsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given completions request as case class" should "be properly serialized to Json" in {
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionModel.GPT35TurboInstruct
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionsBody._
    import sttp.openai.requests.completions.CompletionsRequestBody._

    // given
    val givenRequest = CompletionsRequestBody.CompletionsBody(
      model = GPT35TurboInstruct,
      prompt = Some(SinglePrompt("Say this is a test")),
      maxTokens = Some(7),
      temperature = Some(0),
      topP = Some(1),
      n = Some(1),
      logprobs = None,
      stop = Some(SingleStop("\n"))
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.CompletionsFixture.jsonSinglePromptRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given completions of MultiplePrompt response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionModel.GPT35TurboInstruct
    import sttp.openai.requests.completions.CompletionsResponseData.CompletionsResponse._
    import sttp.openai.requests.completions.CompletionsResponseData._

    // given
    val jsonResponse = fixtures.CompletionsFixture.jsonMultiplePromptResponse
    val expectedResponse: CompletionsResponse = CompletionsResponse(
      id = "cmpl-76D8UlnqOEkhVXu29nY7UPZFDTTlP",
      `object` = "text_completion",
      created = 1681714818,
      model = GPT35TurboInstruct,
      choices = Seq(
        Choices(
          text = "\n\nThis is indeed a test",
          index = 0,
          finishReason = "length",
          logprobs = None
        ),
        Choices(
          text = "\n\nYes, this is also",
          index = 1,
          finishReason = "length",
          logprobs = None
        )
      ),
      usage = Usage(
        promptTokens = 11,
        completionTokens = 14,
        totalTokens = 25,
        completionTokensDetails = Some(
          CompletionTokensDetails(
            acceptedPredictionTokens = 3,
            audioTokens = 1,
            reasoningTokens = 4,
            rejectedPredictionTokens = 2
          )
        ),
        promptTokensDetails = Some(PromptTokensDetails(audioTokens = 2, cachedTokens = 1))
      )
    )

    // when
    val givenResponse: Either[Exception, CompletionsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given completions of MultiplePrompt request as case class" should "be properly serialized to Json" in {
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionModel.GPT35TurboInstruct
    import sttp.openai.requests.completions.CompletionsRequestBody.CompletionsBody._
    import sttp.openai.requests.completions.CompletionsRequestBody._

    // given
    val givenRequest = CompletionsRequestBody.CompletionsBody(
      model = GPT35TurboInstruct,
      prompt = Some(MultiplePrompt(Seq("Say this is a test", "Say this is also a test"))),
      maxTokens = Some(7),
      temperature = Some(0),
      topP = Some(1),
      n = Some(1),
      logprobs = None,
      stop = Some(SingleStop("\n"))
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.CompletionsFixture.jsonMultiplePromptRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

}
