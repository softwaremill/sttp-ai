package sttp.openai.requests.completions.chat

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.Stop.SingleStop
import sttp.openai.requests.completions.chat.ChatRequestBody.Format.Mp3
import sttp.openai.requests.completions.chat.ChatRequestBody.Voice.Ash
import sttp.openai.requests.completions.chat.Role.Assistant
import sttp.openai.requests.completions.{CompletionTokensDetails, PromptTokensDetails, Usage}
import sttp.openai.utils.ChatCompletionFixtures._
import sttp.openai.utils.JsonUtils

class ChatDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given delete chat completion response as Json" should "be properly deserialized to case class" in {
    import ChatRequestResponseData.DeleteChatCompletionResponse
    import ChatRequestResponseData.DeleteChatCompletionResponse._

    // given
    val jsonResponse = fixtures.ChatFixture.jsonDeleteChatCompletionResponse
    val expectedResponse = DeleteChatCompletionResponse(
      id = "chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2",
      deleted = true
    )
    // when
    val givenResponse: Either[Exception, DeleteChatCompletionResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)
    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list chat response as Json" should "be properly deserialized to case class" in {
    import ChatRequestResponseData.ListChatResponse
    import ChatRequestResponseData.ListChatResponse._

    // given
    val jsonResponse = fixtures.ChatFixture.jsonListChatResponse
    val expectedResponse = ListChatResponse(
      data = Seq(),
      firstId = "chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2",
      lastId = "chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2",
      hasMore = false
    )
    // when
    val givenResponse: Either[Exception, ListChatResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)
    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list message response as Json" should "be properly deserialized to case class" in {
    import ChatRequestResponseData.ListMessageResponse._
    import ChatRequestResponseData.{ListMessageResponse, Message}

    // given
    val jsonResponse = fixtures.ChatFixture.jsonListMessageResponse
    val expectedResponse = ListMessageResponse(
      data = Seq(
        Message(
          role = Assistant,
          content = "Hi there! How can I assist you today?",
          audio = Some(
            Audio(
              id = "audio_id",
              expiresAt = 1681725687,
              data = "base64encoded",
              transcript = "transcript"
            )
          )
        )
      ),
      firstId = "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      lastId = "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      hasMore = true
    )
    // when
    val givenResponse: Either[Exception, ListMessageResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)
    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given chat completions response as Json" should "be properly deserialized to case class" in {
    import ChatRequestResponseData.ChatResponse._
    import ChatRequestResponseData._

    // given
    val jsonResponse = fixtures.ChatFixture.jsonResponse

    val usage: Usage = Usage(
      promptTokens = 10,
      completionTokens = 10,
      totalTokens = 20,
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

    val message: Message = Message(
      role = Role.Assistant,
      content = "Hi there! How can I assist you today?",
      toolCalls = toolCalls
    )

    val choices: Choices = Choices(
      message = message,
      finishReason = "stop",
      index = 0,
      logprobs = Some(
        Logprobs(
          content = Some(
            Seq(
              LogprobData(
                token = "Hello",
                logprob = -0.1f,
                bytes = Some(Seq(2, 3, 4)),
                topLogprobs = Seq(TopLogprobs(token = "Hello", logprob = -0.2f, bytes = Some(Seq(4, 5, 6))))
              )
            )
          ),
          refusal = Some(
            Seq(
              LogprobData(
                token = "Hello",
                logprob = -0.1f,
                bytes = Some(Seq(2, 3, 4)),
                topLogprobs = Seq(TopLogprobs(token = "Hello", logprob = -0.2f, bytes = Some(Seq(4, 5, 6))))
              )
            )
          )
        )
      )
    )

    val expectedResponse: ChatResponse = ChatResponse(
      id = "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      `object` = "chat.completion",
      created = 1681725687,
      model = "gpt-3.5-turbo-0301",
      usage = usage,
      choices = Seq(choices),
      systemFingerprint = Some("systemFingerprint"),
      serviceTier = Some("advanced")
    )

    // when
    val givenResponse: Either[Exception, ChatResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given completions request as case class" should "be properly serialized to Json" in {
    import ChatRequestBody._
    import sttp.openai.requests.completions.chat.message._

    // given
    val givenRequest = ChatRequestBody.ChatBody(
      messages = messages,
      model = ChatCompletionModel.GPT35Turbo,
      frequencyPenalty = Some(0),
      maxTokens = Some(7),
      n = Some(1),
      presencePenalty = Some(0),
      temperature = Some(1),
      topP = Some(1),
      tools = Some(tools),
      responseFormat = Some(ResponseFormat.JsonObject),
      toolChoice = Some(ToolChoice.ToolFunction("function")),
      stop = Some(SingleStop("\n")),
      user = Some("testUser"),
      store = Some(true),
      reasoningEffort = Some(ReasoningEffort.Low),
      metadata = Some(Map("key" -> "value")),
      logprobs = Some(true),
      topLogprobs = Some(1),
      maxCompletionTokens = Some(10),
      modalities = Some(Seq("text", "audio")),
      serviceTier = Some("advanced"),
      parallelToolCalls = Some(true),
      streamOptions = Some(StreamOptions(includeUsage = Some(true))),
      prediction =
        Some(Prediction(`type` = "content", content = MultipartContent(value = Seq(ContentPart(`type` = "code", text = "simple text"))))),
      audio = Some(Audio(voice = Ash, format = Mp3))
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.ChatFixture.jsonRequest)

    // when
    val serializedJson = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given update chat completions request as case class" should "be properly serialized to Json" in {
    import ChatRequestBody.UpdateChatCompletionRequestBody._

    // given
    val givenRequest = ChatRequestBody.UpdateChatCompletionRequestBody(
      metadata = Map("key" -> "value")
    )
    val jsonRequest: ujson.Value = ujson.read(fixtures.ChatFixture.jsonUpdateRequest)
    // when
    val serializedJson = SnakePickle.writeJs(givenRequest)
    // then
    serializedJson shouldBe jsonRequest
  }
}
