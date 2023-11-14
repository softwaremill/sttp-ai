import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.ossPublishSettings
import Dependencies._

val scala2 = List("2.13.12")
val scala3 = List("3.3.1")

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.openai"
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publish / skip := true, name := "sttp-openai", scalaVersion := scala2.head)
  .aggregate(allAgregates: _*)

lazy val allAgregates = core.projectRefs ++
  fs2.projectRefs ++
  zio.projectRefs ++
  pekko.projectRefs ++
  akka.projectRefs ++
  docs.projectRefs

lazy val core = (projectMatrix in file("core"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(
    libraryDependencies ++= Seq(Libraries.uPickle) ++ Libraries.sttpClient ++ Seq(Libraries.scalaTest)
  )
  .settings(commonSettings: _*)

lazy val fs2 = (projectMatrix in file("streaming/fs2"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientFs2
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val zio = (projectMatrix in file("streaming/zio"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies += Libraries.sttpClientZio
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val pekko = (projectMatrix in file("streaming/pekko"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientPekko
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val akka = (projectMatrix in file("streaming/akka"))
  .jvmPlatform(
    scalaVersions = scala2
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientAkka
  )
  .dependsOn(core % "compile->compile;test->test")

val compileDocs: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")
compileDocs := {
  (docs.jvm(scala2.head) / mdoc).toTask(" --out target/sttp-openai-docs").value
}

lazy val docs = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("README.md"),
    moduleName := "sttp-openai-docs",
    mdocOut := file("generated-docs/README.md"),
    mdocExtraArguments := Seq("--clean-target"),
    publishArtifact := false,
    name := "docs",
    evictionErrorLevel := Level.Info
  )
  .dependsOn(core, fs2, zio, akka, pekko)
  .jvmPlatform(scalaVersions = scala2)
