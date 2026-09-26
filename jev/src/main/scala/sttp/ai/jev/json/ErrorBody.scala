package sttp.ai.jev.json

import io.circe.Decoder
import io.circe.parser.decode

/** What a non-2xx body says: a human-readable `message` and, when the server sent one, its `error_type`. */
private[jev] final case class ErrorBody(message: String, errorType: Option[String])

private[jev] object ErrorBody:
  private val plainString: Decoder[ErrorBody] = Decoder.decodeString.map(ErrorBody(_, None))

  private val messageWithType: Decoder[ErrorBody] =
    Decoder.forProduct2("message", "error_type")((message: String, errorType: Option[String]) => ErrorBody(message, errorType))

  private val locationPart: Decoder[String] = Decoder.decodeString.or(Decoder.decodeInt.map(_.toString))

  // `loc` starts with the request part (`body`), which is dropped: `questions.2.score.criteria: Field required`
  private val validationError: Decoder[String] =
    Decoder.forProduct2("loc", "msg")((loc: List[String], msg: String) => s"${loc.drop(1).mkString(".")}: $msg")(using
      Decoder.decodeList(locationPart),
      Decoder.decodeString
    )

  private val validationList: Decoder[ErrorBody] =
    Decoder.decodeList(validationError).map(errors => ErrorBody(errors.mkString("; "), None))

  private val detail: Decoder[ErrorBody] = messageWithType.or(validationList).or(plainString).at("detail")

  // Same order as the official SDKs, so a gateway body is read the way its own clients read it
  private val body: Decoder[ErrorBody] =
    plainString.at("error").or(plainString.at("message").at("error")).or(messageWithType).or(detail)

  /** Reads the message from `error` (a string or `{message}`), a top-level `message` (with optional `error_type`), or `detail` (an
    * `{error_type, message}` object, a list of validation errors, or a string). A body in none of these shapes becomes the message
    * verbatim.
    */
  def parse(body: String): ErrorBody = decode(body)(using ErrorBody.body).getOrElse(ErrorBody(body, None))
