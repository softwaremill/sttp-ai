package sttp.ai.openai.requests.admin

case class AdminApiKeyResponse(
    `object`: String = "organization.admin_api_key",
    id: String,
    name: String,
    redactedValue: String,
    createdAt: Int,
    owner: Owner,
    value: Option[String]
)

case class Owner(
    `type`: String,
    `object`: String,
    id: String,
    name: String,
    createdAt: Int,
    role: String
)

case class ListAdminApiKeyResponse(
    `object`: String = "list",
    data: Seq[AdminApiKeyResponse],
    hasMore: Boolean,
    firstId: String,
    lastId: String
)

case class DeleteAdminApiKeyResponse(
    id: String,
    `object`: String = "organization.admin_api_key.deleted",
    deleted: Boolean
)
