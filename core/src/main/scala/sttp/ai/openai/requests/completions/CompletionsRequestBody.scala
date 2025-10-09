package sttp.openai.requests.completions

import sttp.openai.json.SnakePickle
import ujson.Str

object CompletionsRequestBody {

  /** @param model
    *   ID of the model to use.
    * @param prompt
    *   The prompt(s) to generate completions for, encoded as a string, array of strings, array of tokens, or array of token arrays.
    * @param suffix
    *   The suffix that comes after a completion of inserted text.
    * @param maxTokens
    *   The maximum number of tokens to generate in the completion.
    * @param temperature
    *   What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like
    *   0.2 will make it more focused and deterministic.
    * @param topP
    *   An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p
    *   probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.
    * @param n
    *   How many completions to generate for each prompt.
    * @param logprobs
    *   Include the log probabilities on the logprobs most likely tokens, as well the chosen tokens. For example, if logprobs is 5, the API
    *   will return a list of the 5 most likely tokens.
    * @param echo
    *   Echo back the prompt in addition to the completion.
    * @param stop
    *   Up to 4 sequences where the API will stop generating further tokens. The returned text will not contain the stop sequence.
    * @param presencePenalty
    *   Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear in the text so far, increasing the
    *   model's likelihood to talk about new topics.
    * @param frequencyPenalty
    *   Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing
    *   the model's likelihood to repeat the same line verbatim.
    * @param bestOf
    *   Generates `best_of` completions server-side and returns the "best" (the one with the highest log probability per token).
    * @param logitBias
    *   Modify the likelihood of specified tokens appearing in the completion.
    * @param user
    *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/completions/create]]
    */
  case class CompletionsBody(
      model: CompletionModel,
      prompt: Option[Prompt] = None,
      suffix: Option[String] = None,
      maxTokens: Option[Int] = None,
      temperature: Option[Double] = None,
      topP: Option[Double] = None,
      n: Option[Int] = None,
      logprobs: Option[Int] = None,
      echo: Option[Boolean] = None,
      stop: Option[Stop] = None,
      presencePenalty: Option[Double] = None,
      frequencyPenalty: Option[Double] = None,
      bestOf: Option[Int] = None,
      logitBias: Option[Map[String, Float]] = None,
      user: Option[String] = None
  )

  object CompletionsBody {
    implicit val completionBodyW: SnakePickle.Writer[CompletionsBody] = SnakePickle.macroW[CompletionsBody]
  }

  sealed abstract class CompletionModel(val value: String)

  object CompletionModel {

    implicit val completionModelRW: SnakePickle.ReadWriter[CompletionModel] = SnakePickle
      .readwriter[ujson.Value]
      .bimap[CompletionModel](
        model => SnakePickle.writeJs(model.value),
        jsonValue =>
          SnakePickle.read[ujson.Value](jsonValue) match {
            case Str(value) =>
              byCompletionModelValue.getOrElse(value, CustomCompletionModel(value))
            case e => throw new Exception(s"Could not deserialize: $e")
          }
      )

    case object Babbage002 extends CompletionModel("babbage-002")
    case object Davinci002 extends CompletionModel("davinci-002")
    case object GPT35TurboInstruct extends CompletionModel("gpt-3.5-turbo-instruct")
    case class CustomCompletionModel(customCompletionModel: String) extends CompletionModel(customCompletionModel)

    val values: Set[CompletionModel] =
      Set(
        Babbage002,
        Davinci002,
        GPT35TurboInstruct
      )

    private val byCompletionModelValue = values.map(model => model.value -> model).toMap
  }

  sealed trait Prompt
  object Prompt {
    implicit val promptRW: SnakePickle.Writer[Prompt] = SnakePickle
      .writer[ujson.Value]
      .comap[Prompt] {
        case SinglePrompt(value)    => SnakePickle.writeJs(value)
        case MultiplePrompt(values) => SnakePickle.writeJs(values)
      }
  }
  case class SinglePrompt(value: String) extends Prompt
  case class MultiplePrompt(values: Seq[String]) extends Prompt
}
