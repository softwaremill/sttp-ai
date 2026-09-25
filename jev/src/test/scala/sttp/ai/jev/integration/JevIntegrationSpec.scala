package sttp.ai.jev.integration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.jev.unit.Team
import sttp.ai.jev.{Choice, JevSyncClient, Noul, Score}

/** Integration tests against the real Jev API, with minimal token usage.
  *
  * To run: `export TYPESAFE_API_KEY=...` (or `JEV_API_KEY`) then `sbt "testOnly *JevIntegrationSpec"`. Skipped (not failed) when neither
  * variable is set.
  */
class JevIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll:

  private def apiKeySet: Boolean = Seq("TYPESAFE_API_KEY", "JEV_API_KEY").exists(sys.env.get(_).exists(_.nonEmpty))

  private val client: Option[JevSyncClient] = Option.when(apiKeySet)(JevSyncClient.fromEnv)

  override def afterAll(): Unit =
    client.foreach(_.close())
    super.afterAll()

  private def withClient(test: JevSyncClient => Unit): Unit =
    client match
      case Some(client) => test(client)
      case None         => cancel("TYPESAFE_API_KEY / JEV_API_KEY not set - skipping integration test")

  "JevSyncClient" should "answer a noul, a choice and a score question" in withClient { client =>
    val state = "My card was charged twice this month. Please fix this today."
    val isUrgent = Noul("The message conveys urgency")
    val whichTeam = Choice.of[Team]("Which team should handle this")
    val howFrustrated = Score("How frustrated the customer appears", "Calm", "Annoyed", "Furious")

    val response = client.ask(state, (isUrgent, whichTeam, howFrustrated))
    val (urgent, team, frustration) = response.answers

    urgent.probability should (be >= 0.0 and be <= 1.0)
    team.choice shouldBe team.probabilities.maxBy(_._2)._1
    team.probabilities.keySet shouldBe Team.values.toSet
    team.probabilities.values.sum shouldBe 1.0 +- 0.05
    frustration.probabilities should have size 3
    response.usage.inputTokens should be > 0
    response.requestId shouldBe defined
  }

  it should "list the available models" in withClient { client =>
    client.listModels().map(_.name) should contain("jev-latest")
  }
