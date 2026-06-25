package sttp.ai.openai.requests.embeddings

import sttp.ai.openai.requests.embeddings.EmbeddingsRequestBody.EmbeddingsModel

object EmbeddingsResponseBody {
  case class EmbeddingData(
      `object`: String,
      index: Int,
      embedding: Seq[Double]
  )

  case class EmbeddingResponse(
      `object`: String,
      data: Seq[EmbeddingData],
      model: EmbeddingsModel,
      usage: Usage
  )

  case class Usage(
      promptTokens: Int,
      totalTokens: Int
  )
}
