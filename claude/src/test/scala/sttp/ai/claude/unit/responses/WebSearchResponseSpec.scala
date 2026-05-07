package sttp.ai.claude.unit.responses

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{Citation, ContentBlock}
import sttp.ai.claude.responses.MessageResponse
import sttp.ai.core.json.SnakePickle._

class WebSearchResponseSpec extends AnyFlatSpec with Matchers {

  private val successResponseJson =
    """{
      |  "id": "msg_01ABC",
      |  "type": "message",
      |  "role": "assistant",
      |  "model": "claude-haiku-4-5-20251001",
      |  "content": [
      |    {
      |      "type": "text",
      |      "text": "I'll search for that."
      |    },
      |    {
      |      "type": "server_tool_use",
      |      "id": "srvtoolu_01XYZ",
      |      "name": "web_search",
      |      "input": { "query": "claude shannon birth date" }
      |    },
      |    {
      |      "type": "web_search_tool_result",
      |      "tool_use_id": "srvtoolu_01XYZ",
      |      "content": [
      |        {
      |          "type": "web_search_result",
      |          "url": "https://en.wikipedia.org/wiki/Claude_Shannon",
      |          "title": "Claude Shannon - Wikipedia",
      |          "encrypted_content": "AAA",
      |          "page_age": "April 30, 2025"
      |        }
      |      ],
      |      "caller": { "type": "direct" }
      |    },
      |    {
      |      "type": "text",
      |      "text": "Claude Shannon was born on April 30, 1916.",
      |      "citations": [
      |        {
      |          "type": "web_search_result_location",
      |          "url": "https://en.wikipedia.org/wiki/Claude_Shannon",
      |          "title": "Claude Shannon - Wikipedia",
      |          "encrypted_index": "BBB",
      |          "cited_text": "Claude Elwood Shannon (April 30, 1916 ..."
      |        }
      |      ]
      |    }
      |  ],
      |  "stop_reason": "end_turn",
      |  "stop_sequence": null,
      |  "usage": {
      |    "input_tokens": 100,
      |    "output_tokens": 50,
      |    "server_tool_use": { "web_search_requests": 1 }
      |  }
      |}""".stripMargin

  private val errorResponseJson =
    """{
      |  "id": "msg_02DEF",
      |  "type": "message",
      |  "role": "assistant",
      |  "model": "claude-haiku-4-5-20251001",
      |  "content": [
      |    {
      |      "type": "web_search_tool_result",
      |      "tool_use_id": "srvtoolu_02DEF",
      |      "content": {
      |        "type": "web_search_tool_result_error",
      |        "error_code": "max_uses_exceeded"
      |      }
      |    }
      |  ],
      |  "stop_reason": "end_turn",
      |  "stop_sequence": null,
      |  "usage": { "input_tokens": 10, "output_tokens": 5 }
      |}""".stripMargin

  "MessageResponse with web_search content" should "deserialize server_tool_use blocks" in {
    val response = read[MessageResponse](successResponseJson)

    val serverToolUse = response.content.collectFirst { case s: ContentBlock.ServerToolUseContent => s }
    serverToolUse should be(defined)
    serverToolUse.get.id shouldBe "srvtoolu_01XYZ"
    serverToolUse.get.name shouldBe "web_search"
    serverToolUse.get.input("query").str shouldBe "claude shannon birth date"
  }

  it should "deserialize web_search_tool_result with results array" in {
    val response = read[MessageResponse](successResponseJson)

    val toolResult = response.content.collectFirst { case r: ContentBlock.WebSearchToolResultContent => r }
    toolResult should be(defined)
    toolResult.get.toolUseId shouldBe "srvtoolu_01XYZ"

    val results = toolResult.get.content match {
      case ContentBlock.WebSearchToolResult.Results(items) => items
      case other                                           => fail(s"Expected Results, got $other")
    }
    results should have size 1
    results.head.url shouldBe "https://en.wikipedia.org/wiki/Claude_Shannon"
    results.head.title shouldBe "Claude Shannon - Wikipedia"
    results.head.pageAge shouldBe Some("April 30, 2025")
    results.head.encryptedContent shouldBe Some("AAA")
  }

  it should "preserve the undocumented caller field" in {
    val response = read[MessageResponse](successResponseJson)
    val toolResult = response.content.collectFirst { case r: ContentBlock.WebSearchToolResultContent => r }.get

    toolResult.caller should be(defined)
    toolResult.caller.get("type").str shouldBe "direct"
  }

  it should "deserialize web_search_result_location citations on text blocks" in {
    val response = read[MessageResponse](successResponseJson)

    val finalText = response.content.collect { case t: ContentBlock.TextContent => t }.last
    finalText.citations should be(defined)
    finalText.citations.get should have size 1
    finalText.citations.get.head shouldBe a[Citation.WebSearchResultLocation]
    val cite = finalText.citations.get.head.asInstanceOf[Citation.WebSearchResultLocation]
    cite.url shouldBe "https://en.wikipedia.org/wiki/Claude_Shannon"
    cite.encryptedIndex shouldBe "BBB"
  }

  it should "deserialize web_search_tool_result error variant" in {
    val response = read[MessageResponse](errorResponseJson)

    val toolResult = response.content.collectFirst { case r: ContentBlock.WebSearchToolResultContent => r }
    toolResult should be(defined)

    toolResult.get.content match {
      case ContentBlock.WebSearchToolResult.Error(code) => code shouldBe "max_uses_exceeded"
      case other                                        => fail(s"Expected Error, got $other")
    }
  }

  "WebSearchToolResult content RW" should "round-trip Results variant" in {
    val original: ContentBlock.WebSearchToolResult =
      ContentBlock.WebSearchToolResult.Results(
        List(
          ContentBlock.WebSearchResult(
            url = "https://example.com",
            title = "Example",
            pageAge = Some("yesterday"),
            encryptedContent = Some("X")
          )
        )
      )
    read[ContentBlock.WebSearchToolResult](write(original)) shouldBe original
  }

  it should "round-trip Error variant" in {
    val original: ContentBlock.WebSearchToolResult = ContentBlock.WebSearchToolResult.Error("too_many_requests")
    read[ContentBlock.WebSearchToolResult](write(original)) shouldBe original
  }
}
