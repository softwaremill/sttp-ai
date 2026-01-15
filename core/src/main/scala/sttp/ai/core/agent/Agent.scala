package sttp.ai.core.agent

import sttp.client4.Backend
import sttp.monad.MonadError
import sttp.ai.core.json.SnakePickle

class Agent[F[_]](
    agentBackend: AgentBackend[F],
    config: AgentConfig
)(implicit monad: MonadError[F]) {

  private val allTools = config.userTools ++ AgentConfig.systemTools
  private val toolMap = allTools.map(t => t.name -> t).toMap

  def run(
      initialPrompt: String
  )(backend: Backend[F]): F[AgentResult[String]] = {
    val initialHistory = ConversationHistory.withInitialPrompt(initialPrompt)

    def loop(history: ConversationHistory, iteration: Int, toolCallRecords: Seq[ToolCallRecord]): F[AgentResult[String]] =
      if (iteration >= config.maxIterations) {
        val finalAnswer = extractFinalAnswer(history)
        monad.unit(
          AgentResult(
            finalAnswer = finalAnswer,
            iterations = iteration,
            toolCalls = toolCallRecords,
            finishReason = FinishReason.MaxIterations
          )
        )
      } else {
        val historyWithMarker = if (iteration > 0) {
          history.addIterationMarker(iteration + 1, config.maxIterations)
        } else {
          history
        }

        val responseFuture = agentBackend.sendRequest(historyWithMarker, backend)

        monad.flatMap(responseFuture) { response =>
          if (response.toolCalls.isEmpty) {
            val finishReason = mapStopReason(response.stopReason)

            monad.unit(
              AgentResult(
                finalAnswer = response.textContent,
                iterations = iteration + 1,
                toolCalls = toolCallRecords,
                finishReason = finishReason
              )
            )
          } else {
            val updatedHistory = history.addAssistantResponse(response.textContent, response.toolCalls)

            val toolResults = response.toolCalls.map { toolCall =>
              val tool = toolMap.get(toolCall.toolName)
              val result: String = tool match {
                case Some(t) =>
                  executeTool(t, toolCall) match {
                    case Right(successResult) => successResult
                    case Left(errorForLLM)    => errorForLLM
                  }
                case None =>
                  s"Tool not found: ${toolCall.toolName}"
              }

              val record = ToolCallRecord(
                toolName = toolCall.toolName,
                input = toolCall.input,
                output = result,
                iteration = iteration + 1
              )

              (toolCall, result, record)
            }

            val finishToolResult = toolResults.find { case (toolCall, _, _) =>
              toolCall.toolName == FinishTool.ToolName
            }

            finishToolResult match {
              case Some((_, result, _)) =>
                monad.unit(
                  AgentResult(
                    finalAnswer = result,
                    iterations = iteration + 1,
                    toolCalls = toolCallRecords ++ toolResults.map(_._3),
                    finishReason = FinishReason.ToolFinish
                  )
                )

              case None =>
                val historyWithResults = toolResults.foldLeft(updatedHistory) { case (hist, (toolCall, result, _)) =>
                  hist.addToolResult(toolCall.id, toolCall.toolName, result)
                }

                loop(historyWithResults, iteration + 1, toolCallRecords ++ toolResults.map(_._3))
            }
          }
        }
      }

    loop(initialHistory, 0, Seq.empty)
  }

  private def executeTool[T](tool: AgentTool[T], toolCall: ToolCall): Either[String, String] = {
    val parseResult: Either[Exception, T] =
      try
        Right(SnakePickle.read[T](toolCall.input)(tool.readWriter))
      catch {
        case e: Exception => Left(e)
      }

    parseResult match {
      case Left(parseException) =>
        config.exceptionHandler.handleParseError(
          toolCall.toolName,
          toolCall.input,
          parseException
        ) match {
          case Left(errorMessage) => Left(errorMessage)
          case Right(ex)          => throw ex
        }

      case Right(typedInput) =>
        try
          Right(tool.execute(typedInput))
        catch {
          case e: Exception =>
            config.exceptionHandler.handleToolException(toolCall.toolName, e) match {
              case Left(errorMessage) => Left(errorMessage)
              case Right(ex)          => throw ex
            }
        }
    }
  }

  private def mapStopReason(stopReason: StopReason): FinishReason =
    stopReason match {
      case StopReason.MaxTokens     => FinishReason.TokenLimit
      case StopReason.ContentFilter => FinishReason.Error("Content filtered")
      case StopReason.Other(reason) => FinishReason.Error(s"Unknown stop reason: $reason")
      case _                        => FinishReason.NaturalStop
    }

  private def extractFinalAnswer(history: ConversationHistory): String =
    history.entries.reverseIterator
      .collectFirst {
        case ConversationEntry.AssistantResponse(content, _) if content.nonEmpty => content
        case ConversationEntry.ToolResult(_, _, result)                          => result
      }
      .getOrElse("No answer available")
}

object Agent {
  def apply[F[_]](
      agentBackend: AgentBackend[F],
      config: AgentConfig
  )(implicit monad: MonadError[F]): Agent[F] =
    new Agent[F](agentBackend, config)(monad)
}
