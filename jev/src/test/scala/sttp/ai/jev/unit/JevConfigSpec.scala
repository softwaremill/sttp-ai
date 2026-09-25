package sttp.ai.jev.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.jev.{JevConfig, JevModel}
import sttp.model.Uri

class JevConfigSpec extends AnyFlatSpec with Matchers:

  "JevModel.fromString" should "map a known alias to its object" in {
    JevModel.fromString("jev-preview") shouldBe JevModel.JevPreview
  }

  it should "wrap any other id in a CustomModel" in {
    JevModel.fromString("jev-1.13.0") shouldBe JevModel.CustomModel("jev-1.13.0")
  }

  "JevConfig.fromEnv" should "prefer TYPESAFE_API_KEY over JEV_API_KEY" in {
    JevConfig.fromEnv(Map("TYPESAFE_API_KEY" -> "typesafe", "JEV_API_KEY" -> "jev")).apiKey shouldBe "typesafe"
  }

  it should "fall back to JEV_API_KEY when TYPESAFE_API_KEY is empty" in {
    JevConfig.fromEnv(Map("TYPESAFE_API_KEY" -> "", "JEV_API_KEY" -> "jev")).apiKey shouldBe "jev"
  }

  it should "read the optional base URL and model" in {
    val env = Map("TYPESAFE_API_KEY" -> "key", "TYPESAFE_BASE_URL" -> "http://localhost:8080", "TYPESAFE_DEFAULT_MODEL" -> "jev-preview")
    val config = JevConfig.fromEnv(env)
    config.baseUrl shouldBe Uri.unsafeParse("http://localhost:8080")
    config.model shouldBe JevModel.JevPreview
  }

  it should "fail when no key is set" in {
    the[IllegalArgumentException] thrownBy JevConfig.fromEnv(Map.empty) should have message
      "TYPESAFE_API_KEY (or JEV_API_KEY) environment variable is required"
  }
