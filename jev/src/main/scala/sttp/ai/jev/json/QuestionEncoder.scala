package sttp.ai.jev.json

import io.circe.Json
import sttp.ai.jev.{Choice, ChoiceOption, Entry, JevModel, Noul, Question, Score}

/** Builds the `POST /v1/systemone` body. Questions get positional keys `"0".."n-1"`; the server's answers and error messages use the same
  * keys.
  */
private[jev] object QuestionEncoder:
  def requestBody(state: Entry, model: JevModel, questions: Seq[Question[?]]): Json =
    Json.obj(
      "state" -> encodeEntry(state),
      "model" -> Json.fromString(model.value),
      "questions" -> Json.obj(questions.zipWithIndex.map((question, index) => index.toString -> encodeQuestion(question))*)
    )

  private def encodeEntry(e: Entry): Json = e match
    case text: String => Json.fromString(text)
    case json: Json   => json

  private def encodeQuestion(question: Question[?]): Json =
    val (instructions, criteria) = question match
      case Noul(instructions, whenTrue, whenFalse) => (instructions, noulCriteria(whenTrue, whenFalse))
      case Choice(instructions, options)           => (instructions, Some(choiceCriteria(options)))
      case Score(instructions, levels)             => (instructions, Some(Json.arr(levels.map(level => encodeEntry(level.description))*)))
    Json.fromFields(
      List("type" -> Json.fromString(WireType.of(question)), "instructions" -> encodeEntry(instructions)) ++ criteria.map("criteria" -> _)
    )

  /** Only the described sides are sent; with neither described, the `criteria` object is omitted altogether. */
  private def noulCriteria(whenTrue: Option[Entry], whenFalse: Option[Entry]): Option[Json] =
    val described = List("true" -> whenTrue, "false" -> whenFalse).collect { case (key, Some(value)) => key -> encodeEntry(value) }
    Option.when(described.nonEmpty)(Json.fromFields(described))

  /** An undescribed option is sent with a `null` description: the model then interprets it by its name alone. */
  private def choiceCriteria(options: Vector[ChoiceOption[?]]): Json =
    Json.obj(options.map(option => option.name -> option.description.fold(Json.Null)(encodeEntry))*)
