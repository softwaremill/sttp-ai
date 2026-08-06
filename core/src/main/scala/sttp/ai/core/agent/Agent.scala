package sttp.ai.core.agent

import io.circe.Decoder
import io.circe.parser.decode
import sttp.client4.Backend
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps

import scala.annotation.nowarn

class Agent[F[_]](
    agentBackend: AgentBackend[F],
    config: AgentConfig[F]
)(implicit monad: MonadError[F]) {

  private val toolMap = config.userTools.map(t => t.name -> t).toMap

  // Adapts the deprecated beforeToolCall/afterToolCall hooks into an interceptor, appended AFTER user
  // interceptors so hooks run innermost (closest to the tool call), preserving pre-interceptor behavior.
  @nowarn("cat=deprecation")
  private val legacyHookInterceptor: Option[AgentInterceptor[F]] =
    if (config.beforeToolCall.isEmpty && config.afterToolCall.isEmpty) None
    else
      Some(new AgentInterceptor[F] {
        override def aroundToolCall(ctx: ToolCallContext)(next: => F[ToolCallRecord]): F[ToolCallRecord] = {
          val before = config.beforeToolCall.map(_(ctx.toolCall)).getOrElse(monad.unit(()))
          before.flatMap { _ =>
            next.flatMap { record =>
              config.afterToolCall.map(_(record)).getOrElse(monad.unit(())).map(_ => record)
            }
          }
        }
      })

  private val interceptor: AgentInterceptor[F] =
    AgentInterceptor.compose(config.interceptors ++ legacyHookInterceptor.toSeq)

  private sealed trait IterationOutcome
  private final case class ContinueLoop(
      history: ConversationHistory,
      records: Seq[ToolCallRecord],
      usage: TokenUsage,
      llmCalls: Seq[LlmCallUsage]
  ) extends IterationOutcome
  private final case class Finished(result: AgentResult[String]) extends IterationOutcome

  def run(
      initialPrompt: String
  )(backend: Backend[F]): F[AgentResult[String]] = {
    val initialHistory = ConversationHistory.withInitialPrompt(initialPrompt)

    def loop(
        history: ConversationHistory,
        iteration: Int,
        records: Seq[ToolCallRecord],
        usage: TokenUsage,
        llmCalls: Seq[LlmCallUsage]
    ): F[AgentResult[String]] =
      // Safety net for maxIterations <= 0. For maxIterations >= 1 this is unreachable: the final-iteration branch
      // below always returns at iteration == maxIterations - 1, so the loop never recurses past it.
      if (iteration >= config.maxIterations) {
        monad.unit(
          AgentResult(extractFinalAnswer(history), iteration, records, FinishReason.MaxIterations, usage, llmCalls)
        )
      } else {
        val decision = interceptor.decide(AgentRunState(iteration, config.maxIterations, usage, llmCalls))
        val info = IterationInfo(iteration + 1, config.maxIterations)
        interceptor
          .aroundIteration(IterationContext(info))(runIteration(history, iteration, records, usage, llmCalls, decision, backend))
          .flatMap {
            case ContinueLoop(h, r, u, lc) => loop(h, iteration + 1, r, u, lc)
            case Finished(result)          => monad.unit(result)
          }
      }

    loop(initialHistory, 0, Seq.empty, TokenUsage.Zero, Seq.empty)
  }

  private def runIteration(
      history: ConversationHistory,
      iteration: Int,
      records: Seq[ToolCallRecord],
      usage: TokenUsage,
      llmCalls: Seq[LlmCallUsage],
      decision: LoopDecision,
      backend: Backend[F]
  ): F[IterationOutcome] = {
    val isLastByCount = iteration == config.maxIterations - 1
    val forcedCause: Option[FinishReason] = decision match {
      case LoopDecision.FinishNow(cause, _) => Some(cause)
      case LoopDecision.Continue            => None
    }
    val isFinalIteration = isLastByCount || forcedCause.nonEmpty

    val withMarker = if (iteration > 0) history.addIterationMarker(iteration + 1, config.maxIterations) else history
    val requestHistory = decision match {
      case LoopDecision.FinishNow(_, instruction) => withMarker.addUserPrompt(instruction)
      case LoopDecision.Continue                  => withMarker
    }

    val info = IterationInfo(iteration + 1, config.maxIterations)
    val includeTools = !isFinalIteration

    interceptor
      .aroundLlmCall(LlmCallContext(requestHistory, includeTools, info))(
        agentBackend.sendRequest(requestHistory, backend, includeTools, info)
      )
      .flatMap { response =>
        val callUsage = response.usage.getOrElse(TokenUsage.Zero)
        val newUsage = usage + callUsage
        val newLlmCalls = llmCalls :+ LlmCallUsage(response.model, callUsage)

        if (response.stopReason == StopReason.MaxTokens) {
          monad.unit(
            Finished(AgentResult(response.textContent, iteration + 1, records, FinishReason.TokenLimit, newUsage, newLlmCalls))
          )
        } else if (isFinalIteration) {
          // Tools were not offered on the final iteration, so any tool calls in the response are spurious and must not
          // be executed. Force the final answer: prefer the model's text, fall back to the last tool result / assistant text.
          val finalAnswer = if (response.textContent.nonEmpty) response.textContent else extractFinalAnswer(history)
          // MaxIterations takes precedence when the forced last iteration and a FinishNow coincide.
          val reason = if (isLastByCount) FinishReason.MaxIterations else forcedCause.getOrElse(FinishReason.MaxIterations)
          monad.unit(Finished(AgentResult(finalAnswer, iteration + 1, records, reason, newUsage, newLlmCalls)))
        } else if (response.toolCalls.isEmpty) {
          // No tool calls - the agent has produced its final answer, so complete the loop.
          monad.unit(
            Finished(AgentResult(response.textContent, iteration + 1, records, FinishReason.NaturalStop, newUsage, newLlmCalls))
          )
        } else {
          val updatedHistory = history.addAssistantResponse(response.textContent, response.toolCalls)
          runToolCalls(response.toolCalls.toList, iteration + 1, updatedHistory, records).map { case (h, r) =>
            ContinueLoop(h, r, newUsage, newLlmCalls)
          }
        }
      }
  }

  def runAs[T](
      initialPrompt: String
  )(backend: Backend[F])(implicit r: Decoder[T]): F[AgentResult[Either[AgentFailure, T]]] =
    monad.map(run(initialPrompt)(backend)) { res =>
      val parsed: Either[AgentFailure, T] = res.finishReason match {
        case FinishReason.NaturalStop =>
          decode[T](res.finalAnswer).left.map(e => AgentParseError(res.finalAnswer, e))
        case FinishReason.MaxIterations | FinishReason.BudgetExceeded =>
          // A forced final iteration withholds tools and keeps schema guidance, so the answer may still be valid.
          decode[T](res.finalAnswer).left.map(e => AgentIncomplete(res.finalAnswer, res.finishReason, Some(e)))
        case FinishReason.TokenLimit | FinishReason.Error(_) =>
          Left(AgentIncomplete(res.finalAnswer, res.finishReason, parseError = None))
      }
      AgentResult(parsed, res.iterations, res.toolCalls, res.finishReason, res.usage, res.llmCalls)
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
          result <- interceptor.aroundToolCall(ToolCallContext(toolCall, iteration))(runToolCall(toolCall, iteration))
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
