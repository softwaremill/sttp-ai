package sttp.ai.core.agent

import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import io.circe.{Codec, Json}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema

object ResponseSchemaOneOfSpec {
  sealed trait Intent
  final case class Refund(orderId: String) extends Intent
  final case class Complaint(topic: String, severity: Int) extends Intent
  final case class GeneralQuery() extends Intent

  implicit val refundCodec: Codec[Refund] = deriveCodec
  implicit val refundSchema: Schema[Refund] = Schema.derived
  implicit val complaintCodec: Codec[Complaint] = deriveCodec
  implicit val complaintSchema: Schema[Complaint] = Schema.derived
  implicit val generalQueryCodec: Codec[GeneralQuery] = deriveCodec
  implicit val generalQuerySchema: Schema[GeneralQuery] = Schema.derived

  final case class Item(sku: String)
  final case class OrderIssue(item: Item) extends Intent
  implicit val itemCodec: Codec[Item] = deriveCodec
  implicit val itemSchema: Schema[Item] = Schema.derived
  implicit val orderIssueCodec: Codec[OrderIssue] = deriveCodec
  implicit val orderIssueSchema: Schema[OrderIssue] = Schema.derived

  final case class WithKind(kind: String) extends Intent
  implicit val withKindCodec: Codec[WithKind] = deriveCodec
  implicit val withKindSchema: Schema[WithKind] = Schema.derived
}

class ResponseSchemaOneOfSpec extends AnyFlatSpec with Matchers with OptionValues {
  import ResponseSchemaOneOfSpec._

  private val rs: ResponseSchema[Intent] =
    ResponseSchema.oneOf[Intent](Variant[Refund], Variant[Complaint], Variant[GeneralQuery])

  private def schemaJson(r: ResponseSchema[_]): Json =
    sttp.apispec.circe.encoderSchema(r.schema).deepDropNullValues

  behavior of "ResponseSchema.oneOf"

  it should "render a root object with a required result property holding the anyOf" in {
    val json = schemaJson(rs)
    json.hcursor.get[String]("type") shouldBe Right("object")
    json.hcursor.downField("required").as[Vector[String]] shouldBe Right(Vector("result"))
    val variants = json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value
    variants should have size 3
  }

  it should "give each variant a kind discriminator pinned to the variant name and require it" in {
    val json = schemaJson(rs)
    val variants = json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value
    val kinds = variants.map(_.hcursor.downField("properties").downField("kind").downField("enum").as[Vector[String]].toOption.value)
    kinds shouldBe Vector(Vector("Refund"), Vector("Complaint"), Vector("GeneralQuery"))
    variants.foreach { v =>
      v.hcursor.downField("required").as[Vector[String]].toOption.value should contain("kind")
    }
  }

  it should "keep each variant's own fields required alongside kind" in {
    val json = schemaJson(rs)
    val variants = json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value
    val complaint = variants
      .find(_.hcursor.downField("properties").downField("kind").downField("enum").as[Vector[String]].contains(Vector("Complaint")))
      .value
    complaint.hcursor.downField("required").as[Vector[String]].toOption.value.toSet shouldBe Set("kind", "topic", "severity")
    complaint.hcursor.downField("properties").downField("severity").get[String]("type") shouldBe Right("integer")
  }

  it should "decode each variant by its kind" in {
    rs.codec.decodeJson(Json.obj("result" -> Json.obj("kind" -> "Refund".asJson, "orderId" -> "o-1".asJson))) shouldBe Right(Refund("o-1"))
    rs.codec.decodeJson(
      Json.obj("result" -> Json.obj("kind" -> "Complaint".asJson, "topic" -> "slow".asJson, "severity" -> 2.asJson))
    ) shouldBe Right(Complaint("slow", 2))
    rs.codec.decodeJson(Json.obj("result" -> Json.obj("kind" -> "GeneralQuery".asJson))) shouldBe Right(GeneralQuery())
  }

  it should "fail decoding with the list of valid kinds for an unknown kind" in {
    val res = rs.codec.decodeJson(Json.obj("result" -> Json.obj("kind" -> "Chitchat".asJson)))
    res.isLeft shouldBe true
    res.left.toOption.value.getMessage should (include("Chitchat") and include("Refund") and include("Complaint") and include(
      "GeneralQuery"
    ))
  }

  it should "encode a value back to the wire shape (round-trip)" in {
    val encoded = rs.codec(Refund("o-9"))
    encoded shouldBe Json.obj("result" -> Json.obj("kind" -> "Refund".asJson, "orderId" -> "o-9".asJson))
    rs.codec.decodeJson(encoded) shouldBe Right(Refund("o-9"))
  }

  it should "carry the description overload into the ResponseSchema" in {
    val described = ResponseSchema.oneOf[Intent]("Classify the user's intent")(Variant[Refund], Variant[Complaint], Variant[GeneralQuery])
    described.description shouldBe Some("Classify the user's intent")
    rs.description shouldBe None
  }

  it should "hoist nested case-class schemas into the root $defs and reference them" in {
    val nested = ResponseSchema.oneOf[Intent](Variant[OrderIssue], Variant[Refund])
    val json = schemaJson(nested)
    val variants = json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value
    val orderIssue = variants.head
    val ref = orderIssue.hcursor.downField("properties").downField("item").get[String]("$ref").toOption.value
    ref shouldBe "#/$defs/Item"
    json.hcursor.downField("$defs").downField("Item").downField("properties").keys.value.toSet shouldBe Set("sku")
    orderIssue.hcursor.downField("$defs").succeeded shouldBe false
  }

  it should "decode a variant with a nested case class" in {
    val nested = ResponseSchema.oneOf[Intent](Variant[OrderIssue], Variant[Refund])
    val args = Json.obj("result" -> Json.obj("kind" -> "OrderIssue".asJson, "item" -> Json.obj("sku" -> "S-1".asJson)))
    nested.codec.decodeJson(args) shouldBe Right(OrderIssue(Item("S-1")))
  }

  it should "use the custom label from Variant.named in schema, decode, and encode" in {
    val labeled = ResponseSchema.oneOf[Intent](Variant.named[Refund]("refund_request"), Variant[Complaint])
    val json = schemaJson(labeled)
    val variants = json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value
    variants.head.hcursor.downField("properties").downField("kind").downField("enum").as[Vector[String]] shouldBe Right(
      Vector("refund_request")
    )
    val wire = Json.obj("result" -> Json.obj("kind" -> "refund_request".asJson, "orderId" -> "o-2".asJson))
    labeled.codec.decodeJson(wire) shouldBe Right(Refund("o-2"))
    labeled.codec(Refund("o-2")) shouldBe wire
  }

  it should "reject a non-object variant at construction" in {
    val ex = intercept[IllegalArgumentException] {
      ResponseSchema.oneOf[Any](Variant[String], Variant[Refund])
    }
    ex.getMessage should include("must be object schemas")
  }

  it should "reject a variant that already defines a kind property" in {
    val ex = intercept[IllegalArgumentException] {
      ResponseSchema.oneOf[Intent](Variant[WithKind], Variant[Refund])
    }
    ex.getMessage should include("reserved for the discriminator")
  }

  it should "reject duplicate variant names" in {
    val ex = intercept[IllegalArgumentException] {
      ResponseSchema.oneOf[Intent](Variant[Refund], Variant.named[Complaint]("Refund"))
    }
    ex.getMessage should include("duplicate variant names")
  }

  it should "reject the same class registered twice under different names" in {
    val ex = intercept[IllegalArgumentException] {
      ResponseSchema.oneOf[Intent](Variant[Refund], Variant.named[Refund]("RefundAgain"))
    }
    ex.getMessage should include("duplicate variant classes")
  }
}
