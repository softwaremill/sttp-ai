//> using dep com.softwaremill.sttp.ai::jev:0.11.2

// remember to set the TYPESAFE_API_KEY (or JEV_API_KEY) env variable!
// run with: TYPESAFE_API_KEY=... scala-cli run JevTicketTriageExample.scala

import sttp.ai.jev.*

enum Team:
  case Billing, Technical, Sales

enum Frustration:
  case Calm, Annoyed, Furious

object JevTicketTriageExample extends App:
  // Reads the API key from the environment
  val client = JevSyncClient.fromEnv

  val ticket = "I've been trying to connect my Stripe account for 3 days and it keeps failing. I'm losing sales. Please help ASAP."

  try
    // One request, three typed questions: the answers are a tuple typed by the questions
    val response = client.ask(
      ticket,
      (
        Choice.of[Team]("Which team should handle this ticket"),
        Score.of[Frustration](
          "How frustrated the customer is",
          {
            case Frustration.Calm    => "Calm, just stating facts"
            case Frustration.Annoyed => "Frustrated but civil"
            case Frustration.Furious => "Very angry, strong language"
          }
        ),
        Noul("The customer needs a reply today")
      )
    )
    val (team, frustration, urgent) = response.answers

    println(s"Team: ${team.choice} (confidence ${team.confidence})")
    team.probabilities.toSeq.sortBy(-_._2).foreach((t, p) => println(s"  $t: $p"))
    println(s"Frustration: ${frustration.mostLikely} (expected level ${frustration.score})")
    println(s"Urgent: ${urgent.probability}")
    println(s"Model: ${response.model}, usage: ${response.usage}")
  finally client.close()
