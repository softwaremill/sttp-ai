package sttp.ai.openai.requests.moderations

import sttp.ai.openai.requests.moderations.ModerationsRequestBody.ModerationModel

object ModerationsResponseData {
  case class CategoryScores(
      sexual: Double,
      hate: Double,
      violence: Double,
      `self-harm`: Double,
      `sexual/minors`: Double,
      `hate/threatening`: Double,
      `violence/graphic`: Double
  )

  case class Categories(
      sexual: Boolean,
      hate: Boolean,
      violence: Boolean,
      `self-harm`: Boolean,
      `sexual/minors`: Boolean,
      `hate/threatening`: Boolean,
      `violence/graphic`: Boolean
  )

  case class Result(
      flagged: Boolean,
      categories: Categories,
      categoryScores: CategoryScores
  )

  case class ModerationData(
      id: String,
      model: ModerationModel,
      results: Seq[Result]
  )
}
