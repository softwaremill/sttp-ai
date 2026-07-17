package sttp.ai.openai.json

import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4._
import sttp.model.ResponseMetadata
import sttp.model.StatusCode._
import io.circe.{parser, Decoder, Encoder, Json}
import io.circe.syntax._
import io.circe.generic.semiauto.deriveDecoder
import sttp.model.MediaType
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.OpenAIExceptions.OpenAIException._
import sttp.ai.core.http.ResponseHandlers

/** circe-based response handling for the OpenAI API: deserializes snake_case JSON into case classes and maps errors to OpenAIException
  * subclasses. Replaces the previous uPickle-based extension.
  */
object OpenAIJson extends ResponseHandlers[OpenAIException, Decoder] {

  override def read[T: Decoder](s: String): T = parser.decode[T](s).fold(throw _, identity)

  override def deserializationException(cause: Exception, metadata: ResponseMetadata): OpenAIException =
    DeserializationOpenAIException(cause, metadata)

  override def mapErrorToException(errorResponse: String, metadata: ResponseMetadata): OpenAIException =
    httpToOpenAIError(UnexpectedStatusCode(errorResponse, metadata))

  /** Parse a successful JSON response into `T`, mapping non-2xx responses to the corresponding [[OpenAIException]] (rate limit, invalid
    * request, etc.) rather than a generic deserialization error.
    */
  override def asJson_parseErrors[T: Decoder]: ResponseAs[Either[OpenAIException, T]] =
    asStringAlways
      .mapWithMetadata { (body, metadata) =>
        if (metadata.isSuccess)
          parser.decode[T](body).left.map(e => DeserializationOpenAIException(e, metadata))
        else
          Left(httpToOpenAIError(UnexpectedStatusCode(body, metadata)))
      }
      .showAs("either(as error, as json)")

  /** Decodes a single SSE data payload into `B`, used by the streaming modules. */
  def deserializeJsonSnake[B: Decoder]: (String, ResponseMetadata) => Either[DeserializationOpenAIException, B] =
    (s: String, meta: ResponseMetadata) => parser.decode[B](s).left.map(e => DeserializationOpenAIException(e, meta))

  /** Serializes a value to a JSON request body, omitting `None`/null fields */
  def asJson[B: Encoder](b: B): StringBody =
    StringBody(dropNullsPreservingSchemas(b.asJson).noSpaces, "utf-8", MediaType.ApplicationJson)

  /** `deepDropNullValues` is applied to the whole request body to strip unset-`Option` fields (e.g. `user: null`), but it also strips null
    * VALUES nested inside array elements, not just absent object fields. That corrupts tool schemas and response-format schemas that
    * legitimately contain JSON `null` (e.g. `"enum": ["low", "high", null]`, produced by strict-mode nullability normalization via
    * `SchemaSupport.normalizeForStrict`/`addNullToEnum`).
    *
    * To keep those schemas byte-faithful while still dropping unset-field nulls everywhere else: capture `tools[*].function.parameters` and
    * `response_format.json_schema.schema` from the pre-drop encoding, run `deepDropNullValues` as before, then splice the untouched values
    * back into the cleaned JSON. This is a safe no-op for the (large majority of) request bodies that have neither field.
    */
  private def dropNullsPreservingSchemas(bodyJson: Json): Json = {
    val originalToolParameters: Option[Vector[Option[Json]]] =
      bodyJson.asObject
        .flatMap(_("tools"))
        .flatMap(_.asArray)
        .map(_.map(_.asObject.flatMap(_("function")).flatMap(_.asObject).flatMap(_("parameters"))))

    val originalResponseFormatSchema: Option[Json] =
      bodyJson.asObject
        .flatMap(_("response_format"))
        .flatMap(_.asObject)
        .flatMap(_("json_schema"))
        .flatMap(_.asObject)
        .flatMap(_("schema"))

    val cleaned = bodyJson.deepDropNullValues

    val withToolsRestored = originalToolParameters match {
      case Some(originalParameters) =>
        cleaned.mapObject { obj =>
          obj("tools").flatMap(_.asArray) match {
            case Some(cleanedTools) =>
              val restoredTools = cleanedTools.zip(originalParameters).map {
                case (toolJson, Some(parameters)) =>
                  toolJson.mapObject { toolObj =>
                    toolObj("function").flatMap(_.asObject) match {
                      case Some(functionObj) => toolObj.add("function", Json.fromJsonObject(functionObj.add("parameters", parameters)))
                      case None              => toolObj
                    }
                  }
                case (toolJson, None) => toolJson
              }
              obj.add("tools", Json.fromValues(restoredTools))
            case None => obj
          }
        }
      case None => cleaned
    }

    originalResponseFormatSchema match {
      case Some(schema) =>
        withToolsRestored.mapObject { obj =>
          obj("response_format").flatMap(_.asObject) match {
            case Some(responseFormatObj) =>
              responseFormatObj("json_schema").flatMap(_.asObject) match {
                case Some(jsonSchemaObj) =>
                  obj.add(
                    "response_format",
                    Json.fromJsonObject(responseFormatObj.add("json_schema", Json.fromJsonObject(jsonSchemaObj.add("schema", schema))))
                  )
                case None => obj
              }
            case None => obj
          }
        }
      case None => withToolsRestored
    }
  }

  def asStringEither: ResponseAs[Either[OpenAIException, String]] =
    asStringAlways
      .mapWithMetadata { (string, metadata) =>
        if (metadata.isSuccess) Right(string) else Left(httpToOpenAIError(UnexpectedStatusCode(string, metadata)))
      }
      .showAs("either(as error, as string)")

  /** Max number of characters of a raw error body kept as the fallback message when it can't be parsed as a standard OpenAI error. */
  private val MaxRawErrorBodyLength = 500

  private def httpToOpenAIError(he: UnexpectedStatusCode[String]): OpenAIException = {
    // Fallback for bodies that aren't the standard {"error": {...}} OpenAI shape (e.g. Azure's {"statusCode":...,"message":...},
    // a non-object JSON root, or non-JSON): expose a truncated raw body and the status code rather than swallowing it.
    val fallback = (Some(he.body.take(MaxRawErrorBodyLength)), None, None, Some(he.response.code.toString))

    val (message, tpe, param, code) =
      parser.parse(he.body).toOption.flatMap(_.asObject).flatMap(_("error")) match {
        case Some(errorNode) =>
          errorNode.as[Error] match {
            case Right(error) => (error.message, error.`type`, error.param, error.code)
            case Left(_)      => fallback
          }
        case None => fallback
      }

    he.response.code match {
      case TooManyRequests                              => new RateLimitException(message, tpe, param, code, he)
      case BadRequest | NotFound | UnsupportedMediaType => new InvalidRequestException(message, tpe, param, code, he)
      case Unauthorized                                 => new AuthenticationException(message, tpe, param, code, he)
      case Forbidden                                    => new PermissionException(message, tpe, param, code, he)
      case Conflict                                     => new TryAgain(message, tpe, param, code, he)
      case ServiceUnavailable                           => new ServiceUnavailableException(message, tpe, param, code, he)
      case _                                            => new APIException(message, tpe, param, code, he)
    }
  }

  private case class Error(
      message: Option[String] = None,
      `type`: Option[String] = None,
      param: Option[String] = None,
      code: Option[String] = None
  )
  private object Error {
    implicit val errorDecoder: Decoder[Error] = deriveDecoder
  }
}
