package sttp.ai.claude.responses

case class ModelsResponse(
    data: List[ModelData]
)

case class ModelData(
    id: String,
    `type`: String,
    displayName: String
)
