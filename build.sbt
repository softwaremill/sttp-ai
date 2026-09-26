import Dependencies.*
import com.softwaremill.Publish.{ossPublishSettings, updateDocs}
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.UpdateVersionInDocs

val scala2 = List("2.13.18", "2.12.20")
val scala3 = List("3.3.8")

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

lazy val javaOutputVersion = settingKey[String]("Java version to emit Scala 3 bytecode for")

commonSmlBuildSettings
ossPublishSettings

organization := "com.softwaremill.sttp.ai"
// -Yfuture-lazy-vals is backed by VarHandle, hence the Java output version; the JVM check skips the Native rows
javaOutputVersion := "11"
scalacOptions ++= Def.uncached {
  val isJvm = virtualAxes.?.value.forall(_.contains(VirtualAxis.jvm))
  if (isJvm && ScalaArtifacts.isScala3(scalaVersion.value))
    Seq("-Yfuture-lazy-vals", "-java-output-version", javaOutputVersion.value)
  else Seq.empty
}
// Suppress ScalaTest Assertion unused value warnings in tests; Scala 3 names the type org.scalatest.compatible.Assertion, and
// the compile-check assertions (assertDoesNotCompile etc.) expand to a Succeeded literal on Scala 2
Test / scalacOptions += "-Wconf:msg=unused value of type org.scalatest.(compatible.Assertion|Assertion|Succeeded.type):silent"
Test / scalacOptions += "-Wconf:msg=discarded non-Unit value of type org.scalatest.(compatible.)?Assertion:silent"
// 2.12 has no `scala.annotation.unused` to suppress warnings per-site (see sttp.ai.core.compat.unused), so silence the category there;
// 2.13 keeps full unused checking. 2.12's -Ywarn-value-discard also fires on the explicit `: Unit` ascriptions which satisfy
// -Wnonunit-statement on 2.13/3 (those exempt them), so value discards are only checked on 2.13/3. 2.12 also reports the deprecated
// FileData fields at the codec derived for them (2.13 reports them at the fields, where FileData's @nowarn covers them)
scalacOptions ++= (if (scalaVersion.value.startsWith("2.12"))
                     Seq(
                       "-Wconf:msg=never used:silent",
                       "-Wconf:cat=w-flag-value-discard:silent",
                       "-Wconf:cat=deprecation&site=sttp.ai.openai.json.OpenAIDerivedCodecs.fileDataCodec.*:silent"
                     )
                   else Seq.empty)
// 2.12's missing-interpolator lint resolves in-scope names inside plain string literals ("$defs", "$ref"), which errors with
// "recursive value needs type" when the name is the val being defined (fixed in 2.13) - drop the lint on 2.12 only
scalacOptions := (if (scalaVersion.value.startsWith("2.12")) scalacOptions.value.filterNot(_ == "-Xlint:missing-interpolator")
                  else scalacOptions.value)

lazy val root = (project in file("."))
  .settings(
    publish / skip := true,
    name := "sttp-ai",
    scalaVersion := scala2.head,
    updateDocs := Def.uncached(Def.taskDyn {
      val files = UpdateVersionInDocs(sLog.value, organization.value, version.value)
      Def.task {
        (docs.jvm(scala3.head) / mdoc).toTask("").value
        files ++ Seq(file("generated-docs/out"))
      }
    }.value),
    // defined on the root project only: in sbt 2, bare settings in build.sbt apply to every subproject
    compileDocumentation := (docs.jvm(scala3.head) / mdoc).toTask(" --out target/sttp-ai-docs").value,
    verifyExamplesCompileUsingScalaCli :=
      Def.uncached(VerifyExamplesCompileUsingScalaCli(sLog.value, (examples.jvm(scala3.head) / sourceDirectory).value)),
    verifyModelUpdateScriptsCompileUsingScalaCli :=
      Def.uncached(VerifyExamplesCompileUsingScalaCli(sLog.value, file("model_update_scripts")))
  )
  .aggregate(allAgregates *)

lazy val allAgregates = core.projectRefs ++
  openai.projectRefs ++
  claude.projectRefs ++
  gemini.projectRefs ++
  jev.projectRefs ++
  fs2.projectRefs ++
  zio.projectRefs ++
  pekko.projectRefs ++
  akka.projectRefs ++
  ox.projectRefs ++
  mcp.projectRefs ++
  agentTestkit.projectRefs ++
  examples.projectRefs ++
  docs.projectRefs

lazy val core = (projectMatrix in file("core"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .nativePlatform(
    scalaVersions = scala3
  )
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
  .dependsOn(core % "compile->compile;test->test")

lazy val claude = (projectMatrix in file("claude"))
  .jvmPlatform(
    scalaVersions = scala3 ++ scala2 // Scala 3 first priority
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(
    libraryDependencies ++=
      Seq(Libraries.tapirApispecDocs.value) ++
        Libraries.sttpApispec.value ++ Libraries.circe.value ++
        Libraries.sttpClient.value ++ Seq(Libraries.scalaTest.value),
    libraryDependencies ++= (if (scalaVersion.value.startsWith("2.")) Seq(Libraries.circeGenericExtras.value) else Seq.empty)
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val gemini = (projectMatrix in file("gemini"))
  .jvmPlatform(
    scalaVersions = scala3 ++ scala2 // Scala 3 first priority
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(
    libraryDependencies ++=
      Seq(Libraries.tapirApispecDocs.value) ++
        Libraries.sttpApispec.value ++ Libraries.circe.value ++
        Libraries.sttpClient.value ++ Seq(Libraries.scalaTest.value),
    libraryDependencies ++= (if (scalaVersion.value.startsWith("2.")) Seq(Libraries.circeGenericExtras.value) else Seq.empty)
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val jev = (projectMatrix in file("jev"))
  .jvmPlatform(
    scalaVersions = scala3 // match types and union types: no Scala 2 cross-build
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(
    libraryDependencies ++= Libraries.circe.value ++ Libraries.sttpClient.value ++ Seq(Libraries.scalaTest.value)
  )
  .dependsOn(core)

lazy val agentTestkit = (projectMatrix in file("agent-testkit"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .nativePlatform(
    scalaVersions = scala3
  )
  .settings(
    name := "agent-testkit",
    libraryDependencies += Libraries.scalaTestProvided.value
  )
  .dependsOn(core)

lazy val fs2 = (projectMatrix in file("streaming/fs2"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(
    libraryDependencies ++= Libraries.sttpClientFs2
  )
  .dependsOn(
    openai % "compile->compile;test->test",
    claude % "compile->compile;test->test",
    gemini % "compile->compile;test->test"
  )

lazy val zio = (projectMatrix in file("streaming/zio"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(
    libraryDependencies += Libraries.sttpClientZio
  )
  .dependsOn(
    openai % "compile->compile;test->test",
    claude % "compile->compile;test->test",
    gemini % "compile->compile;test->test"
  )

lazy val pekko = (projectMatrix in file("streaming/pekko"))
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .settings(
    libraryDependencies ++= Libraries.sttpClientPekko
  )
  .dependsOn(
    openai % "compile->compile;test->test",
    claude % "compile->compile;test->test",
    gemini % "compile->compile;test->test"
  )

lazy val akka = (projectMatrix in file("streaming/akka"))
  .jvmPlatform(
    scalaVersions = scala2
  )
  .settings(
    libraryDependencies ++= Libraries.sttpClientAkka
  )
  .dependsOn(
    openai % "compile->compile;test->test",
    claude % "compile->compile;test->test",
    gemini % "compile->compile;test->test"
  )

lazy val ox = (projectMatrix in file("streaming/ox"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(javaOutputVersion := "21") // ox requires JDK 21
  .settings(
    libraryDependencies ++= Libraries.sttpClientOx
  )
  .dependsOn(
    openai % "compile->compile;test->test",
    claude % "compile->compile;test->test",
    gemini % "compile->compile;test->test"
  )

lazy val mcp = (projectMatrix in file("mcp"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(javaOutputVersion := "21") // chimp and tapir-netty-server-sync, which build on ox, require JDK 21
  .settings(
    libraryDependencies ++= Seq(
      Libraries.chimpClient,
      Libraries.chimpServer,
      Libraries.tapirNettyServerSync,
      Libraries.scalaTest.value,
      Libraries.logbackTest
    )
  )
  .dependsOn(core % "compile->compile;test->test", openai % "test->compile", claude % "test->compile")

lazy val examples = (projectMatrix in file("examples"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir,
      "ch.qos.logback" % "logback-classic" % "1.6.3"
    ) ++ Libraries.sttpClientOx,
    publish / skip := true
  )
  .dependsOn(ox, jev)

lazy val compileDocumentation: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")

// verify the scala-cli `//> using dep` directives, which are not covered by the sbt build
lazy val verifyExamplesCompileUsingScalaCli: TaskKey[Unit] = taskKey[Unit]("Verify that each example compiles using Scala CLI")

lazy val verifyModelUpdateScriptsCompileUsingScalaCli: TaskKey[Unit] =
  taskKey[Unit]("Verify that each model update script compiles using Scala CLI")

lazy val docs = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
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
    evictionErrorLevel := Level.Info,
    // the agent-testkit's scalatest dependency is Provided, so the docs snippets using its matchers need scalatest explicitly
    libraryDependencies += Libraries.scalaTestProvided.value
  )
  .dependsOn(openai, claude, gemini, jev, fs2, zio, ox, pekko, mcp, agentTestkit)
  .jvmPlatform(scalaVersions = scala3)
