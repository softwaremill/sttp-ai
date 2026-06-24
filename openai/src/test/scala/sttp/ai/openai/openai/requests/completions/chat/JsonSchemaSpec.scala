package sttp.ai.openai.requests.completions.chat

import io.circe.parser.parse
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

import cats.implicits.catsSyntaxOptionId
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.{Schema, SchemaType}
import sttp.ai.openai.fixtures
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ResponseFormat
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ResponseFormat.JsonSchema

import scala.collection.immutable.ListMap

class JsonSchemaSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given string JSON schema" should "be properly serialized to Json" in {
    val schema = Schema(SchemaType.String)

    val jsonStringSchema = parse(fixtures.JsonSchemaFixture.stringSchema).value

    val serializedSchema = (JsonSchema("testString", true.some, schema.some, "description".some): ResponseFormat).asJson.deepDropNullValues

    serializedSchema shouldBe jsonStringSchema
  }

  "Given string JSON schema without strict field" should "be properly serialized to Json" in {
    val schema = Schema(SchemaType.String)

    val jsonStringSchema = parse(fixtures.JsonSchemaFixture.stringSchemaWithoutStrictField).value

    val serializedSchema = (JsonSchema("testString", None, schema.some, None): ResponseFormat).asJson.deepDropNullValues

    serializedSchema shouldBe jsonStringSchema
  }

  "Given number JSON schema" should "be properly serialized to Json" in {
    val schema = Schema(SchemaType.Number)

    val jsonNumberSchema = parse(fixtures.JsonSchemaFixture.numberSchema).value

    val serializedSchema = (JsonSchema("testNumber", true.some, schema.some, None): ResponseFormat).asJson.deepDropNullValues

    serializedSchema shouldBe jsonNumberSchema
  }

  "Given object JSON schema" should "be properly serialized to Json" in {
    val schema = Schema(SchemaType.Object)
      .copy(properties = ListMap("foo" -> Schema(SchemaType.String), "bar" -> Schema(SchemaType.Number)))

    val jsonObjectSchema = parse(fixtures.JsonSchemaFixture.objectSchema).value

    val serializedSchema = (JsonSchema("testObject", true.some, schema.some, None): ResponseFormat).asJson.deepDropNullValues

    serializedSchema shouldBe jsonObjectSchema
  }

  "Given array JSON schema" should "be properly serialized to Json" in {
    val schema = Schema(SchemaType.Array).copy(items = Some(Schema(SchemaType.String)))

    val jsonArraySchema = parse(fixtures.JsonSchemaFixture.arraySchema).value

    val serializedSchema = (JsonSchema("testArray", true.some, schema.some, None): ResponseFormat).asJson.deepDropNullValues

    serializedSchema shouldBe jsonArraySchema
  }
}
