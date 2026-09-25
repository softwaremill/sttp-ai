package sttp.ai.jev

import sttp.ai.core.model.AIModel

/** Model ids accepted by Jev. Use [[JevModel.CustomModel]] for a pinned version such as "jev-1.13.0". */
sealed abstract class JevModel(val value: String) extends AIModel

object JevModel:
  case object JevLatest extends JevModel("jev-latest")
  case object JevPreview extends JevModel("jev-preview")

  /** A model id not in the predefined list, e.g. a version pin. */
  final case class CustomModel(override val value: String) extends JevModel(value)

  val values: List[JevModel] = List(JevLatest, JevPreview)

  def fromString(s: String): JevModel = values.find(_.value == s).getOrElse(CustomModel(s))
