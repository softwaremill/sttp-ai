import Dependencies.*
import com.softwaremill.Publish.ossPublishSettings
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings

val scala2 = List("2.13.16")
val scala3 = List("3.3.6")

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
  .settings(
    libraryDependencies ++= Seq(
      Libraries.tapirApispecDocs,
      Libraries.uJsonCirce,
      Libraries.uPickle
    ) ++ Libraries.sttpApispec ++ Libraries.sttpClient ++ Seq(Libraries.scalaTest)
  )
  .settings(commonSettings: _*)

lazy val claude = (projectMatrix in file("claude"))
  .jvmPlatform(
    scalaVersions = scala3 ++ scala2 // Scala 3 first priority
  )
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Libraries.tapirApispecDocs,
      Libraries.uJsonCirce,
      Libraries.uPickle
    ) ++ Libraries.sttpApispec ++ Libraries.sttpClient ++ Seq(Libraries.scalaTest)
  )

lazy val fs2 = (projectMatrix in file("streaming/fs2"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientFs2
  )
  .dependsOn(core % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val zio = (projectMatrix in file("streaming/zio"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies += Libraries.sttpClientZio
  )
  .dependsOn(core % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val pekko = (projectMatrix in file("streaming/pekko"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientPekko
  )
  .dependsOn(core % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val akka = (projectMatrix in file("streaming/akka"))
  .jvmPlatform(
    scalaVersions = scala2
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientAkka
  )
  .dependsOn(core % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val ox = (projectMatrix in file("streaming/ox"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Libraries.sttpClientOx
  )
  .dependsOn(core % "compile->compile;test->test", claude % "compile->compile;test->test")

lazy val examples = (projectMatrix in file("examples"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.46",
      "ch.qos.logback" % "logback-classic" % "1.5.6"
    ) ++ Libraries.sttpClientOx,
    publish / skip := true
  )
  .dependsOn(ox)

val compileDocumentation: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")
compileDocumentation :=
  (docs.jvm(scala3.head) / mdoc).toTask(" --out target/sttp-openai-docs").value

lazy val docs = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("README.md"),
    moduleName := "sttp-openai-docs",
    mdocOut := file("generated-docs/README.md"),
    mdocExtraArguments := Seq("--clean-target", "--disable-using-directives"),
    publishArtifact := false,
    name := "docs",
    evictionErrorLevel := Level.Info
  )
  .dependsOn(core, fs2, zio, ox, pekko)
  .jvmPlatform(scalaVersions = scala3)
