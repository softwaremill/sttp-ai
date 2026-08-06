package sttp.ai.core.agent

/** Position of the current request within the agent loop.
  *
  * @param iteration
  *   1-based index of the loop iteration this request belongs to
  * @param maxIterations
  *   the configured maximum number of iterations
  */
final case class IterationInfo(iteration: Int, maxIterations: Int) {

  /** True on the forced-final iteration, where tools are withheld and the model must produce a final answer. Note that the loop cannot know
    * in advance which iteration produces the final answer when the model stops naturally — this flags only the forced last iteration.
    */
  def isLastIteration: Boolean = iteration >= maxIterations
}
