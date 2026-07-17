package sttp.ai.openai.json

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.{ExampleSingleValue, Schema, SchemaType}
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.SchemaSupport
import sttp.ai.openai.requests.completions.chat.message.{Content, Message, Tool}
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
}
