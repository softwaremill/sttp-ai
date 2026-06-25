package sttp.ai.openai.requests.models

object ModelsResponseData {

  case class DeletedModelData(
      id: String,
      `object`: String,
      deleted: Boolean
  )

  case class ModelData(
      id: String,
      `object`: String,
      created: Int,
      ownedBy: String
  )

  case class ModelsResponse(`object`: String, data: Seq[ModelData])
}
