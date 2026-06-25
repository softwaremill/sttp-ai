package sttp.ai.openai.requests.completions.chat

sealed trait Role

object Role {

  sealed trait Standard extends Role

  case object System extends Standard

  case object User extends Standard

  case object Assistant extends Standard

  case object Tool extends Standard

  case class Custom(customRole: String) extends Role
}
