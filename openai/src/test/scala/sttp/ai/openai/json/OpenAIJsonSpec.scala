package sttp.ai.openai.json

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.{ExampleSingleValue, Schema, SchemaType}
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.SchemaSupport
import sttp.ai.openai.requests.completions.chat.message.{Content, Message, Tool}
import sttp.ai.openai.requests.responses.{ResponsesModel, ResponsesRequestBody, Tool => RespTool}
import sttp.ai.openai.requests.responses.ResponsesRequestBody.{Format => RequestFormat, TextConfig => RequestTextConfig}
import sttp.client4.StringBody

import scala.collection.immutable.ListMap

/** Regression coverage for the null-drop-vs-schema-fidelity fix in [[OpenAIJson.asJson]]: `deepDropNullValues` is applied to the whole
  * request body (to omit unset `Option` fields), but it also strips null VALUES nested inside JSON arrays, which corrupts tool `parameters`
  * schemas that legitimately contain `null` -- in particular the `null` that `SchemaSupport.normalizeForStrict` adds to an optional
  * property's `enum` so strict mode can accept it as "not provided". The body must preserve `tools[*].function.parameters` verbatim while
  * still dropping unset-field nulls everywhere else.
  */
class OpenAIJsonSpec extends AnyFlatSpec with Matchers with EitherValues {

  private def requestBodyOf(chatBody: ChatBody): String =
    new OpenAI("test-key").createChatCompletion(chatBody).body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected a StringBody request body, got $other")
    }

  "OpenAI.createChatCompletion" should
    "retain the null strict-mode normalization adds to an optional enum property, while dropping unrelated unset-field nulls" in {
      val schema = Schema(SchemaType.Object).copy(
        properties = ListMap(
          "name" -> Schema(SchemaType.String),
          "priority" -> Schema(SchemaType.String).copy(`enum` = Some(List(ExampleSingleValue("low"), ExampleSingleValue("high"))))
        ),
        required = List("name") // "priority" is optional -> normalizeForStrict makes it nullable and appends null to its enum
      )
      val parametersJson = SchemaSupport.normalizeForStrict(schema)

      val tool = Tool.Function(
        name = "set_priority",
        description = Some("Sets the priority"),
        parameters = parametersJson.asObject.map(_.toMap),
        strict = Some(true)
      )

      val chatBody = ChatBody(
        messages = Seq(Message.User(content = Content.TextContent("hi"))),
        model = ChatCompletionModel.GPT4oMini,
        tools = Some(Seq(tool))
        // frequencyPenalty, user, seed, ... are all left as their `None` default: their JSON `null`s should be dropped.
      )

      val json = parse(requestBodyOf(chatBody)).value

      val enumValues = json.hcursor
        .downField("tools")
        .downArray
        .downField("function")
        .downField("parameters")
        .downField("properties")
        .downField("priority")
        .get[List[Json]]("enum")
        .value
      enumValues should contain(Json.Null)

      json.hcursor.downField("frequency_penalty").succeeded shouldBe false
      json.hcursor.downField("user").succeeded shouldBe false
      json.hcursor.downField("tool_choice").succeeded shouldBe false
    }

  it should "omit the parameters key entirely when a Tool.Function has no parameters (capture-leak regression)" in {
    val tool = Tool.Function(name = "no_args_tool", description = Some("Takes no arguments"), parameters = None, strict = Some(true))

    val chatBody = ChatBody(
      messages = Seq(Message.User(content = Content.TextContent("hi"))),
      model = ChatCompletionModel.GPT4oMini,
      tools = Some(Seq(tool))
    )

    val json = parse(requestBodyOf(chatBody)).value

    json.hcursor.downField("tools").downArray.downField("function").downField("parameters").succeeded shouldBe false
  }

  it should "omit the schema key entirely when a ResponseFormat.JsonSchema has no schema (capture-leak regression)" in {
    val chatBody = ChatBody(
      messages = Seq(Message.User(content = Content.TextContent("hi"))),
      model = ChatCompletionModel.GPT4oMini,
      responseFormat =
        Some(ChatRequestBody.ResponseFormat.JsonSchema(name = "my_schema", strict = Some(true), schema = None, description = None))
    )

    val json = parse(requestBodyOf(chatBody)).value

    json.hcursor.downField("response_format").downField("json_schema").downField("schema").succeeded shouldBe false
  }

  it should "merge extraBody entries into the top level of the serialized request" in {
    val chatBody = ChatBody(
      messages = Seq(Message.User(content = Content.TextContent("hi"))),
      model = ChatCompletionModel.GPT4oMini,
      extraBody = Map(
        "guided_json" -> Json.obj("type" -> Json.fromString("object")),
        "top_k" -> Json.fromInt(40)
      )
    )

    val json = parse(requestBodyOf(chatBody)).value

    json.hcursor.downField("guided_json").as[Json].value shouldBe Json.obj("type" -> Json.fromString("object"))
    json.hcursor.downField("top_k").as[Int].value shouldBe 40
    json.hcursor.downField("extra_body").succeeded shouldBe false
  }

  it should "let an extraBody entry override a colliding typed field" in {
    val chatBody = ChatBody(
      messages = Seq(Message.User(content = Content.TextContent("hi"))),
      model = ChatCompletionModel.GPT4oMini,
      temperature = Some(0.2),
      extraBody = Map("temperature" -> Json.fromDoubleOrNull(0.9))
    )

    val json = parse(requestBodyOf(chatBody)).value

    json.hcursor.downField("temperature").as[Double].value shouldBe 0.9
  }

  private def responsesRequestBodyOf(requestBody: ResponsesRequestBody): String =
    new OpenAI("test-key").createModelResponse(requestBody).body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected a StringBody request body, got $other")
    }

  "OpenAI.createModelResponse" should
    "retain the null strict-mode normalization adds to an optional enum property in a flat Responses-API function tool" in {
      val schema = Schema(SchemaType.Object).copy(
        properties = ListMap(
          "name" -> Schema(SchemaType.String),
          "priority" -> Schema(SchemaType.String).copy(`enum` = Some(List(ExampleSingleValue("low"), ExampleSingleValue("high"))))
        ),
        required = List("name") // "priority" is optional -> normalizeForStrict makes it nullable and appends null to its enum
      )
      val parametersJson = SchemaSupport.normalizeForStrict(schema)

      val tool = RespTool.Function(
        name = "set_priority",
        parameters = parametersJson.asObject.map(_.toMap).getOrElse(Map.empty),
        strict = true,
        description = Some("Sets the priority")
      )

      val requestBody = ResponsesRequestBody(
        model = Some(ResponsesModel.GPT4o),
        input = Some(Left("hi")),
        tools = Some(List(tool))
      )

      val json = parse(responsesRequestBodyOf(requestBody)).value

      // Unlike chat's `tools[*].function.parameters`, the Responses API encodes function tools flat: `tools[*].parameters`.
      val enumValues = json.hcursor
        .downField("tools")
        .downArray
        .downField("parameters")
        .downField("properties")
        .downField("priority")
        .get[List[Json]]("enum")
        .value
      enumValues should contain(Json.Null)

      json.hcursor.downField("temperature").succeeded shouldBe false
    }

  it should "retain the enum null at text.format.schema for a strict Responses-API JsonSchema format" in {
    val schema = Schema(SchemaType.Object).copy(
      properties = ListMap(
        "name" -> Schema(SchemaType.String),
        "priority" -> Schema(SchemaType.String).copy(`enum` = Some(List(ExampleSingleValue("low"), ExampleSingleValue("high"))))
      ),
      required = List("name") // "priority" is optional -> normalizeForStrict (triggered by strict = Some(true)) appends null to its enum
    )

    val requestBody = ResponsesRequestBody(
      model = Some(ResponsesModel.GPT4o),
      input = Some(Left("hi")),
      text = Some(
        RequestTextConfig(format =
          Some(RequestFormat.JsonSchema(name = "priority_schema", strict = Some(true), schema = Some(schema), description = None))
        )
      )
    )

    val json = parse(responsesRequestBodyOf(requestBody)).value

    val enumValues = json.hcursor
      .downField("text")
      .downField("format")
      .downField("schema")
      .downField("properties")
      .downField("priority")
      .get[List[Json]]("enum")
      .value
    enumValues should contain(Json.Null)
  }
}
