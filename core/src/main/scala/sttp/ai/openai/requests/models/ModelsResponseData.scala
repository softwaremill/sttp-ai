package sttp.openai.requests.models

import sttp.openai.json.SnakePickle

object ModelsResponseData {

  case class DeletedModelData(
      id: String,
      `object`: String,
      deleted: Boolean
  )

  object DeletedModelData {
    implicit val deletedModelDataR: SnakePickle.Reader[DeletedModelData] = SnakePickle.macroR[DeletedModelData]
  }

  case class ModelData(
      id: String,
      `object`: String,
      created: Int,
      ownedBy: String
  )

  object ModelData {
    implicit def dataReader: SnakePickle.Reader[ModelData] = SnakePickle.macroR[ModelData]
  }

  case class ModelsResponse(`object`: String, data: Seq[ModelData])

  object ModelsResponse {
    implicit def modelsResponseR: SnakePickle.Reader[ModelsResponse] = SnakePickle.macroR[ModelsResponse]
  }
}
