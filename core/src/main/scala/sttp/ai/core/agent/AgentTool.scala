package sttp.ai.core.agent

import io.circe.{Codec, Decoder, Encoder, Json}
import sttp.apispec.Schema
import sttp.tapir.{Schema => TapirSchema}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema

trait AgentTool[T] {
  def name: String
  def description: String
  def jsonSchema: Schema
  def codec: Codec[T]
  def execute(input: T): String
}

object AgentTool {
  def fromFunction[T](
      toolName: String,
      toolDescription: String
  )(f: T => String)(implicit tapirSchema: TapirSchema[T], toolCodec: Codec[T]): AgentTool[T] =
    new AgentTool[T] {
      override def name: String = toolName
      override def description: String = toolDescription
      override def jsonSchema: Schema =
        TapirSchemaToJsonSchema(tapirSchema, markOptionsAsNullable = true)
      override def codec: Codec[T] = toolCodec
      override def execute(input: T): String = f(input)
    }

  def dynamic(
      toolName: String,
      toolDescription: String,
      toolSchema: Schema
  )(f: Map[String, Json] => String): AgentTool[Map[String, Json]] =
    new AgentTool[Map[String, Json]] {
      override def name: String = toolName
      override def description: String = toolDescription
      override def jsonSchema: Schema = toolSchema
      override def codec: Codec[Map[String, Json]] = Codec.implied
      override def execute(input: Map[String, Json]): String = f(input)
    }
}
