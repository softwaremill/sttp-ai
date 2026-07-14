import Dependencies.*
import com.softwaremill.Publish.ossPublishSettings
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings

val scala2 = List("2.13.18")
val scala3 = List("3.3.8")

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.ai",
  // Suppress ScalaTest Assertion unused value warnings in tests
  Test / scalacOptions += "-Wconf:msg=unused value of type org.scalatest.Assertion:silent",
  Test / scalacOptions += "-Wconf:msg=discarded non-Unit value of type org.scalatest.Assertion:silent"
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publish / skip := true, name := "sttp-ai", scalaVersion := scala2.head)
  .aggregate(allAgregates: _*)

lazy val allAgregates = core.projectRefs ++
  openai.projectRefs ++
  claude.projectRefs ++
  fs2.projectRefs ++
  zio.projectRefs ++
  pekko.projectRefs ++
  akka.projectRefs ++
  ox.projectRefs ++
  examples.projectRefs ++
  docs.projectRefs

lazy val core = (projectMatrix in file("core"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      Libraries.sttpClient.value ++ Libraries.sttpApispec.value ++ Libraries.circe.value ++ Seq(
        Libraries.tapirApispecDocs.value,
        Libraries.scalaTest.value
      ),
    // circe configured derivation lives in different artifacts per Scala version: circe-generic-extras on 2.13,
    // io.circe.derivation (bundled in circe-generic) on 3.
    libraryDependencies ++= (if (scalaVersion.value.startsWith("2.")) Seq(Libraries.circeGenericExtras.value) else Seq.empty)
  )

lazy val openai = (projectMatrix in file("openai"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(
    libraryDependencies ++=
      Libraries.sttpClient.value ++ Libraries.circe.value ++
        Seq(Libraries.tapirApispecDocs.value) ++
        Libraries.sttpApispec.value ++ Seq(Libraries.scalaTest.value),
    libraryDependencies ++= (if (scalaVersion.value.startsWith("2.")) Seq(Libraries.circeGenericExtras.value) else Seq.empty)
  )
  .settings(commonSettings: _*)
  .dependsOn(core % "compile->compile;test->test")

lazy val claude = (projectMatrix in file("claude"))
  .jvmPlatform(
    scalaVersions = scala3 ++ scala2 // Scala 3 first priority
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      Seq(Libraries.tapirApispecDocs.value) ++
        Libraries.sttpApispec.value ++ Libraries.circe.value ++
        Libraries.sttpClient.value ++ Seq(Libraries.scalaTest.value),
    libraryDependencies ++= (if (scalaVersion.value.startsWith("2.")) Seq(Libraries.circeGenericExtras.value) else Seq.empty)
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val fs2 = (projectMatrix in file("streaming/fs2"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientFs2
  )
  .dependsOn(openai % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val zio = (projectMatrix in file("streaming/zio"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies += Libraries.sttpClientZio
  )
  .dependsOn(openai % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val pekko = (projectMatrix in file("streaming/pekko"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientPekko
  )
  .dependsOn(openai % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val akka = (projectMatrix in file("streaming/akka"))
  .jvmPlatform(
    scalaVersions = scala2
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientAkka
  )
  .dependsOn(openai % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val ox = (projectMatrix in file("streaming/ox"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientOx
  )
  .dependsOn(openai % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val examples = (projectMatrix in file("examples"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir,
      "ch.qos.logback" % "logback-classic" % "1.5.38"
    ) ++ Libraries.sttpClientOx,
    publish / skip := true
  )
  .dependsOn(ox)

val compileDocumentation: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")
compileDocumentation :=
  (docs.jvm(scala3.head) / mdoc).toTask(" --out target/sttp-ai-docs").value

// verify the scala-cli `//> using dep` directives, which are not covered by the sbt build
val verifyExamplesCompileUsingScalaCli: TaskKey[Unit] = taskKey[Unit]("Verify that each example compiles using Scala CLI")
verifyExamplesCompileUsingScalaCli :=
  VerifyExamplesCompileUsingScalaCli(sLog.value, (examples.jvm(scala3.head) / sourceDirectory).value)

val verifyModelUpdateScriptsCompileUsingScalaCli: TaskKey[Unit] =
  taskKey[Unit]("Verify that each model update script compiles using Scala CLI")
verifyModelUpdateScriptsCompileUsingScalaCli :=
  VerifyExamplesCompileUsingScalaCli(sLog.value, file("model_update_scripts"))

lazy val docs = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("docs"),
    moduleName := "sttp-ai-docs",
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    mdocOut := file("generated-docs/out"),
    mdocExtraArguments := Seq(
      "--clean-target",
      "--disable-using-directives",
      "--exclude",
      ".venv",
      "--exclude",
      "_build",
      "--exclude",
      "adr",
      "--exclude",
      "plans",
      "--exclude",
      "superpowers"
    ),
    publishArtifact := false,
    name := "docs",
    evictionErrorLevel := Level.Info
  )
  .dependsOn(openai, fs2, zio, ox, pekko)
  .jvmPlatform(scalaVersions = scala3)
