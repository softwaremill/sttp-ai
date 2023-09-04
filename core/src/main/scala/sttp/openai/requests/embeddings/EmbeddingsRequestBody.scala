package sttp.openai.requests.embeddings

import sttp.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.openai.json.SnakePickle
import ujson.Str

object EmbeddingsRequestBody {

  /** @param model
    *   ID of the [[EmbeddingsModel]] to use.
    * @param input
    *   Input text to get embeddings for, encoded as a string or array of tokens.
    * @param user
    *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
    */
  case class EmbeddingsBody(model: EmbeddingsModel, input: EmbeddingsInput, user: Option[String] = None)

  object EmbeddingsBody {
    implicit val embeddingsBodyWriter: SnakePickle.Writer[EmbeddingsBody] = SnakePickle.macroW
  }

  sealed abstract class EmbeddingsModel(val value: String)

  object EmbeddingsModel {

    implicit val embeddingsModelReadWriter: SnakePickle.ReadWriter[EmbeddingsModel] = SnakePickle
      .readwriter[ujson.Value]
      .bimap[EmbeddingsModel](
        model => SnakePickle.writeJs(model.value),
        jsonValue =>
          SnakePickle.read[ujson.Value](jsonValue) match {
            case Str(value) =>
              byEmbeddingsModelValue.getOrElse(value, CustomEmbeddingsModel(value))
            case e => throw DeserializationOpenAIException(new Exception(s"Could not deserialize: $e"))
          }
      )
    case object TextEmbeddingAda002 extends EmbeddingsModel("text-embedding-ada-002")
    case object TextSearchAdaDoc001 extends EmbeddingsModel("text-search-ada-doc-001")
    case class CustomEmbeddingsModel(customEmbeddingsModel: String) extends EmbeddingsModel(customEmbeddingsModel)

    val values: Set[EmbeddingsModel] = Set(TextEmbeddingAda002, TextSearchAdaDoc001)

    private val byEmbeddingsModelValue = values.map(model => model.value -> model).toMap
  }

  sealed trait EmbeddingsInput
  object EmbeddingsInput {
    case class SingleInput(value: String) extends EmbeddingsInput
    case class MultipleInput(values: Seq[String]) extends EmbeddingsInput

    implicit val embeddingsInputWriter: SnakePickle.Writer[EmbeddingsInput] = SnakePickle.writer[ujson.Value].comap[EmbeddingsInput] {
      case SingleInput(value)    => SnakePickle.writeJs(value)
      case MultipleInput(values) => SnakePickle.writeJs(values)
    }
  }
}
