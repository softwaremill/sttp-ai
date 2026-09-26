package sttp.ai.jev

import scala.annotation.implicitNotFound

/** An answer to a [[Question]]; the concrete type is fixed by the question's type parameter. */
sealed trait Answer

/** `probability` (0 to 1) that the noul's instructions hold for the state. */
final case class NoulAnswer(probability: Double) extends Answer

/** `choice` is the most probable option; `probabilities` has every option's probability (summing to about 1); `confidence` (0 to 1) is the
  * server's certainty in the answer, derived from the distribution. Invariant in `O` because it keys the map.
  */
final case class ChoiceAnswer[O](choice: O, confidence: Double, probabilities: Map[O, Double]) extends Answer

/** `levels(i)` has probability `probabilities(i)`; `score` is the expected level index (fractional, from 0 to `levels.size - 1`);
  * `confidence` (0 to 1) is the server's certainty in the answer, derived from the distribution.
  */
final case class ScoreAnswer[+L](score: Double, confidence: Double, levels: Vector[L], probabilities: Vector[Double]) extends Answer:
  /** The level with the highest probability (the lowest one on ties); requires a non-empty `probabilities` (always true for decoded
    * answers).
    */
  def mostLikely: L = levels(probabilities.indices.maxBy(probabilities))

/** Answer types for a tuple of questions, position by position: `Answers[(Noul, Score[Int])]` is `(NoulAnswer, ScoreAnswer[Int])`. */
type Answers[Qs <: Tuple] <: Tuple = Qs match
  case EmptyTuple        => EmptyTuple
  case Question[a] *: qs => a *: Answers[qs]

/** Evidence that every element of `Qs` is a [[Question]]; its absence is a compile error naming the tuple. */
@implicitNotFound("Every element of ${Qs} must be a Question (Noul, Choice or Score).")
type AllQuestions[Qs <: Tuple] = Tuple.Union[Qs] <:< Question[?]
