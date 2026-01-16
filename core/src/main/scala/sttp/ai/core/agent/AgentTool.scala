package sttp.ai.core.agent

import sttp.apispec.Schema
import sttp.ai.core.json.SnakePickle
import sttp.tapir.{Schema => TapirSchema}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import ujson.Value

trait AgentTool[T] {
  def name: String
  def description: String
  def jsonSchema: Schema
  def readWriter: SnakePickle.ReadWriter[T]
  def execute(input: T): String
}

object AgentTool {
  def fromFunction[T](
      toolName: String,
      toolDescription: String
  )(f: T => String)(implicit tapirSchema: TapirSchema[T], toolReadWriter: SnakePickle.ReadWriter[T]): AgentTool[T] =
    new AgentTool[T] {
      override def name: String = toolName
      override def description: String = toolDescription
      override def jsonSchema: Schema =
        TapirSchemaToJsonSchema(tapirSchema, markOptionsAsNullable = true)
      override def readWriter: SnakePickle.ReadWriter[T] = toolReadWriter
      override def execute(input: T): String = f(input)
    }

  def dynamic(
      toolName: String,
      toolDescription: String,
      toolSchema: Schema
  )(f: Map[String, Value] => String)(implicit toolReadWriter: SnakePickle.ReadWriter[Map[String, Value]]): AgentTool[Map[String, Value]] =
    new AgentTool[Map[String, Value]] {
      override def name: String = toolName
      override def description: String = toolDescription
      override def jsonSchema: Schema = toolSchema
      override def readWriter: SnakePickle.ReadWriter[Map[String, Value]] = toolReadWriter
      override def execute(input: Map[String, Value]): String = f(input)
    }
}
