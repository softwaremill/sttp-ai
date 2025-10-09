package sttp.openai.fixtures

import sttp.openai.requests.admin.{AdminApiKeyResponse, Owner}

object AdminFixture {

  val jsonRequest: String =
    """{
        |  "name": "api_key_name"
        |}""".stripMargin

  val jsonResponse: String =
    """{
      |  "object": "organization.admin_api_key",
      |  "id": "key_xyz",
      |  "name": "New Admin Key",
      |  "redacted_value": "sk-admin...xyz",
      |  "created_at": 1711471533,
      |  "owner": {
      |    "type": "user",
      |    "object": "organization.user",
      |    "id": "user_123",
      |    "name": "John Doe",
      |    "created_at": 1711471533,
      |    "role": "owner"
      |  },
      |  "value": "sk-admin-1234abcd"
      |}""".stripMargin

  val jsonListResponse: String =
    s"""{
      |  "object": "list",
      |  "data": [$jsonResponse],
      |  "first_id": "key_abc",
      |  "last_id": "key_abc",
      |  "has_more": false
      |}""".stripMargin

  val jsonDeleteResponse: String =
    """{
      |  "id": "key_abc",
      |  "object": "organization.admin_api_key.deleted",
      |  "deleted": true
      |}""".stripMargin

  val adminApiKeyResponse: AdminApiKeyResponse = AdminApiKeyResponse(
    id = "key_xyz",
    name = "New Admin Key",
    redactedValue = "sk-admin...xyz",
    createdAt = 1711471533,
    owner = Owner(
      `type` = "user",
      `object` = "organization.user",
      id = "user_123",
      name = "John Doe",
      createdAt = 1711471533,
      role = "owner"
    ),
    value = Some("sk-admin-1234abcd")
  )

}
