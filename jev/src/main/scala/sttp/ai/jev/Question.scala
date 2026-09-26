package sttp.ai.jev

import scala.compiletime.{constValueTuple, summonAll}
import scala.deriving.Mirror

/** A question about a state; `A` is the answer type Jev returns for it. Covariant so that mixed lists infer `Question[Answer]`. */
sealed trait Question[+A <: Answer]

/** How likely it is that `instructions` hold for the state. `whenTrue` / `whenFalse` optionally describe what a yes and a no look like. */
final case class Noul(instructions: Entry, whenTrue: Option[Entry] = None, whenFalse: Option[Entry] = None) extends Question[NoulAnswer]

/** Pick one of `options`; each option's `name` is the wire key shown to the model. Names must be distinct (duplicates would collapse into
  * one JSON key), and so must values: a programmer error, rejected at construction with an [[IllegalArgumentException]].
  */
final case class Choice[O](instructions: Entry, options: Vector[ChoiceOption[O]]) extends Question[ChoiceAnswer[O]]:
  require(options.map(_.name).distinct.size == options.size, "choice option names must be distinct")
  // `ChoiceAnswer.choice` and its `probabilities` map are keyed by value
  require(options.map(_.value).distinct.size == options.size, "choice option values must be distinct")

/** One candidate of a [[Choice]]: `value` is what the answer decodes to, `name` (with the optional `description`) is what the model sees.
  */
final case class ChoiceOption[O](value: O, name: String, description: Option[Entry] = None)

object Choice:
  /** Named and described options: `Choice.described("Which team", "billing" -> "Payment issues", "sales" -> "Pricing")`. Duplicate names
    * throw [[IllegalArgumentException]].
    */
  def described(instructions: Entry, options: (String, Entry)*): Choice[String] =
    Choice(instructions, options.iterator.map((name, description) => ChoiceOption(name, name, Some(description))).toVector)

  /** Bare candidates (e.g. values pre-extracted from the state): the name is the value itself, sent without a description. Duplicates are
    * dropped.
    */
  def strings(instructions: Entry, options: Seq[String]): Choice[String] =
    from[String](instructions, options.distinct)(identity)

  /** Every case of an enum (or sealed family of singleton cases) as an option, named by the case's declared name, so a `toString` override
    * does not reach the wire. A case with parameters fails to compile.
    */
  inline def of[E](instructions: Entry, describe: E => Option[Entry] = (_: E) => None)(using m: Mirror.SumOf[E]): Choice[E] =
    // the cast only restates what the mirror guarantees: labels are string literals
    val names = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    Choice(instructions, singletonCases[E].zip(names).map((value, name) => ChoiceOption(value, name, describe(value))).toVector)

  /** Arbitrary values, named (and optionally described) by functions. Duplicate names or values throw [[IllegalArgumentException]]. */
  def from[O](instructions: Entry, options: Seq[O])(name: O => String, describe: O => Option[Entry] = (_: O) => None): Choice[O] =
    Choice(instructions, options.iterator.map(option => ChoiceOption(option, name(option), describe(option))).toVector)

/** One level of a [[Score]]: `value` is what `mostLikely` returns, `description` is what the model sees. */
final case class ScoreLevel[L](value: L, description: Entry)

/** Position on an ordered rubric. `levels` run from low to high; level `i` is `levels(i)`. */
final case class Score[L](instructions: Entry, levels: Vector[ScoreLevel[L]]) extends Question[ScoreAnswer[L]]

object Score:
  /** Plain rubric, `Score("How frustrated", "calm", "civil", "angry")`: a level's value is its index, so `mostLikely` is an `Int`. */
  def apply(instructions: Entry, first: Entry, rest: Entry*): Score[Int] =
    Score(instructions, (first +: rest).zipWithIndex.map((description, index) => ScoreLevel(index, description)).toVector)

  /** Every case of an enum (or sealed family of singleton cases) as a level, in declaration order from low to high; `describe` gives the
    * text the model sees. A case with parameters fails to compile.
    */
  inline def of[L](instructions: Entry, describe: L => Entry)(using m: Mirror.SumOf[L]): Score[L] =
    Score(instructions, singletonCases[L].map(value => ScoreLevel(value, describe(value))).toVector)

/** The cases of `E` in declaration order; compiles only when every case is a singleton. */
private inline def singletonCases[E](using m: Mirror.SumOf[E]): List[E] =
  // the cast only restates what the mirror guarantees: each `ValueOf` is for a case of `E`
  summonAll[Tuple.Map[m.MirroredElemTypes, ValueOf]].toList.asInstanceOf[List[ValueOf[E]]].map(_.value)
