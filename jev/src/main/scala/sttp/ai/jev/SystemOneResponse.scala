package sttp.ai.jev

/** A Jev response: the typed `answers` plus billing metadata. `model` is the resolved model id (e.g. "jev-1.13.0"), which may differ from
  * the alias requested. `requestId` is the server's `x-typesafe-request-id`, when the response carried one.
  */
final case class SystemOneResponse[+A](answers: A, model: String, usage: Usage, requestId: Option[String]):
  private[jev] def map[B](f: A => B): SystemOneResponse[B] = copy(answers = f(answers))

/** Token counts of one request; only input tokens are billed. */
final case class Usage(inputTokens: Int, outputTokens: Int)
