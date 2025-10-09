package sttp.openai.json

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.json.SerializationHelpersSpec.TestObject
import ujson._

object SerializationHelpersSpec {
  sealed trait Base
  @upickle.implicits.key("test_object_key")
  case class TestObject(name: String, value: Int, optionalField: Option[String] = None) extends Base
}

class SerializationHelpersSpec extends AnyFlatSpec with Matchers {

  "withFlattenedDiscriminator" should "add discriminator field when writing object" in {
    // given
    val testObj = TestObject("test", 42, Some("optional"))
    implicit val testObjectW: SnakePickle.Writer[TestObject] = SnakePickle.macroW

    // when
    val serializedJson = SnakePickle.writeJs(testObj)

    // then
    val expectedJson = Obj(
      "type" -> Str("test_object_key"),
      "name" -> Str("test"),
      "value" -> Num(42),
      "optional_field" -> Str("optional")
    )
    serializedJson shouldBe expectedJson
  }

  it should "remove discriminator field when reading object" in {
    // given
    val inputJson = Obj(
      "type" -> Str("test_object_key"),
      "name" -> Str("test"),
      "value" -> Num(42),
      "optional_field" -> Str("optional")
    )
    implicit val testObjectR: SnakePickle.Reader[TestObject] = SnakePickle.macroR

    // when
    val deserializedObj = SnakePickle.read[TestObject](inputJson)

    // then
    val expectedObj = TestObject("test", 42, Some("optional"))
    deserializedObj shouldBe expectedObj
  }

  "withNestedDiscriminatorWriter" should "produce writer that wraps object in nested discriminator structure" in {
    // given
    val testObj = TestObject("nested_test", 123, Some("value"))
    implicit val baseWriter: SnakePickle.Writer[TestObject] = SnakePickle.macroW
    val nestedWriter = SerializationHelpers.withNestedDiscriminatorWriter[TestObject]("test_object_key", "custom_field")

    // when
    val serializedJson = SnakePickle.writeJs(testObj)(nestedWriter)

    // then
    val expectedJson = Obj(
      "type" -> Str("test_object_key"),
      "custom_field" -> Obj(
        "name" -> Str("nested_test"),
        "value" -> Num(123),
        "optional_field" -> Str("value")
      )
    )
    serializedJson shouldBe expectedJson
  }

  "withNestedDiscriminatorReader" should "produce reader that extracts object from nested discriminator structure" in {
    // given
    val inputJson = Obj(
      "type" -> Str("test_object_key"),
      "json_schema" -> Obj(
        "name" -> Str("reader_test"),
        "value" -> Num(789),
        "optional_field" -> Str("extracted")
      )
    )
    implicit val baseReader: SnakePickle.Reader[TestObject] = SnakePickle.macroR
    val nestedReader = SerializationHelpers.withNestedDiscriminatorReader[TestObject]("test_object_key", "json_schema")

    // when
    val deserializedObj = SnakePickle.read[TestObject](inputJson)(nestedReader)

    // then
    val expectedObj = TestObject("reader_test", 789, Some("extracted"))
    deserializedObj shouldBe expectedObj
  }

}
