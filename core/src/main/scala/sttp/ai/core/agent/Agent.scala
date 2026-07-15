package sttp.ai.core.agent

import io.circe.Decoder
import io.circe.parser.decode
import sttp.client4.Backend
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps

class Agent[F[_]](
    agentBackend: AgentBackend[F],
    config: AgentConfig[F]
)(implicit monad: MonadError[F]) {

  private val toolMap = config.userTools.map(t => t.name -> t).toMap

  private val beforeToolCall: ToolCall => F[Unit] = config.beforeToolCall.getOrElse((_: ToolCall) => monad.unit(()))
  private val afterToolCall: ToolCallRecord => F[Unit] = config.afterToolCall.getOrElse((_: ToolCallRecord) => monad.unit(()))

  def run(
      initialPrompt: String
  )(backend: Backend[F]): F[AgentResult[String]] = {
    val initialHistory = ConversationHistory.withInitialPrompt(initialPrompt)

    def loop(history: ConversationHistory, iteration: Int, toolCallRecords: Seq[ToolCallRecord]): F[AgentResult[String]] =
      if (iteration >= config.maxIterations) {
        monad.unit(
          AgentResult(
            finalAnswer = extractFinalAnswer(history),
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

        val response = agentBackend.sendRequest(historyWithMarker, backend)

        response.flatMap { response =>
          if (response.stopReason == StopReason.MaxTokens) {
            monad.unit(
              AgentResult(
                finalAnswer = response.textContent,
                iterations = iteration + 1,
                toolCalls = toolCallRecords,
                finishReason = FinishReason.TokenLimit
              )
            )
          } else if (response.toolCalls.isEmpty) {
            // No tool calls - the agent has produced its final answer, so complete the loop.
            monad.unit(
              AgentResult(
                finalAnswer = response.textContent,
                iterations = iteration + 1,
                toolCalls = toolCallRecords,
                finishReason = FinishReason.NaturalStop
              )
            )
          } else {
            val updatedHistory = history.addAssistantResponse(response.textContent, response.toolCalls)

            runToolCalls(response.toolCalls.toList, iteration + 1, updatedHistory, toolCallRecords).flatMap {
              case (historyWithResults, results) =>
                loop(historyWithResults, iteration + 1, results)
            }
          }
        }
      }

    loop(initialHistory, 0, Seq.empty)
  }

  def runAs[T](
      initialPrompt: String
  )(backend: Backend[F])(implicit r: Decoder[T]): F[AgentResult[Either[AgentParseError, T]]] =
    monad.map(run(initialPrompt)(backend)) { res =>
      val parsed: Either[AgentParseError, T] =
        decode[T](res.finalAnswer).left.map(e => AgentParseError(res.finalAnswer, e))
      AgentResult(parsed, res.iterations, res.toolCalls, res.finishReason)
    }

  private def runToolCalls(
      toolCalls: List[ToolCall],
      iteration: Int,
      history: ConversationHistory,
      results: Seq[ToolCallRecord]
  ): F[(ConversationHistory, Seq[ToolCallRecord])] =
    toolCalls match {
      case Nil              => monad.unit((history, results))
      case toolCall :: rest =>
        for {
          _ <- beforeToolCall(toolCall)
          result <- runToolCall(toolCall, iteration)
          _ <- afterToolCall(result)
          updatedHistory = history.addToolResult(result)
          acc <- runToolCalls(rest, iteration, updatedHistory, results :+ result)
        } yield acc
    }

  private def runToolCall(
      toolCall: ToolCall,
      iteration: Int
  ): F[ToolCallRecord] = {
    val output = toolMap.get(toolCall.toolName) match {
      case Some(tool) => executeTool(tool, toolCall)
      case None       => monad.unit(s"Tool not found: ${toolCall.toolName}")
    }

    output.map { result =>
      ToolCallRecord(
        id = toolCall.id,
        toolName = toolCall.toolName,
        input = toolCall.input,
        output = result,
        iteration = iteration
      )
    }
  }

  private def executeTool[T](tool: AgentTool[F, T], toolCall: ToolCall): F[String] =
    monad
      .eval(decode[T](toolCall.input)(tool.codec).fold(throw _, identity))
      .map[Either[String, T]](Right(_))
      .handleError { case parseException: Exception =>
        config.exceptionHandler.handleParseError(toolCall.toolName, toolCall.input, parseException) match {
          case Left(errorMessage) => monad.unit(Left(errorMessage))
          case Right(ex)          => monad.error(ex)
        }
      }
      .flatMap {
        case Left(errorMessage) => monad.unit(errorMessage)
        case Right(typedInput)  =>
          tool.execute(typedInput).handleError { case e: Exception =>
            config.exceptionHandler.handleToolException(toolCall.toolName, e) match {
              case Left(errorMessage) => monad.unit(errorMessage)
              case Right(ex)          => monad.error(ex)
            }
          }
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
      config: AgentConfig[F]
  )(implicit monad: MonadError[F]): Agent[F] =
    new Agent[F](agentBackend, config)(monad)
}
