package sttp.ai.core.json

import io.circe.generic.extras.Configuration

object CirceConfiguration {
  implicit val jsonConfiguration: Configuration =
    Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames.withDiscriminator("type").withDefaults
}
