package sttp.ai.openai.requests.assistants

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures
import sttp.ai.openai.requests.assistants.Tool.{CodeInterpreter, FileSearch}
import sttp.ai.openai.requests.completions.chat.message.ToolResource.FileSearchToolResource
import sttp.ai.openai.requests.completions.chat.message.ToolResources
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

class AssistantsDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create assistant request" should "be properly serialized to Json" in {
    // given
    val givenRequest = AssistantsRequestBody.CreateAssistantBody(
      instructions = Some("You are a personal math tutor. When asked a question, write and run Python code to answer the question."),
      name = Some("Math Tutor"),
      tools = Seq(CodeInterpreter),
      model = AssistantsModel.GPT4,
      reasoningEffort = Some(ReasoningEffort.Low),
      temperature = Some(1.0f),
      topP = Some(1.0f)
    )

    val jsonRequest: io.circe.Json = parse(fixtures.AssistantsFixture.jsonCreateAssistantRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.assistants.AssistantsResponseData.AssistantData._
    import sttp.ai.openai.requests.assistants.AssistantsResponseData._

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
        CodeInterpreter
      ),
      toolResources = None,
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, AssistantData] = decode[AssistantData](jsonResponse)

    // then
    val json = givenResponse.value
    json shouldBe expectedResponse
  }

  "Given list assistants response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.assistants.AssistantsResponseData.ListAssistantsResponse._
    import sttp.ai.openai.requests.assistants.AssistantsResponseData._

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
    val givenResponse: Either[Exception, ListAssistantsResponse] =
      decode[ListAssistantsResponse](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list assistant files response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.assistants.AssistantsResponseData.ListAssistantsResponse._
    import sttp.ai.openai.requests.assistants.AssistantsResponseData._

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
    val givenResponse: Either[Exception, ListAssistantsResponse] =
      decode[ListAssistantsResponse](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given retrieve assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.assistants.AssistantsResponseData.AssistantData._
    import sttp.ai.openai.requests.assistants.AssistantsResponseData._

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
        FileSearch
      ),
      toolResources = Some(ToolResources(None, Some(FileSearchToolResource(Some(Seq("vs_1")))))),
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, AssistantData] = decode[AssistantData](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given modify assistant request" should "be properly serialized to Json" in {
    // given
    val givenRequest = AssistantsRequestBody.ModifyAssistantBody(
      instructions = Some(
        "You are an HR bot, and you have access to files to answer employee questions about company policies. Always response with info from either of the files."
      ),
      tools = Seq(FileSearch),
      model = Some("gpt-4"),
      toolResources = Some(ToolResources(None, Some(FileSearchToolResource(Some(Seq("vs_1", "vs_3")))))),
      reasoningEffort = Some(ReasoningEffort.Low),
      temperature = Some(1.0f),
      topP = Some(1.0f)
    )

    val jsonRequest: io.circe.Json = parse(fixtures.AssistantsFixture.jsonModifyAssistantRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given modify assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.assistants.AssistantsResponseData.AssistantData._
    import sttp.ai.openai.requests.assistants.AssistantsResponseData._

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
      tools = Seq(FileSearch),
      toolResources = Some(ToolResources(None, Some(FileSearchToolResource(Some(Seq("vs_1", "vs_2")))))),
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, AssistantData] = decode[AssistantData](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given delete assistant response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.assistants.AssistantsResponseData.DeleteAssistantResponse._
    import sttp.ai.openai.requests.assistants.AssistantsResponseData._

    // given
    val jsonResponse = fixtures.AssistantsFixture.jsonDeleteAssistantResponse
    val expectedResponse: DeleteAssistantResponse = DeleteAssistantResponse(
      id = "asst_abc123",
      `object` = "assistant.deleted",
      deleted = true
    )

    // when
    val givenResponse: Either[Exception, DeleteAssistantResponse] =
      decode[DeleteAssistantResponse](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
