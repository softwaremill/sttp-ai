package sttp.ai.gemini.models

/** Status of an interaction. Unknown values decode as [[InteractionStatus.Other]] so new API statuses don't break deserialization. */
sealed abstract class InteractionStatus(val value: String)

object InteractionStatus {
  case object Completed extends InteractionStatus("completed")
  case object RequiresAction extends InteractionStatus("requires_action")
  case object InProgress extends InteractionStatus("in_progress")
  case object Failed extends InteractionStatus("failed")
  case object Cancelled extends InteractionStatus("cancelled")
  case object Incomplete extends InteractionStatus("incomplete")
  case object BudgetExceeded extends InteractionStatus("budget_exceeded")
  case object Queued extends InteractionStatus("queued")
  case class Other(raw: String) extends InteractionStatus(raw)

  val values: List[InteractionStatus] =
    List(Completed, RequiresAction, InProgress, Failed, Cancelled, Incomplete, BudgetExceeded, Queued)

  def fromString(s: String): InteractionStatus =
    values.find(_.value == s).getOrElse(Other(s))
}
