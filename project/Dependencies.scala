import sbt.*

object Dependencies {

  object V {
    val scalaTest = "3.2.17"
    val sttpClient = "4.0.0-M4"
    val uPickle = "3.1.3"
  }

  object Libraries {

    val scalaTest = "org.scalatest" %% "scalatest" % V.scalaTest % Test

    val sttpClient = Seq(
      "com.softwaremill.sttp.client4" %% "core" % V.sttpClient,
      "com.softwaremill.sttp.client4" %% "upickle" % V.sttpClient
    )

    val uPickle = "com.lihaoyi" %% "upickle" % V.uPickle

  }

}
