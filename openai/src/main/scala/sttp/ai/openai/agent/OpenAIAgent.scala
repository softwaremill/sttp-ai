package sttp.ai.openai.agent

import sttp.ai.core.agent._
import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.{FunctionCall, ToolCall => OpenAIToolCall}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message, Tool}
import sttp.client4.Backend
import ujson.{Arr, Bool, Obj, Str, Value}

class OpenAIAgent[F[_]](
  openAI: OpenAI, 
  modelName: String,
  tools: Seq[AgentTool],
  systemPrompt: Option[String]
)(implicit monad: sttp.monad.MonadError[F]) 
  extends AgentBackendBase[F](tools, systemPrompt) {

  private val convertedTools: Seq[Tool.FunctionTool] = tools.map(convertTool)

  private def convertTool(tool: AgentTool): Tool.FunctionTool = {
    val properties = Obj()
    val required = Arr()

    tool.parameters.foreach { case (paramName, paramSpec) =>
      val paramObj = Obj("type" -> Str(ParameterType.asString(paramSpec.dataType)))
      paramObj("description") = Str(paramSpec.description)
      
      paramSpec.`enum`.foreach { enumValues =>
        paramObj("enum") = Arr.from(enumValues.map(Str(_)))
      }

      properties(paramName) = paramObj

      if (paramSpec.required) {
        required.arr.addOne(Str(paramName))
        ()
      }
    }

    val parametersMap = Map(
      "type" -> Str("object"),
      "properties" -> properties,
      "required" -> required,
      "additionalProperties" -> Bool(false)
    )

    Tool.FunctionTool(
      name = tool.name,
      description = Some(tool.description),
      parameters = Some(parametersMap),
      strict = Some(false)
    )
  }

  private def buildMessages(history: ConversationHistory): Seq[Message] = {
    val systemMessages = systemPrompt.map { prompt =>
      Message.SystemMessage(content = prompt)
    }.toSeq

    val conversationMessages = history.entries.flatMap {
      case ConversationEntry.UserPrompt(content) =>
        Seq(Message.UserMessage(content = Content.TextContent(content)))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val openaiToolCalls = toolCalls.map { tc =>
          val argsJson = SnakePickle.write(tc.input)
          OpenAIToolCall.FunctionToolCall(
            id = Some(tc.id),
            function = FunctionCall(
              arguments = argsJson,
              name = Some(tc.toolName)
            )
          )
        }
        Seq(Message.AssistantMessage(
          content = content,
          toolCalls = openaiToolCalls
        ))

      case ConversationEntry.ToolResult(toolCallId, _, result) =>
        Seq(Message.ToolMessage(content = result, toolCallId = toolCallId))

      case ConversationEntry.IterationMarker(current, max) =>
        Seq(Message.UserMessage(content = Content.TextContent(s"[Iteration $current of $max]")))
    }

    systemMessages ++ conversationMessages
  }

  override protected def sendApiRequest(
    history: ConversationHistory,
    backend: Backend[F]
  ): F[AgentResponse] = {
    val messages = buildMessages(history)
    val request = ChatBody(
      model = ChatCompletionModel.CustomChatCompletionModel(modelName),
      messages = messages,
      tools = if (convertedTools.nonEmpty) Some(convertedTools) else None
    )
    
    monad.flatMap(monad.map(openAI.createChatCompletion(request).send(backend))(_.body)) {
      case Right(response) => 
        val textContent = response.choices.headOption
          .flatMap(choice => Option(choice.message.content))
          .getOrElse("")
        
        val toolCalls = response.choices.headOption match {
          case Some(choice) =>
            choice.message.toolCalls.zipWithIndex.map { case (toolCall, idx) =>
              toolCall match {
                case OpenAIToolCall.FunctionToolCall(maybeId, function) =>
                  val id = maybeId.getOrElse(s"call_$idx")
                  val inputMap =
                    try {
                      val parsed = ujson.read(function.arguments)
                      parsed.obj.toMap
                    } catch {
                      case _: Exception => Map.empty[String, Value]
                    }
                  ToolCall(
                    id = id,
                    toolName = function.name.getOrElse("unknown"),
                    input = inputMap
                  )
              }
            }
          case None => Seq.empty
        }
        
        val stopReason = response.choices.headOption.map(_.finishReason)
        
        monad.unit(AgentResponse(textContent, toolCalls, stopReason))
      
      case Left(error) => monad.error(
        new RuntimeException(s"OpenAI API error: ${error.getMessage}")
      )
    }
  }
}

object OpenAIAgent {
  def apply[F[_]](
    apiKey: String, 
    modelName: String, 
    tools: Seq[AgentTool],
    systemPrompt: Option[String]
  )(implicit monad: sttp.monad.MonadError[F]): OpenAIAgent[F] =
    new OpenAIAgent[F](new OpenAI(apiKey), modelName, tools, systemPrompt)

  def apply[F[_]](
    openAI: OpenAI, 
    modelName: String, 
    tools: Seq[AgentTool],
    systemPrompt: Option[String]
  )(implicit monad: sttp.monad.MonadError[F]): OpenAIAgent[F] =
    new OpenAIAgent[F](openAI, modelName, tools, systemPrompt)
}
