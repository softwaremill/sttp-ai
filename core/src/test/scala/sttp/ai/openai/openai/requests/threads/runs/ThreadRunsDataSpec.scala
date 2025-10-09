package sttp.openai.requests.threads.runs

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.json.SnakePickle
import sttp.openai.requests.assistants.Tool.{CodeInterpreterTool, FileSearchTool, FunctionTool}
import sttp.openai.requests.completions.chat.message.ToolResource.CodeInterpreterToolResource
import sttp.openai.requests.completions.chat.message.ToolResources
import sttp.openai.requests.threads.ThreadsRequestBody.CreateThreadBody
import sttp.openai.requests.threads.messages.ThreadMessagesRequestBody.CreateMessage
import sttp.openai.requests.threads.runs.ThreadRunsRequestBody.ToolOutput
import sttp.openai.requests.threads.runs.ThreadRunsResponseData.{
  ListRunStepsResponse,
  ListRunsResponse,
  MessageCreation,
  RunStepData,
  Usage
}
import sttp.openai.utils.JsonUtils
import ujson.{Arr, Obj, Str}

class ThreadRunsDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create thread run request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = ThreadRunsRequestBody.CreateRun(
      assistantId = "asst_abc123"
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.ThreadRunsFixture.jsonCreateRunRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create thread run response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonCreateRunResponse
    val expectedResponse: RunData = RunData(
      id = "run_abc123",
      `object` = "thread.run",
      createdAt = 1699063290,
      assistantId = "asst_abc123",
      threadId = "thread_abc123",
      status = "queued",
      startedAt = Some(1699063290),
      expiresAt = None,
      cancelledAt = None,
      failedAt = None,
      completedAt = Some(1699063291),
      lastError = None,
      model = "gpt-4",
      instructions = None,
      tools = Seq(CodeInterpreterTool),
      toolResources = Some(ToolResources(Some(CodeInterpreterToolResource(Some(Seq("file-abc123", "file-abc456")))))),
      metadata = Map.empty,
      usage = None
    )

    // when
    val givenResponse: Either[Exception, RunData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given create thread and run request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = ThreadRunsRequestBody.CreateThreadAndRun(
      assistantId = "asst_abc123",
      thread = CreateThreadBody(
        messages = Some(Seq(CreateMessage(role = "user", content = "Explain deep learning to a 5 year old.")))
      )
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.ThreadRunsFixture.jsonCreateThreadAndRunRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create thread and run response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonCreateThreadAndRunResponse
    val expectedResponse: RunData = RunData(
      id = "run_abc123",
      `object` = "thread.run",
      createdAt = 1699076792,
      assistantId = "asst_abc123",
      threadId = "thread_abc123",
      status = "queued",
      startedAt = None,
      expiresAt = Some(1699077392),
      cancelledAt = None,
      failedAt = None,
      completedAt = None,
      lastError = None,
      model = "gpt-4",
      instructions = Some("You are a helpful assistant."),
      tools = Seq.empty,
      toolResources = None,
      metadata = Map.empty,
      usage = None
    )

    // when
    val givenResponse: Either[Exception, RunData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list runs response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.ListRunsResponse._
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonListRunsResponse
    val expectedResponse: ListRunsResponse =
      ListRunsResponse(
        `object` = "list",
        data = Seq(
          RunData(
            id = "run_abc123",
            `object` = "thread.run",
            createdAt = 1699075072,
            assistantId = "asst_abc123",
            threadId = "thread_abc123",
            status = "completed",
            startedAt = Some(1699075072),
            expiresAt = None,
            cancelledAt = None,
            failedAt = None,
            completedAt = Some(1699075073),
            lastError = None,
            model = "gpt-3.5-turbo",
            instructions = None,
            tools = Seq(CodeInterpreterTool),
            toolResources = Some(ToolResources(Some(CodeInterpreterToolResource(Some(Seq("file-abc123", "file-abc456")))))),
            metadata = Map.empty,
            usage = Some(Usage(promptTokens = 123, completionTokens = 456, totalTokens = 579))
          ),
          RunData(
            id = "run_abc456",
            `object` = "thread.run",
            createdAt = 1699063290,
            assistantId = "asst_abc123",
            threadId = "thread_abc123",
            status = "completed",
            startedAt = Some(1699063290),
            expiresAt = None,
            cancelledAt = None,
            failedAt = None,
            completedAt = Some(1699063291),
            lastError = None,
            model = "gpt-3.5-turbo",
            instructions = None,
            tools = Seq(CodeInterpreterTool),
            toolResources = Some(ToolResources(Some(CodeInterpreterToolResource(Some(Seq("file-abc123", "file-abc456")))))),
            metadata = Map.empty,
            usage = Some(
              Usage(
                promptTokens = 123,
                completionTokens = 456,
                totalTokens = 579
              )
            )
          )
        ),
        firstId = "run_abc123",
        lastId = "run_abc456",
        hasMore = false
      )

    // when
    val givenResponse: Either[Exception, ListRunsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list run steps response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.ListRunStepsResponse._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonListRunStepsResponse
    val expectedResponse: ListRunStepsResponse =
      ListRunStepsResponse(
        `object` = "list",
        data = Seq(
          RunStepData(
            id = "step_abc123",
            `object` = "thread.run.step",
            createdAt = 1699063291,
            runId = "run_abc123",
            assistantId = "asst_abc123",
            threadId = "thread_abc123",
            `type` = "message_creation",
            status = "completed",
            cancelledAt = None,
            completedAt = Some(1699063291),
            expiredAt = None,
            failedAt = None,
            lastError = None,
            stepDetails = MessageCreation(
              messageId = "msg_abc123"
            ),
            usage = Some(
              Usage(
                promptTokens = 123,
                completionTokens = 456,
                totalTokens = 579
              )
            )
          )
        ),
        firstId = "step_abc123",
        lastId = "step_abc456",
        hasMore = false
      )

    // when
    val givenResponse: Either[Exception, ListRunStepsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given retrieve run response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonRetrieveRunResponse
    val expectedResponse: RunData =
      RunData(
        id = "run_abc123",
        `object` = "thread.run",
        createdAt = 1699075072,
        assistantId = "asst_abc123",
        threadId = "thread_abc123",
        status = "completed",
        startedAt = Some(1699075072),
        expiresAt = None,
        cancelledAt = None,
        failedAt = None,
        completedAt = Some(1699075073),
        lastError = None,
        model = "gpt-3.5-turbo",
        instructions = None,
        tools = Seq(CodeInterpreterTool),
        toolResources = Some(ToolResources(Some(CodeInterpreterToolResource(Some(Seq("file-abc123", "file-abc456")))))),
        metadata = Map.empty,
        usage = Some(Usage(promptTokens = 123, completionTokens = 456, totalTokens = 579))
      )

    // when
    val givenResponse: Either[Exception, RunData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given retrieve run step response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunStepData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunStepData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonRetrieveRunStepResponse
    val expectedResponse: RunStepData =
      RunStepData(
        id = "step_abc123",
        `object` = "thread.run.step",
        createdAt = 1699063291,
        runId = "run_abc123",
        assistantId = "asst_abc123",
        threadId = "thread_abc123",
        `type` = "message_creation",
        status = "completed",
        cancelledAt = None,
        completedAt = Some(1699063291),
        expiredAt = None,
        failedAt = None,
        lastError = None,
        stepDetails = MessageCreation(messageId = "msg_abc123"),
        usage = Some(Usage(promptTokens = 123, completionTokens = 456, totalTokens = 579))
      )

    // when
    val givenResponse: Either[Exception, RunStepData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given modify thread run request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = ThreadRunsRequestBody.ModifyRun(
      metadata = Map("user_id" -> "user_abc123")
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.ThreadRunsFixture.jsonModifyRunRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given modify run response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonModifyRunResponse
    val expectedResponse: RunData =
      RunData(
        id = "run_abc123",
        `object` = "thread.run",
        createdAt = 1699075072,
        assistantId = "asst_abc123",
        threadId = "thread_abc123",
        status = "completed",
        startedAt = Some(1699075072),
        expiresAt = None,
        cancelledAt = None,
        failedAt = None,
        completedAt = Some(1699075073),
        lastError = None,
        model = "gpt-3.5-turbo",
        instructions = None,
        tools = Seq(CodeInterpreterTool),
        toolResources = Some(ToolResources(Some(CodeInterpreterToolResource(Some(Seq("file-abc123", "file-abc456")))))),
        metadata = Map("user_id" -> "user_abc123"),
        usage = Some(Usage(promptTokens = 123, completionTokens = 456, totalTokens = 579))
      )

    // when
    val givenResponse: Either[Exception, RunData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given submit tool outputs to run request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = ThreadRunsRequestBody.SubmitToolOutputsToRun(
      Seq(
        ToolOutput(
          toolCallId = Some("call_abc123"),
          output = "28C"
        )
      )
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.ThreadRunsFixture.jsonSubmitToolOutputsRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given submit tool outputs to run response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonSubmitToolOutputsResponse
    val expectedResponse: RunData =
      RunData(
        id = "run_abc123",
        `object` = "thread.run",
        createdAt = 1699075592,
        assistantId = "asst_abc123",
        threadId = "thread_abc123",
        status = "queued",
        startedAt = Some(1699075592),
        expiresAt = Some(1699076192),
        cancelledAt = None,
        failedAt = None,
        completedAt = None,
        lastError = None,
        model = "gpt-4",
        instructions = Some("You tell the weather."),
        tools = Seq(
          FunctionTool(
            name = "get_weather",
            description = "Determine weather in my location",
            parameters = Map(
              "type" -> Str("object"),
              "properties" -> Obj(
                "location" -> Obj(
                  "type" -> Str("string"),
                  "description" -> Str("The city and state e.g. San Francisco, CA")
                ),
                "unit" -> Obj(
                  "type" -> Str("string"),
                  "enum" -> Arr(Str("c"), Str("f"))
                )
              ),
              "required" -> Arr(Str("location"))
            )
          )
        ),
        toolResources = None,
        metadata = Map.empty,
        usage = None
      )

    // when
    val givenResponse: Either[Exception, RunData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given cancel run response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData
    import sttp.openai.requests.threads.runs.ThreadRunsResponseData.RunData._

    // given
    val jsonResponse = fixtures.ThreadRunsFixture.jsonCancelRunResponse
    val expectedResponse: RunData =
      RunData(
        id = "run_abc123",
        `object` = "thread.run",
        createdAt = 1699076126,
        threadId = "thread_abc123",
        assistantId = "asst_abc123",
        status = "cancelling",
        startedAt = Some(1699076126),
        expiresAt = Some(1699076726),
        cancelledAt = None,
        failedAt = None,
        completedAt = None,
        lastError = None,
        model = "gpt-4",
        instructions = Some("You summarize books."),
        tools = Seq(FileSearchTool),
        toolResources = None,
        metadata = Map.empty,
        usage = None
      )

    // when
    val givenResponse: Either[Exception, RunData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
