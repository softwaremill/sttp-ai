package sttp.ai.jev.json

import io.circe.{Decoder, Json}
import io.circe.parser.decode

/** What a non-2xx body says: a human-readable `message` and, when the server sent one, its `error_type`. */
private[jev] final case class ErrorBody(message: String, errorType: Option[String])

private[jev] object ErrorBody:
  private val objectDetail: Decoder[ErrorBody] =
    Decoder.forProduct2("message", "error_type")((message: String, errorType: Option[String]) => ErrorBody(message, errorType))

  private val locationPart: Decoder[String] = Decoder.decodeString.or(Decoder.decodeInt.map(_.toString))

  // `loc` starts with the request part (`body`), which is dropped: `questions.2.score.criteria: Field required`
  private val validationError: Decoder[String] =
    Decoder.forProduct2("loc", "msg")((loc: List[String], msg: String) => s"${loc.drop(1).mkString(".")}: $msg")(using
      Decoder.decodeList(locationPart),
      Decoder.decodeString
    )

  private val validationDetail: Decoder[ErrorBody] =
    Decoder.decodeList(validationError).map(errors => ErrorBody(errors.mkString("; "), None))

  private val stringDetail: Decoder[ErrorBody] = Decoder.decodeString.map(ErrorBody(_, None))

  private val detail: Decoder[ErrorBody] = objectDetail.or(validationDetail).or(stringDetail)

  /** Reads `detail` in any of its three shapes: `{error_type, message}`, a list of validation errors (`{loc, msg}`, joined by `; `), or a
    * plain string. A body in none of these shapes becomes the message verbatim.
    */
  def parse(body: String): ErrorBody =
    decode[Json](body).flatMap(_.hcursor.get("detail")(using detail)).getOrElse(ErrorBody(body, None))
