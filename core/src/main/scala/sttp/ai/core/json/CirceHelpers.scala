package sttp.ai.core.json

import io.circe.{Codec, Decoder, Encoder, Json}

object CirceHelpers {

  implicit def emptyIterableAsNone[A[x] <: Iterable[x], B](implicit encoder: Encoder[A[B]], decoder: Decoder[A[B]]): Codec[Option[A[B]]] =
    Codec.from(Decoder.decodeOption[A[B]].map(_.filter(_.nonEmpty)), Encoder.encodeOption[A[B]].contramap(_.filter(_.nonEmpty)))

  implicit def emptyMapAsNone[K, V](implicit encoder: Encoder[Map[K, V]], decoder: Decoder[Map[K, V]]): Codec[Option[Map[K, V]]] =
    Codec.from(Decoder.decodeOption[Map[K, V]].map(_.filter(_.nonEmpty)), Encoder.encodeOption[Map[K, V]].contramap(_.filter(_.nonEmpty)))

  def dropEmptyTopLevel(j: Json): Json =
    j.mapObject(_.filter { case (_, v) => !(v.asArray.exists(_.isEmpty) || v.asObject.exists(_.isEmpty)) })

  implicit val omitFalse: Encoder[Option[Boolean]] = Encoder.encodeOption[Boolean].contramap(_.filter(identity))

  /** Splices the JSON object under `fieldName` into the top level of `json`, dropping the `fieldName` wrapper key. Used to implement
    * `extraBody`-style escape hatches: a request body encodes its `extraBody: Map[String, Json]` field as a nested object under `fieldName`
    * like any other field, and this then flattens it into siblings of the body's other fields, overriding any of them it collides with
    * (`deepMerge` is right-biased). A no-op if `fieldName` is absent or not a JSON object.
    */
  def mergeExtraBody(fieldName: String)(json: Json): Json =
    json.asObject match {
      case Some(obj) =>
        obj(fieldName).flatMap(_.asObject) match {
          case Some(extra) => Json.fromJsonObject(obj.remove(fieldName)).deepMerge(Json.fromJsonObject(extra))
          case None        => json
        }
      case None => json
    }
}
