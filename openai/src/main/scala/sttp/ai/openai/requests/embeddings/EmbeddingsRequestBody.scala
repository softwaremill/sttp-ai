package sttp.ai.openai.requests.embeddings

import io.circe.Json

object EmbeddingsRequestBody {

  /** @param model
    *   ID of the [[EmbeddingsModel]] to use.
    * @param input
    *   Input text to get embeddings for, encoded as a string or array of tokens.
    * @param user
    *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
    * @param dimensions
    *   The number of dimensions for the embeddings. Only supported in text-embedding-3 and later models.
    * @param extraBody
    *   Arbitrary extra parameters merged into the top level of the serialized request JSON, alongside the fields above. Useful for
    *   parameters supported only by an OpenAI-compatible backend, which have no typed field here. If a key collides with one of the typed
    *   fields above, the `extraBody` value wins for scalar/array values; if both sides are JSON objects, they are merged recursively (deep
    *   merge), so unset sub-keys of the typed field's object may still survive alongside `extraBody`'s.
    */
  case class EmbeddingsBody(
      model: EmbeddingsModel,
      input: EmbeddingsInput,
      user: Option[String] = None,
      dimensions: Option[Int] = None,
      extraBody: Map[String, Json] = Map.empty
  )

  sealed abstract class EmbeddingsModel(val value: String)

  object EmbeddingsModel {
    case object TextEmbedding3Large extends EmbeddingsModel("text-embedding-3-large")
    case object TextEmbedding3Small extends EmbeddingsModel("text-embedding-3-small")
    case object TextEmbeddingAda002 extends EmbeddingsModel("text-embedding-ada-002")
    case class CustomEmbeddingsModel(customEmbeddingsModel: String) extends EmbeddingsModel(customEmbeddingsModel)

    val values: Set[EmbeddingsModel] = Set(TextEmbedding3Large, TextEmbedding3Small, TextEmbeddingAda002)
  }

  sealed trait EmbeddingsInput
  object EmbeddingsInput {
    case class SingleInput(value: String) extends EmbeddingsInput
    case class MultipleInput(values: Seq[String]) extends EmbeddingsInput
  }
}
