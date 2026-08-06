package sttp.ai.core.agent

import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.model.{AIModel, Capability}
import sttp.apispec.Schema
import sttp.client4.Backend
import sttp.monad.IdentityMonad
import sttp.shared.Identity

// Top-level so compile-check snippets can reference everything by stable, fully-qualified path.
object AgentBuilderCapabilitySpecFixtures {
  sealed abstract class TestModel(val value: String) extends AIModel
  case object FullModel extends TestModel("full") with Capability.ToolCalling with Capability.StructuredOutput
  case object BareModel extends TestModel("bare")

  val echoTool: AgentTool[Identity, _] = {
    val schema = parse("""{"type":"object"}""").toOption.get.as[Schema](sttp.apispec.circe.schemaDecoder).toOption.get
    AgentTool.dynamic("echo", "Echoes input", schema)(_ => "ok")
  }

  private val noopBackend: AgentBackend[Identity] = new AgentBackend[Identity] {
    val tools: Seq[AgentTool[Identity, _]] = Seq.empty
    val systemPrompt: Option[String] = None
    def sendRequest(
        history: ConversationHistory,
        backend: Backend[Identity],
        includeTools: Boolean,
        iterationInfo: IterationInfo
    ): Identity[AgentResponse] = AgentResponse("done", Seq.empty, StopReason.EndTurn)
  }

  def newBuilder[M <: AIModel]: AgentBuilder[Identity, M] =
    AgentBuilder[Identity, M](_ => noopBackend)(IdentityMonad)

  case class Out(answer: String)
  implicit val outCodec: io.circe.Codec[Out] = io.circe.generic.semiauto.deriveCodec
  implicit val outSchema: sttp.tapir.Schema[Out] = sttp.tapir.Schema.derived
  val outResponseSchema: ResponseSchema[Out] = ResponseSchema.derived[Out](None)
}

class AgentBuilderCapabilitySpec extends AnyFlatSpec with Matchers with EitherValues {
  import AgentBuilderCapabilitySpecFixtures._

  "AgentBuilder.tools" should "compile for a model with ToolCalling" in {
    newBuilder[FullModel.type].tools(echoTool): Unit
    succeed
  }

  it should "not compile for a model without ToolCalling" in {
    assertDoesNotCompile(
      """sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures
        .newBuilder[sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.BareModel.type]
        .tools(sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.echoTool)"""
    )
    // positive twin proving the snippet shape is valid apart from the model:
    assertCompiles(
      """sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures
        .newBuilder[sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.FullModel.type]
        .tools(sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.echoTool)"""
    )
  }

  "AgentBuilder.deriveResponseSchema" should "not compile for a model without StructuredOutput" in {
    assertDoesNotCompile(
      """import io.circe.Codec
        import io.circe.generic.semiauto.deriveCodec
        import sttp.tapir.Schema
        case class Out(answer: String)
        object Out {
          implicit val codec: Codec[Out] = deriveCodec
          implicit val schema: Schema[Out] = Schema.derived
        }
        sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures
          .newBuilder[sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.BareModel.type]
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
        sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures
          .newBuilder[sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.FullModel.type]
          .deriveResponseSchema[Out]"""
    )
  }

  "AgentBuilder.responseSchema" should "not compile for a model without StructuredOutput" in {
    assertDoesNotCompile(
      """sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures
        .newBuilder[sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.BareModel.type]
        .responseSchema(sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.outResponseSchema)"""
    )
    assertCompiles(
      """sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures
        .newBuilder[sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.FullModel.type]
        .responseSchema(sttp.ai.core.agent.AgentBuilderCapabilitySpecFixtures.outResponseSchema)"""
    )
  }

  "AgentBuilder" should "allow builder methods without capability requirements on any model" in {
    newBuilder[BareModel.type].maxIterations(3).systemPrompt("hi").build: Unit
    succeed
  }
}
