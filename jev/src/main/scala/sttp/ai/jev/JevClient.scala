package sttp.ai.jev

import io.circe.Decoder
import io.circe.parser.parse
import sttp.ai.jev.JevExceptions.JevException
import sttp.ai.jev.JevExceptions.JevException.*
import sttp.ai.jev.json.{ErrorBody, QuestionEncoder, ResponseDecoders}
import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4.{asString, basicRequest, Request, ResponseAs}
import sttp.model.{ResponseMetadata, StatusCode, Uri}

/** Async Jev (TypeSafe AI) client: every method builds an sttp request to send with the backend of your choice. Nothing is thrown; API and
  * decoding failures come back as a [[JevExceptions.JevException]]. The model asked is `JevConfig.model`.
  */
trait JevClient:
  /** Asks one question about `state`. */
  def ask[A](state: Entry, question: Question[A]): Request[Either[JevException, SystemOneResponse[A]]]

  /** Asks a tuple of questions about `state`; the answers are a tuple of the matching answer types, position by position:
    * `ask(state, (noul, choice))` answers with `(NoulAnswer, ChoiceAnswer[O])`. Every element must be a [[Question]].
    */
  def ask[Qs <: NonEmptyTuple](state: Entry, questions: Qs)(using
      Tuple.Union[Qs] <:< Question[?]
  ): Request[Either[JevException, SystemOneResponse[Answers[Qs]]]]

  /** Asks a list of questions about `state`, answered in the same order. A mixed list is a `Seq[Question[Answer]]`. */
  def askAll[A](state: Entry, questions: Seq[Question[A]]): Request[Either[JevException, SystemOneResponse[Seq[A]]]]

  def listModels(): Request[Either[JevException, Seq[ModelInfo]]]

object JevClient:
  def apply(config: JevConfig): JevClient = new JevClientImpl(config)

  def fromEnv: JevClient = apply(JevConfig.fromEnv)

private[jev] class JevClientImpl(config: JevConfig) extends JevClient:
  private val systemOneUri: Uri = config.baseUrl.addPath("v1", "systemone")
  private val modelsUri: Uri = config.baseUrl.addPath("v1", "models")

  override def ask[A](state: Entry, question: Question[A]): Request[Either[JevException, SystemOneResponse[A]]] =
    // exactly one answer is decoded per question sent
    askAll(state, Seq(question)).mapResponse(_.map(_.map(_.head)))

  override def ask[Qs <: NonEmptyTuple](state: Entry, questions: Qs)(using
      Tuple.Union[Qs] <:< Question[?]
  ): Request[Either[JevException, SystemOneResponse[Answers[Qs]]]] =
    // unchecked: the `<:<` evidence is a compile-time guard only, as `Tuple.Union[questions.type]` does not reduce to a type it converts
    val questionList = questions.toList.map(_.asInstanceOf[Question[?]])
    // unchecked: `Answers[Qs]` pairs each position with its question's answer type, which is what `decodeAll` produced, in order
    askAll(state, questionList).mapResponse(_.map(_.map(answers => Tuple.fromArray(answers.toArray).asInstanceOf[Answers[Qs]])))

  override def askAll[A](state: Entry, questions: Seq[Question[A]]): Request[Either[JevException, SystemOneResponse[Seq[A]]]] =
    basicRequest
      .headers(config.authHeaders)
      .readTimeout(config.timeout)
      .post(systemOneUri)
      .body(QuestionEncoder.requestBody(state, config.model, questions).noSpaces)
      .response(asJsonOrError(ResponseDecoders.systemOne(questions, _)))

  override def listModels(): Request[Either[JevException, Seq[ModelInfo]]] =
    basicRequest
      .headers(config.authHeaders)
      .readTimeout(config.timeout)
      .get(modelsUri)
      .response(asJsonOrError(_ => ResponseDecoders.models))

  /** Decodes a 2xx body with the decoder built for its request id; any other status becomes the exception for it. Not built on core's
    * `ResponseHandlers`: the exceptions need the request id from the response metadata.
    */
  private def asJsonOrError[A](decoder: Option[String] => Decoder[A]): ResponseAs[Either[JevException, A]] =
    asString.mapWithMetadata: (body, metadata) =>
      val requestId = metadata.header(JevClientImpl.RequestIdHeader)
      body match
        case Left(error) => Left(statusException(error, metadata, requestId))
        case Right(text) =>
          parse(text).flatMap(decoder(requestId).decodeJson).left.map(DeserializationJevException(text, _, metadata, requestId))

  private def statusException(body: String, metadata: ResponseMetadata, requestId: Option[String]): JevException =
    val error = ErrorBody.parse(body)
    val message = Some(error.message)
    val cause = UnexpectedStatusCode(body, metadata)
    metadata.code match
      case StatusCode.BadRequest | StatusCode.UnprocessableEntity => InvalidRequestException(message, error.errorType, cause, requestId)
      case StatusCode.Unauthorized | StatusCode.Forbidden         => AuthenticationException(message, error.errorType, cause, requestId)
      case StatusCode.TooManyRequests                             => RateLimitException(message, error.errorType, cause, requestId)
      case JevClientImpl.Overloaded                               => OverloadedException(message, error.errorType, cause, requestId)
      case _                                                      => APIException(message, error.errorType, cause, requestId)

private[jev] object JevClientImpl:
  val RequestIdHeader: String = "x-typesafe-request-id"

  // sttp has no constant for 529, which the API uses for "overloaded"
  private val Overloaded: StatusCode = StatusCode(529)
