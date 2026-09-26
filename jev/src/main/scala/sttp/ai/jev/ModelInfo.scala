package sttp.ai.jev

/** A model listed by `GET /v1/models`. `releaseDate` is the ISO-8601 timestamp reported by the server. */
final case class ModelInfo(name: String, description: String, releaseDate: String)
