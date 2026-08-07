package sttp.ai.core.agent

/** Position of the current request within the agent loop.
  *
  * @param iteration
  *   1-based index of the loop iteration this request belongs to
  * @param maxIterations
  *   the configured maximum number of iterations
  * @param forcedFinal
  *   true when an [[AgentInterceptor]] forced this iteration to be the final one via [[LoopDecision.FinishNow]] (e.g. a budget breach)
  */
final case class IterationInfo(iteration: Int, maxIterations: Int, forcedFinal: Boolean = false) {

  /** True on any final iteration where tools are withheld and the model must produce a final answer — whether the iteration cap was reached
    * or an interceptor forced the finish ([[forcedFinal]]). Note that the loop cannot know in advance which iteration produces the final
    * answer when the model stops naturally — this flags only forced final iterations.
    */
  def isLastIteration: Boolean = iteration >= maxIterations || forcedFinal
}
