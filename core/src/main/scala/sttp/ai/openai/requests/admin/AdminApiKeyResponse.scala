package sttp.openai.requests.admin

import sttp.openai.json.SnakePickle

case class AdminApiKeyResponse(
    `object`: String = "organization.admin_api_key",
    id: String,
    name: String,
    redactedValue: String,
    createdAt: Int,
    owner: Owner,
    value: Option[String]
)

object AdminApiKeyResponse {
  implicit val adminApiKeyResponseR: SnakePickle.Reader[AdminApiKeyResponse] = SnakePickle.macroR[AdminApiKeyResponse]
}

case class Owner(
    `type`: String,
    `object`: String,
    id: String,
    name: String,
    createdAt: Int,
    role: String
)

object Owner {
  implicit val ownerR: SnakePickle.Reader[Owner] = SnakePickle.macroR[Owner]
}

case class ListAdminApiKeyResponse(
    `object`: String = "list",
    data: Seq[AdminApiKeyResponse],
    hasMore: Boolean,
    firstId: String,
    lastId: String
)

object ListAdminApiKeyResponse {
  implicit val listAdminApiKeyResponseR: SnakePickle.Reader[ListAdminApiKeyResponse] = SnakePickle.macroR[ListAdminApiKeyResponse]
}

case class DeleteAdminApiKeyResponse(
    id: String,
    `object`: String = "organization.admin_api_key.deleted",
    deleted: Boolean
)

object DeleteAdminApiKeyResponse {
  implicit val deleteAdminApiKeyResponseR: SnakePickle.Reader[DeleteAdminApiKeyResponse] = SnakePickle.macroR[DeleteAdminApiKeyResponse]
}
