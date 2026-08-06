package sttp.ai.claude.agent

import io.circe.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.core.agent.{AgentTool, IterationInfo}
import sttp.ai.claude.models.ClaudeModel
import sttp.apispec.Schema
import sttp.shared.Identity

object ClaudeAgentCapabilitySpecFixtures {
  val client: ClaudeClient = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
  val echoTool: AgentTool[Identity, _] = {
    val schema = parse("""{"type":"object"}""").toOption.get.as[Schema](sttp.apispec.circe.schemaDecoder).toOption.get
    AgentTool.dynamic("echo", "Echoes input", schema)(_ => "ok")
  }
}

class ClaudeAgentCapabilitySpec extends AnyFlatSpec with Matchers {
  import ClaudeAgentCapabilitySpecFixtures._

  "ClaudeAgent" should "reject deriveResponseSchema for Claude 3 Haiku (no StructuredOutput) at compile time" in {
    assertDoesNotCompile(
      """import io.circe.Codec
        import io.circe.generic.semiauto.deriveCodec
        import sttp.tapir.Schema
        case class Out(answer: String)
        object Out {
          implicit val codec: Codec[Out] = deriveCodec
          implicit val schema: Schema[Out] = Schema.derived
        }
        sttp.ai.claude.agent.ClaudeAgent
          .synchronous(sttp.ai.claude.agent.ClaudeAgentCapabilitySpecFixtures.client, sttp.ai.claude.models.ClaudeModel.Claude3Haiku)
          .deriveResponseSchema[Out]"""
    )
    assertCompiles(
      """import io.circe.Codec
        import io.circe.generic.semiauto.deriveCodec
        import sttp.tapir.Schema
        case class Out(answer: String)
        object Out {
          implicit val codec: Codec[Out] = deriveCodec
          implicit val schema: Schema[Out] = Schema.derived
        }
        sttp.ai.claude.agent.ClaudeAgent
          .synchronous(sttp.ai.claude.agent.ClaudeAgentCapabilitySpecFixtures.client, sttp.ai.claude.models.ClaudeModel.ClaudeSonnet5)
          .deriveResponseSchema[Out]"""
    )
  }

  it should "keep String model names working and allow tools on them" in {
    ClaudeAgent.synchronous(client, "claude-haiku-4-5-20251001").tools(echoTool): Unit
    succeed
  }

  it should "infer shared capabilities across a mixed-model hook" in {
    ClaudeAgent
      .synchronous(
        client,
        (info: IterationInfo) => if (info.isLastIteration) ClaudeModel.ClaudeOpus5 else ClaudeModel.ClaudeHaiku4_5
      )
      .tools(echoTool): Unit
    succeed
  }
}
