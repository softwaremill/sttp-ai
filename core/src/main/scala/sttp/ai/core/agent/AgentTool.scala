package sttp.ai.core.agent

import io.circe.{Codec, Json}
import sttp.apispec.Schema
import sttp.shared.Identity
import sttp.tapir.{Schema => TapirSchema}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema

trait AgentTool[F[_], T] {
  def name: String
  def description: String
  def jsonSchema: Schema
  def codec: Codec[T]
  def execute(input: T): F[String]

  /** The tool's input schema as raw JSON. Defaults to the encoding of [[jsonSchema]]; implementations that obtained the schema as JSON in
    * the first place (e.g. tools loaded from MCP servers) should override this with the original document, so backends can pass it through
    * without a lossy round-trip.
    */
  def rawJsonSchema: Json = sttp.apispec.circe.encoderSchema(jsonSchema).deepDropNullValues
}

object AgentTool {

  def fromFunction[T](
      toolName: String,
      toolDescription: String
  )(f: T => String)(implicit tapirSchema: TapirSchema[T], toolCodec: Codec[T]): AgentTool[Identity, T] =
    fromFunctionF[Identity, T](toolName, toolDescription)(f)

  def fromFunctionF[F[_], T](
      toolName: String,
      toolDescription: String
  )(f: T => F[String])(implicit tapirSchema: TapirSchema[T], toolCodec: Codec[T]): AgentTool[F, T] =
    new AgentTool[F, T] {
      override def name: String = toolName
      override def description: String = toolDescription
      override def jsonSchema: Schema =
        TapirSchemaToJsonSchema(tapirSchema, markOptionsAsNullable = true)
      override def codec: Codec[T] = toolCodec
      override def execute(input: T): F[String] = f(input)
    }

  def dynamic(
      toolName: String,
      toolDescription: String,
      toolSchema: Schema
  )(f: Map[String, Json] => String): AgentTool[Identity, Map[String, Json]] =
    dynamicF[Identity](toolName, toolDescription, toolSchema)(f)

  def dynamicF[F[_]](
      toolName: String,
      toolDescription: String,
      toolSchema: Schema
  )(f: Map[String, Json] => F[String]): AgentTool[F, Map[String, Json]] =
    new AgentTool[F, Map[String, Json]] {
      override def name: String = toolName
      override def description: String = toolDescription
      override def jsonSchema: Schema = toolSchema
      override def codec: Codec[Map[String, Json]] = Codec.implied
      override def execute(input: Map[String, Json]): F[String] = f(input)
    }
}
