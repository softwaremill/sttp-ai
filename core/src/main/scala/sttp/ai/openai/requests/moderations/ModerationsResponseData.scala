package sttp.openai.requests.moderations

import sttp.openai.json.SnakePickle
import sttp.openai.requests.moderations.ModerationsRequestBody.ModerationModel

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

  object CategoryScores {
    implicit val categoryScoresInfoReader: SnakePickle.Reader[CategoryScores] = SnakePickle.macroR[CategoryScores]
  }

  case class Categories(
      sexual: Boolean,
      hate: Boolean,
      violence: Boolean,
      `self-harm`: Boolean,
      `sexual/minors`: Boolean,
      `hate/threatening`: Boolean,
      `violence/graphic`: Boolean
  )
  object Categories {
    implicit val categoriesInfoReader: SnakePickle.Reader[Categories] = SnakePickle.macroR[Categories]
  }
  case class Result(
      flagged: Boolean,
      categories: Categories,
      categoryScores: CategoryScores
  )

  object Result {
    implicit val resultsInfoReader: SnakePickle.Reader[Result] = SnakePickle.macroR[Result]
  }
  case class ModerationData(
      id: String,
      model: ModerationModel,
      results: Seq[Result]
  )
  object ModerationData {
    implicit val morderationInfoReader: SnakePickle.Reader[ModerationData] = SnakePickle.macroR[ModerationData]
  }
}
