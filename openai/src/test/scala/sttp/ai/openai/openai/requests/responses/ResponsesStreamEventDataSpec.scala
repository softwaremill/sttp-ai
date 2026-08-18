package sttp.ai.openai.requests.responses

import io.circe.parser.{decode, parse}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.ResponsesStreamEventFixture
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

class ResponsesStreamEventDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  private def decodeEvent(json: String): ResponsesStreamEvent = decode[ResponsesStreamEvent](json).value

  "Every registered event type" should "decode to a concrete case carrying that type" in
    responsesStreamEventDecoders.keySet.foreach { eventType =>
      withClue(s"event type '$eventType': ") {
        val json = ResponsesStreamEventFixture.minimalByType.getOrElse(
          eventType,
          fail(s"no fixture for '$eventType' - add one to ResponsesStreamEventFixture.minimalByType")
        )
        val event = decodeEvent(json)
        event shouldNot be(a[ResponsesStreamEvent.Unknown])
        event.`type` shouldBe eventType
        event.sequenceNumber shouldBe ResponsesStreamEventFixture.SequenceNumber
      }
    }

  it should "cover every documented event type" in {
    responsesStreamEventDecoders should have size 58
    ResponsesStreamEventFixture.minimalByType.keySet shouldBe responsesStreamEventDecoders.keySet
  }

  "A dotted event type" should "dispatch to the matching case with all fields decoded" in {
    decodeEvent(ResponsesStreamEventFixture.jsonOutputTextDeltaWithLogprobs) shouldBe ResponsesStreamEvent.OutputTextDelta(
      contentIndex = 0,
      delta = "Hi",
      itemId = "item_1",
      outputIndex = 0,
      sequenceNumber = 1,
      logprobs = List(ResponsesStreamEvent.LogProb("Hi", -0.01, List(ResponsesStreamEvent.TopLogProb(Some("Hi"), Some(-0.01)))))
    )
  }

  it should "default logprobs to empty when the field is absent" in {
    decodeEvent(ResponsesStreamEventFixture.jsonOutputTextDeltaNoLogprobs) match {
      case delta: ResponsesStreamEvent.OutputTextDelta => delta.logprobs shouldBe empty
      case other                                       => fail(s"expected an OutputTextDelta, got $other")
    }
  }

  "The unprefixed error event type" should "decode to Error" in {
    decodeEvent(ResponsesStreamEventFixture.jsonError) shouldBe ResponsesStreamEvent.Error(
      message = "Something went wrong.",
      sequenceNumber = 1,
      code = Some("server_error"),
      param = None
    )
  }

  "An event type introduced after this release" should "decode to Unknown rather than failing the stream" in {
    val json = ResponsesStreamEventFixture.jsonUnknownEventType
    val event = decodeEvent(json)

    event shouldBe ResponsesStreamEvent.Unknown("response.brand_new.delta", parse(json).value)
    event.`type` shouldBe "response.brand_new.delta"
    event.sequenceNumber shouldBe ResponsesStreamEventFixture.SequenceNumber
  }

  it should "report -1 as the sequence number when the frame carries none" in {
    decodeEvent("""{"type":"response.brand_new.delta"}""").sequenceNumber shouldBe -1
  }

  "A known event type with a malformed payload" should "fail rather than fall back to Unknown" in {
    decode[ResponsesStreamEvent](ResponsesStreamEventFixture.jsonOutputTextDeltaMissingItemId).left.value.getMessage should
      include("item_id")
  }

  "A frame without a type" should "fail to decode" in {
    decode[ResponsesStreamEvent]("""{"sequence_number":1}""").isLeft shouldBe true
  }

  "response.created" should "decode the nested response body" in {
    decodeEvent(ResponsesStreamEventFixture.jsonCreated) match {
      case created: ResponsesStreamEvent.Created =>
        created.response.id shouldBe "resp_67ccd3a9da748190baa7f1570fe91ac604becb25c45c1d41"
        created.response.status shouldBe "completed"
        created.response.output should have size 1
      case other => fail(s"expected a Created, got $other")
    }
  }

  "response.output_item.added" should "decode a modelled output item" in {
    decodeEvent(ResponsesStreamEventFixture.jsonOutputItemAddedFunctionCall) shouldBe ResponsesStreamEvent.OutputItemAdded(
      item = ResponsesResponseBody.OutputItem.FunctionCall("{}", "call_1", "get_weather", "fc_1", "completed"),
      outputIndex = 0,
      sequenceNumber = 1
    )
  }

  it should "decode a custom_tool_call output item" in {
    decodeEvent(ResponsesStreamEventFixture.jsonOutputItemAddedCustomToolCall) shouldBe ResponsesStreamEvent.OutputItemAdded(
      item = ResponsesResponseBody.OutputItem.CustomToolCall("call_2", "echo hi", "shell", "ctc_1"),
      outputIndex = 0,
      sequenceNumber = 1
    )
  }

  it should "decode an unmodelled output item to OutputItem.Unknown instead of failing" in {
    decodeEvent(ResponsesStreamEventFixture.jsonOutputItemAddedUnknownItem) match {
      case added: ResponsesStreamEvent.OutputItemAdded =>
        added.item shouldBe ResponsesResponseBody.OutputItem.Unknown(
          "shell_call",
          parse(ResponsesStreamEventFixture.jsonUnknownItem).value
        )
      case other => fail(s"expected an OutputItemAdded, got $other")
    }
  }

  "response.content_part.added" should "decode all three content part kinds" in {
    import ResponsesStreamEventFixture._

    def partOf(json: String): ResponsesResponseBody.OutputContent = decodeEvent(jsonContentPartAdded(json)) match {
      case added: ResponsesStreamEvent.ContentPartAdded => added.part
      case other                                        => fail(s"expected a ContentPartAdded, got $other")
    }

    partOf(jsonOutputTextPart) shouldBe ResponsesResponseBody.OutputContent.OutputText(Nil, "Hi")
    partOf(jsonRefusalPart) shouldBe ResponsesResponseBody.OutputContent.Refusal("I cannot help with that.")
    partOf(jsonReasoningTextPart) shouldBe ResponsesResponseBody.OutputContent.ReasoningText("Let me think.")
  }

  it should "decode an unmodelled content part to OutputContent.Unknown instead of failing" in {
    import ResponsesStreamEventFixture._

    decodeEvent(jsonContentPartAdded(jsonUnknownPart)) match {
      case added: ResponsesStreamEvent.ContentPartAdded =>
        added.part shouldBe ResponsesResponseBody.OutputContent.Unknown("output_audio", parse(jsonUnknownPart).value)
      case other => fail(s"expected a ContentPartAdded, got $other")
    }
  }

  it should "still fail on a modelled content part with a malformed payload" in {
    decode[ResponsesStreamEvent](
      ResponsesStreamEventFixture.jsonContentPartAdded("""{"type":"refusal"}""")
    ).left.value.getMessage should include("refusal")
  }

  "response.output_text.annotation.added" should "decode every annotation kind, and a null annotation" in {
    import ResponsesResponseBody.OutputContent.Annotation._
    import ResponsesStreamEventFixture._

    def annotationOf(json: String): Option[ResponsesResponseBody.OutputContent.Annotation] =
      decodeEvent(jsonAnnotationAdded(json)) match {
        case added: ResponsesStreamEvent.OutputTextAnnotationAdded => added.annotation
        case other                                                 => fail(s"expected an OutputTextAnnotationAdded, got $other")
      }

    annotationOf(jsonAnnotationFileCitation) shouldBe Some(FileCitation("file_1", "a.pdf", 3))
    annotationOf(jsonAnnotationUrlCitation) shouldBe Some(UrlCitation(5, 0, "Example", "https://example.com"))
    annotationOf(jsonAnnotationContainerFileCitation) shouldBe Some(ContainerFileCitation("c_1", 5, "file_2", "b.txt", 0))
    annotationOf(jsonAnnotationFilePath) shouldBe Some(FilePath("file_3", 1))
    annotationOf("null") shouldBe None
  }

  "response.reasoning_summary_part.done" should "decode the optional incomplete status" in {
    decodeEvent(ResponsesStreamEventFixture.jsonReasoningSummaryPartDoneIncomplete) shouldBe
      ResponsesStreamEvent.ReasoningSummaryPartDone(
        itemId = "item_1",
        outputIndex = 0,
        part = ResponsesResponseBody.OutputItem.SummaryText("Considered the request."),
        sequenceNumber = 1,
        summaryIndex = 0,
        status = Some("incomplete")
      )
  }

  "response.shell_call_output_content.done" should "decode both shell outcomes" in {
    def outcomesOf(json: String) = decodeEvent(json) match {
      case done: ResponsesStreamEvent.ShellCallOutputContentDone => done.output.map(_.outcome)
      case other                                                 => fail(s"expected a ShellCallOutputContentDone, got $other")
    }

    outcomesOf(ResponsesStreamEventFixture.jsonShellCallOutputContentDoneExit) shouldBe
      List(ResponsesStreamEvent.ShellOutcome.Exit(0))
    outcomesOf(ResponsesStreamEventFixture.jsonShellCallOutputContentDoneTimeout) shouldBe
      List(ResponsesStreamEvent.ShellOutcome.Timeout())
  }

  "response.shell_call_command.delta" should "decode the obfuscation field when present" in {
    decodeEvent(ResponsesStreamEventFixture.jsonShellCallCommandDeltaWithObfuscation) shouldBe
      ResponsesStreamEvent.ShellCallCommandDelta(
        commandIndex = 0,
        delta = "ls",
        outputIndex = 0,
        sequenceNumber = 1,
        obfuscation = Some("abc")
      )
  }

  "response.image_generation_call.partial_image" should "leave the optional image settings empty when absent" in {
    decodeEvent(ResponsesStreamEventFixture.jsonImageGenerationCallPartialImage) shouldBe
      ResponsesStreamEvent.ImageGenerationCallPartialImage(
        itemId = "item_1",
        outputIndex = 0,
        partialImageB64 = "aGk=",
        partialImageIndex = 0,
        sequenceNumber = 1
      )
  }

  "Token usage" should "be reported on the terminal lifecycle events" in {
    val completed = decodeEvent(ResponsesStreamEventFixture.jsonCompleted)

    completed.usage shouldBe Some(
      ResponsesResponseBody.Usage(
        inputTokens = 328,
        outputTokens = 52,
        totalTokens = 380,
        inputTokensDetails = Some(ResponsesResponseBody.InputTokensDetails(cachedTokens = Some(0))),
        outputTokensDetails = Some(ResponsesResponseBody.OutputTokensDetails(reasoningTokens = Some(0)))
      )
    )
  }

  it should "include the full token breakdown, cache writes included" in {
    decodeEvent(ResponsesStreamEventFixture.jsonCompletedWithFullUsage).usage shouldBe Some(
      ResponsesResponseBody.Usage(
        inputTokens = 328,
        outputTokens = 52,
        totalTokens = 380,
        inputTokensDetails = Some(ResponsesResponseBody.InputTokensDetails(cachedTokens = Some(64), cacheWriteTokens = Some(16))),
        outputTokensDetails = Some(ResponsesResponseBody.OutputTokensDetails(reasoningTokens = Some(12)))
      )
    )
  }

  it should "be absent on events that do not carry a response snapshot" in {
    decodeEvent(ResponsesStreamEventFixture.jsonOutputTextDeltaNoLogprobs).usage shouldBe None
    decodeEvent(ResponsesStreamEventFixture.jsonError).usage shouldBe None
    decodeEvent(ResponsesStreamEventFixture.jsonUnknownEventType).usage shouldBe None
  }

  it should "be takeable from a full stream as the last reported value" in {
    val events = ResponsesStreamEventFixture.sseSequence.map(decodeEvent)

    events.flatMap(_.usage).lastOption.map(_.totalTokens) shouldBe Some(380)
  }
}
