package sttp.ai.jev.json

import sttp.ai.jev.{Choice, Noul, Question, Score}

/** The wire `type` of a question; its answer echoes the same value. */
private[jev] object WireType:
  def of(question: Question[?]): String = question match
    case _: Noul      => "noul"
    case _: Choice[?] => "choice"
    case _: Score[?]  => "score"
