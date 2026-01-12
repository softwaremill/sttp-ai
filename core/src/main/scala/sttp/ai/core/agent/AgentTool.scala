package sttp.ai.core.agent

import ujson.Value

sealed trait ParameterType

object ParameterType {
  case object String extends ParameterType
  case object Number extends ParameterType
  case object Integer extends ParameterType
  case object Boolean extends ParameterType
  case object Object extends ParameterType
  case object Array extends ParameterType

  def asString(paramType: ParameterType): String = paramType match {
    case String  => "string"
    case Number  => "number"
    case Integer => "integer"
    case Boolean => "boolean"
    case Object  => "object"
    case Array   => "array"
  }
}

case class ParameterSpec(
    dataType: ParameterType,
    description: String,
    required: Boolean = true,
    `enum`: Option[Seq[String]] = None
)

trait AgentTool {
  def name: String
  def description: String
  def parameters: Map[String, ParameterSpec]
  def execute(input: Map[String, Value]): String
}

object AgentTool {
  def apply(
      toolName: String,
      toolDescription: String,
      toolParameters: Map[String, ParameterSpec]
  )(executeFunction: Map[String, Value] => String): AgentTool = new AgentTool {
    override def name: String = toolName
    override def description: String = toolDescription
    override def parameters: Map[String, ParameterSpec] = toolParameters
    override def execute(input: Map[String, Value]): String = executeFunction(input)
  }
}
