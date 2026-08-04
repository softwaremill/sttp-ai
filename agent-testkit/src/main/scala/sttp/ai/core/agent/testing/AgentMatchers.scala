package sttp.ai.core.agent.testing

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.matchers.{MatchResult, Matcher}
import sttp.ai.core.agent.{AgentResult, FinishReason}

/** Scalatest matchers over [[RecordedInteractions]] (what was sent to the model) and [[sttp.ai.core.agent.AgentResult]] (what the agent
  * did). Mix into a spec, or `import AgentMatchers._` (one or the other — doing both is ambiguous under Scala 3). Everything asserted here
  * is also reachable through the plain [[RecordedInteractions]] accessors, for use with other test frameworks.
  */
trait AgentMatchers {

  /** Asserts that a user prompt exactly equal to `expected` was sent to the model. */
  def haveReceivedPrompt(expected: String): Matcher[RecordedInteractions] = Matcher { recording =>
    val prompts = recording.userPrompts
    MatchResult(
      prompts.contains(expected),
      s"""no user prompt equal to "$expected" was sent; sent user prompts: ${describe(prompts)}""",
      s"""a user prompt equal to "$expected" was sent"""
    )
  }

  /** Asserts that a tool named `name` was offered to the model on any request. */
  def haveOfferedTool(name: String): Matcher[RecordedInteractions] = Matcher { recording =>
    val names = recording.offeredTools.map(_.name)
    MatchResult(
      names.contains(name),
      s"""tool "$name" was not offered to the model; ${describeOffered(names)}""",
      s"""tool "$name" was offered to the model"""
    )
  }

  /** Asserts that tool `name` was offered with exactly `expectedSchema` (compared structurally as JSON). */
  def haveOfferedToolWithSchema(name: String, expectedSchema: Json): Matcher[RecordedInteractions] = Matcher { recording =>
    recording.offeredTools.find(_.name == name) match {
      case None =>
        MatchResult(
          matches = false,
          s"""tool "$name" was not offered to the model; ${describeOffered(recording.offeredTools.map(_.name))}""",
          s"""tool "$name" was offered to the model"""
        )
      case Some(tool) =>
        MatchResult(
          tool.schema == expectedSchema,
          s"""schema of tool "$name" was ${tool.schema.noSpaces}, expected ${expectedSchema.noSpaces}""",
          s"""schema of tool "$name" was ${expectedSchema.noSpaces}"""
        )
    }
  }

  /** Asserts that the agent called tool `name` at least once. */
  def haveCalledTool(name: String): Matcher[AgentResult[_]] = Matcher { result =>
    val names = result.toolCalls.map(_.toolName)
    MatchResult(
      names.contains(name),
      s"""tool "$name" was not called; called tools: ${describe(names)}""",
      s"""tool "$name" was called"""
    )
  }

  /** Asserts that the agent called tool `name` with the given arguments. Both sides are parsed and compared as JSON, so whitespace and
    * field order don't matter.
    */
  def haveCalledToolWith(name: String, expectedArgsJson: String): Matcher[AgentResult[_]] = Matcher { result =>
    parse(expectedArgsJson) match {
      case Left(error) =>
        MatchResult(
          matches = false,
          s"""expected arguments for tool "$name" are not valid JSON: ${error.getMessage}""",
          s"""expected arguments for tool "$name" are valid JSON"""
        )
      case Right(expected) =>
        val inputs = result.toolCalls.filter(_.toolName == name).map(_.input)
        MatchResult(
          inputs.exists(input => parse(input).toOption.contains(expected)),
          s"""tool "$name" was not called with ${expected.noSpaces}; recorded calls of "$name": ${describe(inputs)}""",
          s"""tool "$name" was called with ${expected.noSpaces}"""
        )
    }
  }

  /** Asserts on the agent's finish reason. */
  def haveFinishedWith(expected: FinishReason): Matcher[AgentResult[_]] = Matcher { result =>
    MatchResult(
      result.finishReason == expected,
      s"agent finished with ${result.finishReason}, expected $expected",
      s"agent finished with $expected"
    )
  }

  private def describe(values: Seq[String]): String =
    if (values.isEmpty) "none" else values.mkString("\"", "\", \"", "\"")

  private def describeOffered(names: Seq[String]): String =
    if (names.isEmpty) "no request offered any tools (note: the agent loop withholds tools on the last allowed iteration)"
    else s"offered tools: ${describe(names)}"
}

object AgentMatchers extends AgentMatchers
