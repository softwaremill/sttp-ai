package sttp.ai.core.json

import io.circe.{Codec, Decoder, Encoder, Json}

object CirceCodecs {

  implicit def emptyIterableAsNone[A[x] <: Iterable[x], B](implicit encoder: Encoder[A[B]], decoder: Decoder[A[B]]): Codec[Option[A[B]]] =
    Codec.from(Decoder.decodeOption[A[B]].map(_.filter(_.nonEmpty)), Encoder.encodeOption[A[B]].contramap(_.filter(_.nonEmpty)))

  implicit def emptyMapAsNone[K, V](implicit encoder: Encoder[Map[K, V]], decoder: Decoder[Map[K, V]]): Codec[Option[Map[K, V]]] =
    Codec.from(Decoder.decodeOption[Map[K, V]].map(_.filter(_.nonEmpty)), Encoder.encodeOption[Map[K, V]].contramap(_.filter(_.nonEmpty)))

  def dropEmptyTopLevel(j: Json): Json =
    j.mapObject(_.filter { case (_, v) => !(v.asArray.exists(_.isEmpty) || v.asObject.exists(_.isEmpty)) })

  implicit val omitFalse: Encoder[Option[Boolean]] = Encoder.encodeOption[Boolean].contramap(_.filter(identity))
}
