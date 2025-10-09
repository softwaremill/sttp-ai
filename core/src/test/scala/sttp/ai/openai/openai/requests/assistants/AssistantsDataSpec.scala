package sttp.openai.requests.assistants

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.IsOption._
import sttp.openai.fixtures
import sttp.openai.json.SnakePickle
import sttp.openai.requests.assistants.Tool.{CodeInterpreterTool, FileSearchTool}
import sttp.openai.requests.completions.chat.message.ToolResource.FileSearchToolResource
import sttp.openai.requests.completions.chat.message.ToolResources
import sttp.openai.utils.JsonUtils

class AssistantsDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create assistant request" should "be properly serialized to Json" in {
    // given
    val givenRequest = AssistantsRequestBody.CreateAssistantBody(
      instructions = Some("You are a personal math tutor. When asked a question, write and run Python code to answer the question."),
      name = Some("Math Tutor"),
      tools = Seq(CodeInterpreterTool),
      model = AssistantsModel.GPT4,
      reasoningEffort = Some(ReasoningEffort.Low),
      temperature = Some(1.0f),
      topP = Some(1.0f)
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.AssistantsFixture.jsonCreateAssistantRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.assistants.AssistantsResponseData.AssistantData._
    import sttp.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonCreateAssistantResponse
    val expectedResponse: AssistantData = AssistantData(
      id = "asst_abc123",
      `object` = "assistant",
      createdAt = 1698984975,
      name = Some("Math Tutor"),
      description = None,
      model = AssistantsModel.GPT4,
      instructions = Some("You are a personal math tutor. When asked a question, write and run Python code to answer the question."),
      tools = Seq(
        CodeInterpreterTool
      ),
      toolResources = None,
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, AssistantData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    val json = givenResponse.value
    json shouldBe expectedResponse
  }

  "Given list assistants response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.assistants.AssistantsResponseData.ListAssistantsResponse._
    import sttp.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonListAssistantsResponse
    val expectedResponse: ListAssistantsResponse = ListAssistantsResponse(
      `object` = "list",
      data = Seq(
        AssistantData(
          id = "asst_abc123",
          `object` = "assistant",
          createdAt = 1698982736,
          name = Some("Coding Tutor"),
          description = None,
          AssistantsModel.GPT4,
          instructions = Some("You are a helpful assistant designed to make me better at coding!"),
          tools = Seq(),
          toolResources = None,
          metadata = Map.empty
        ),
        AssistantData(
          id = "asst_abc456",
          `object` = "assistant",
          createdAt = 1698982718,
          name = Some("My Assistant"),
          description = None,
          AssistantsModel.GPT4,
          instructions = Some("You are a helpful assistant designed to make me better at coding!"),
          tools = Seq(),
          toolResources = None,
          metadata = Map.empty
        ),
        AssistantData(
          id = "asst_abc789",
          `object` = "assistant",
          createdAt = 1698982643,
          name = None,
          description = None,
          AssistantsModel.GPT4,
          instructions = None,
          tools = Seq(),
          toolResources = None,
          metadata = Map.empty
        )
      ),
      firstId = "asst_abc123",
      lastId = "asst_abc789",
      hasMore = false
    )

    // when
    val givenResponse: Either[Exception, ListAssistantsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list assistant files response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.assistants.AssistantsResponseData.ListAssistantsResponse._
    import sttp.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonListAssistantsResponse
    val expectedResponse: ListAssistantsResponse = ListAssistantsResponse(
      `object` = "list",
      data = Seq(
        AssistantData(
          id = "asst_abc123",
          `object` = "assistant",
          createdAt = 1698982736,
          name = Some("Coding Tutor"),
          description = None,
          AssistantsModel.GPT4,
          instructions = Some("You are a helpful assistant designed to make me better at coding!"),
          tools = Seq(),
          toolResources = None,
          metadata = Map.empty
        ),
        AssistantData(
          id = "asst_abc456",
          `object` = "assistant",
          createdAt = 1698982718,
          name = Some("My Assistant"),
          description = None,
          AssistantsModel.GPT4,
          instructions = Some("You are a helpful assistant designed to make me better at coding!"),
          tools = Seq(),
          toolResources = None,
          metadata = Map.empty
        ),
        AssistantData(
          id = "asst_abc789",
          `object` = "assistant",
          createdAt = 1698982643,
          name = None,
          description = None,
          AssistantsModel.GPT4,
          instructions = None,
          tools = Seq(),
          toolResources = None,
          metadata = Map.empty
        )
      ),
      firstId = "asst_abc123",
      lastId = "asst_abc789",
      hasMore = false
    )

    // when
    val givenResponse: Either[Exception, ListAssistantsResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given retrieve assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.assistants.AssistantsResponseData.AssistantData._
    import sttp.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonRetrieveAssistantResponse
    val expectedResponse: AssistantData = AssistantData(
      id = "asst_abc123",
      `object` = "assistant",
      createdAt = 1699009709,
      name = Some("HR Helper"),
      description = None,
      AssistantsModel.GPT4,
      instructions = Some("You are an HR bot, and you have access to files to answer employee questions about company policies."),
      tools = Seq(
        FileSearchTool
      ),
      toolResources = Some(ToolResources(None, Some(FileSearchToolResource(Some(Seq("vs_1")))))),
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, AssistantData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given modify assistant request" should "be properly serialized to Json" in {
    // given
    val givenRequest = AssistantsRequestBody.ModifyAssistantBody(
      instructions = Some(
        "You are an HR bot, and you have access to files to answer employee questions about company policies. Always response with info from either of the files."
      ),
      tools = Seq(FileSearchTool),
      model = Some("gpt-4"),
      toolResources = Some(ToolResources(None, Some(FileSearchToolResource(Some(Seq("vs_1", "vs_3")))))),
      reasoningEffort = Some(ReasoningEffort.Low),
      temperature = Some(1.0f),
      topP = Some(1.0f)
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.AssistantsFixture.jsonModifyAssistantRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given modify assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.assistants.AssistantsResponseData.AssistantData._
    import sttp.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonModifyAssistantResponse
    val expectedResponse: AssistantData = AssistantData(
      id = "asst_abc123",
      `object` = "assistant",
      createdAt = 1699009709,
      name = Some("HR Helper"),
      description = None,
      AssistantsModel.GPT4,
      instructions = Some(
        "You are an HR bot, and you have access to files to answer employee questions about company policies. Always response with info from either of the files."
      ),
      tools = Seq(FileSearchTool),
      toolResources = Some(ToolResources(None, Some(FileSearchToolResource(Some(Seq("vs_1", "vs_2")))))),
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, AssistantData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given delete assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.assistants.AssistantsResponseData.DeleteAssistantResponse._
    import sttp.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonDeleteAssistantResponse
    val expectedResponse: DeleteAssistantResponse = DeleteAssistantResponse(
      id = "asst_abc123",
      `object` = "assistant.deleted",
      deleted = true
    )

    // when
    val givenResponse: Either[Exception, DeleteAssistantResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
