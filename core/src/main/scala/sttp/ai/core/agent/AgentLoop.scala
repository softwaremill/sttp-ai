package sttp.ai.core.agent

import sttp.client4.Backend
import sttp.ai.core.json.SnakePickle

class AgentLoop[F[_]](
    agentBackend: AgentBackend[F],
    config: AgentConfig
)(implicit monad: sttp.monad.MonadError[F]) {

  private val allTools = config.userTools ++ AgentConfig.systemTools
  private val toolMap = allTools.map(t => t.name -> t).toMap

  def run(
      initialPrompt: String
  )(backend: Backend[F]): F[AgentResult] = {
    val initialHistory = ConversationHistory.withInitialPrompt(initialPrompt)

    def loop(history: ConversationHistory, iteration: Int, toolCallRecords: Seq[ToolCallRecord]): F[AgentResult] =
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

            val toolResults = response.toolCalls.map { toolCall =>
              val tool = toolMap.get(toolCall.toolName)
              val result = tool match {
                case Some(t) =>
                  try
                    t.execute(toolCall.input)
                  catch {
                    case e: Exception =>
                      s"Error executing tool: ${e.getMessage}"
                  }
                case None =>
                  s"Tool not found: ${toolCall.toolName}"
              }

              val inputJson = SnakePickle.write(toolCall.input)
              val record = ToolCallRecord(
                toolName = toolCall.toolName,
                input = inputJson,
                output = result,
                iteration = iteration + 1
              )

              (toolCall, result, record)
            }

            val finishToolResult = toolResults.find { case (toolCall, _, _) =>
              toolCall.toolName == FinishTool.ToolName
            }

            finishToolResult match {
              case Some((_, result, record)) =>
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

  private def extractFinalAnswer(history: ConversationHistory): String =
    history.entries.reverseIterator
      .collectFirst {
        case ConversationEntry.AssistantResponse(content, _) if content.nonEmpty => content
        case ConversationEntry.ToolResult(_, _, result)                          => result
      }
      .getOrElse("No answer available")
}
