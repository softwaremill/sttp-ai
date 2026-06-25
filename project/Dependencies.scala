import sbt.*
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

object Dependencies {

  object V {
    val scalaTest = "3.2.20"
    val scalaTestCats = "1.8.0"

    val sttpApispec = "0.11.10"
    val sttpClient = "4.0.25"
    val pekkoStreams = "1.6.0"
    val akkaStreams = "2.6.20" // last Apache-2.0 licensed Akka release; 2.7+ is under the BSL
    val tapir = "1.13.23"
    val circe = "0.14.16"
    val circeGenericExtras = "0.14.4" // 0.14.5 is only available as RC
  }

  object Libraries {

    val scalaTest = Def.setting("org.scalatest" %%% "scalatest" % V.scalaTest % Test)

    val sttpApispec = Def.setting(
      Seq(
        "com.softwaremill.sttp.apispec" %%% "apispec-model" % V.sttpApispec,
        "com.softwaremill.sttp.apispec" %%% "jsonschema-circe" % V.sttpApispec
      )
    )

    val sttpClient = Def.setting(
      Seq(
        "com.softwaremill.sttp.client4" %%% "core" % V.sttpClient
      )
    )

    val circe = Def.setting(
      Seq(
        "io.circe" %%% "circe-core" % V.circe,
        "io.circe" %%% "circe-parser" % V.circe,
        "io.circe" %%% "circe-generic" % V.circe
      )
    )

    // circe-generic-extras provides `Configuration` (snake_case + discriminator) only on Scala 2.13.
    // On Scala 3 the equivalent lives in circe-generic's `io.circe.derivation` package.
    val circeGenericExtras = Def.setting("io.circe" %%% "circe-generic-extras" % V.circeGenericExtras)

    val sttpClientFs2 = Seq(
      "com.softwaremill.sttp.client4" %% "fs2" % V.sttpClient,
      "org.typelevel" %% "cats-effect-testing-scalatest" % V.scalaTestCats % Test
    )

    val sttpClientZio = "com.softwaremill.sttp.client4" %% "zio" % V.sttpClient

    val sttpClientPekko = Seq(
      "com.softwaremill.sttp.client4" %% "pekko-http-backend" % V.sttpClient,
      "org.apache.pekko" %% "pekko-stream" % V.pekkoStreams
    )

    val sttpClientAkka = Seq(
      "com.softwaremill.sttp.client4" %% "akka-http-backend" % V.sttpClient,
      "com.typesafe.akka" %% "akka-stream" % V.akkaStreams
    )

    val sttpClientOx = Seq(
      "com.softwaremill.sttp.client4" %% "ox" % V.sttpClient
    )

    val tapirApispecDocs = Def.setting("com.softwaremill.sttp.tapir" %%% "tapir-apispec-docs" % V.tapir)

  }

}
