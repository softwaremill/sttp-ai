package sttp.ai.jev.json

import io.circe.{Decoder, DecodingFailure, HCursor, Json, JsonObject}
import sttp.ai.jev.{Answer, Choice, ChoiceAnswer, Noul, NoulAnswer, Question, Score, ScoreAnswer}

/** Decodes answers strictly against the questions that produced them: the answer `type` must match the question, a choice and every
  * probability key must be one of the options, and every option and level must have a probability. Extra keys (including the score
  * `legend`) are ignored.
  */
private[jev] object AnswerDecoder:
  /** Matches the answer keyed `i` in `answers` to `questions(i)`. */
  def decodeAll[A <: Answer](questions: Seq[Question[A]], answers: JsonObject): Decoder.Result[Seq[A]] =
    traverse(questions.zipWithIndex): (question, index) =>
      answers(index.toString).toRight(DecodingFailure(s"missing answer for question $index", Nil)).flatMap(decode(question, _))

  private def decode[A <: Answer](question: Question[A], answer: Json): Decoder.Result[A] =
    val cursor = answer.hcursor
    val expectedType = WireType.of(question)
    for
      answerType <- cursor.get[String]("type")
      _ <- Either.cond(answerType == expectedType, (), failure(cursor, s"expected a '$expectedType' answer, got '$answerType'"))
      decoded <- decodeTyped(question, cursor)
    yield decoded

  private def decodeTyped[A <: Answer](question: Question[A], cursor: HCursor): Decoder.Result[A] = question match
    case _: Noul           => cursor.get[Double]("noul").map(NoulAnswer(_))
    case choice: Choice[o] => decodeChoice(choice, cursor)
    case score: Score[l]   => decodeScore(score, cursor)

  private def decodeChoice[O](question: Choice[O], cursor: HCursor): Decoder.Result[ChoiceAnswer[O]] =
    val valuesByName = question.options.map(option => option.name -> option.value).toMap
    def optionValue(name: String): Decoder.Result[O] = valuesByName.get(name).toRight(failure(cursor, s"unknown option '$name'"))
    for
      choice <- cursor.get[String]("choice").flatMap(optionValue)
      confidence <- cursor.get[Double]("confidence")
      probabilities <- cursor.get[Map[String, Double]]("probabilities")
      byValue <- traverse(probabilities.toSeq)((name, probability) => optionValue(name).map(_ -> probability))
      _ <- question.options.map(_.name).find(!probabilities.contains(_)).toLeft(()).left.map { name =>
        failure(cursor, s"missing probability for option '$name'")
      }
    yield ChoiceAnswer(choice, confidence, byValue.toMap)

  private def decodeScore[L](question: Score[L], cursor: HCursor): Decoder.Result[ScoreAnswer[L]] =
    for
      score <- cursor.get[Double]("score")
      confidence <- cursor.get[Double]("confidence")
      byLevel <- cursor.get[Map[String, Double]]("probabilities")
      probabilities <- traverse(question.levels.indices)(level =>
        byLevel.get(level.toString).toRight(failure(cursor, s"missing probability for level $level"))
      )
    yield ScoreAnswer(score, confidence, question.levels.map(_.value), probabilities)

  private def failure(cursor: HCursor, message: String): DecodingFailure = DecodingFailure(message, cursor.history)

  private def traverse[A, B](values: Seq[A])(f: A => Decoder.Result[B]): Decoder.Result[Vector[B]] =
    values.foldLeft[Decoder.Result[Vector[B]]](Right(Vector.empty))((acc, value) => acc.flatMap(decoded => f(value).map(decoded :+ _)))
