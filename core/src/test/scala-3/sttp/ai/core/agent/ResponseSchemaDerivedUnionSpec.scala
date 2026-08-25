package sttp.ai.core.agent

import io.circe.syntax.*
import io.circe.{Codec, Json}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema

import scala.compiletime.testing.typeCheckErrors

object ResponseSchemaDerivedUnionSpec {
  final case class Refund(orderId: String) derives Codec.AsObject, Schema
  final case class Complaint(topic: String) derives Codec.AsObject, Schema
  final case class GeneralQuery() derives Codec.AsObject, Schema

  type Intent = Refund | Complaint | GeneralQuery
}

class ResponseSchemaDerivedUnionSpec extends AnyFlatSpec with Matchers with OptionValues {
  import ResponseSchemaDerivedUnionSpec.*

  behavior of "ResponseSchema.derivedUnion"

  it should "derive the same wire shape as ResponseSchema.oneOf" in {
    val rs: ResponseSchema[Refund | Complaint | GeneralQuery] = ResponseSchema.derivedUnion[Refund | Complaint | GeneralQuery]
    val json = sttp.apispec.circe.encoderSchema(rs.schema).deepDropNullValues
    json.hcursor.get[String]("type") shouldBe Right("object")
    val variants = json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value
    val kinds = variants.flatMap(_.hcursor.downField("properties").downField("kind").downField("enum").as[Vector[String]].toOption).flatten
    kinds.toSet shouldBe Set("Refund", "Complaint", "GeneralQuery")
  }

  it should "decode into the union and support an exhaustive match" in {
    val rs = ResponseSchema.derivedUnion[Refund | Complaint | GeneralQuery]
    val decoded = rs.codec.decodeJson(Json.obj("result" -> Json.obj("kind" -> "Complaint".asJson, "topic" -> "slow".asJson))).toOption.value
    val summary = decoded match {
      case r: Refund       => s"refund:${r.orderId}"
      case c: Complaint    => s"complaint:${c.topic}"
      case _: GeneralQuery => "query"
    }
    summary shouldBe "complaint:slow"
  }

  it should "work through a type alias of the union and flatten nested unions" in {
    val viaAlias: ResponseSchema[Intent] = ResponseSchema.derivedUnion[Intent]
    val nested: ResponseSchema[(Refund | Complaint) | GeneralQuery] = ResponseSchema.derivedUnion[(Refund | Complaint) | GeneralQuery]
    viaAlias.codec.decodeJson(Json.obj("result" -> Json.obj("kind" -> "Refund".asJson, "orderId" -> "o-1".asJson))) shouldBe Right(
      Refund("o-1")
    )
    val json = sttp.apispec.circe.encoderSchema(nested.schema).deepDropNullValues
    json.hcursor.downField("properties").downField("result").downField("anyOf").as[Vector[Json]].toOption.value should have size 3
  }

  it should "carry the description overload" in {
    val rs = ResponseSchema.derivedUnion[Refund | Complaint]("Classify the intent")
    rs.description shouldBe Some("Classify the intent")
  }

  behavior of "ResponseSchema.derivedUnion compile-time validation"

  it should "reject a non-union type" in {
    val errors = typeCheckErrors("""
      sttp.ai.core.agent.ResponseSchema.derivedUnion[sttp.ai.core.agent.ResponseSchemaDerivedUnionSpec.Refund]
    """)
    errors.map(_.message).mkString should include("must be a union type")
  }

  it should "reject duplicate members after dealiasing" in {
    val errors = typeCheckErrors("""
      type R2 = sttp.ai.core.agent.ResponseSchemaDerivedUnionSpec.Refund
      sttp.ai.core.agent.ResponseSchema.derivedUnion[sttp.ai.core.agent.ResponseSchemaDerivedUnionSpec.Refund | R2]
    """)
    errors.map(_.message).mkString should include("duplicate union member")
  }

  it should "reject distinct union members sharing a simple name at compile time" in {
    val errors = typeCheckErrors("""
      object Billing { case class Refund(amount: Int) }
      object Shipping { case class Refund(orderId: String) }
      sttp.ai.core.agent.ResponseSchema.derivedUnion[Billing.Refund | Shipping.Refund]
    """)
    errors.map(_.message).mkString should include("duplicate variant names across union members")
  }

  it should "reject a member without a given Schema, naming the member" in {
    val errors = typeCheckErrors("""
      class Opaque(val x: Int)
      sttp.ai.core.agent.ResponseSchema.derivedUnion[sttp.ai.core.agent.ResponseSchemaDerivedUnionSpec.Refund | Opaque]
    """)
    val messages = errors.map(_.message).mkString
    messages should include("no given")
    messages should include("Opaque")
  }
}
