import sbt.*
import sbt.Def
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

object Dependencies {

  object V {
    val scalaTest = "3.2.19"
    val scalaTestCats = "1.7.0"

    val sttpApispec = "0.11.10"
    val sttpClient = "4.0.18"
    val pekkoStreams = "1.2.1"
    val akkaStreams = "2.6.20"
    val tapir = "1.13.8"
    val uPickle = "4.3.2"
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
        "com.softwaremill.sttp.client4" %%% "core" % V.sttpClient,
        "com.softwaremill.sttp.client4" %%% "upickle" % V.sttpClient
      )
    )

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

    val uJsonCirce = Def.setting("com.lihaoyi" %%% "ujson-circe" % V.uPickle)

    val uPickle = Def.setting("com.lihaoyi" %%% "upickle" % V.uPickle)

  }

}
