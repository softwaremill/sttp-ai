package sttp.openai.requests.responses

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.json.SnakePickle
import sttp.openai.requests.responses.InputItemsListResponseBody.InputItem

class InputItemsListDataSpec extends AnyFlatSpec with Matchers {

  "InputItemsListResponseBody" should "deserialize simple input message correctly" in {
    // given
    val jsonResponse =
      """{
        |  "object": "list",
        |  "data": [
        |    {
        |      "id": "msg_abc123",
        |      "type": "message",
        |      "status": "in_progress",
        |      "role": "user",
        |      "content": [
        |        {
        |          "type": "input_text",
        |          "text": "Tell me a three sentence bedtime story about a unicorn."
        |        }
        |      ]
        |    }
        |  ],
        |  "first_id": "msg_abc123",
        |  "last_id": "msg_abc123",
        |  "has_more": false
        |}""".stripMargin

    // when
    val deserializedResponse = SnakePickle.read[InputItemsListResponseBody](jsonResponse)

    // then
    deserializedResponse.`object` shouldBe "list"
    deserializedResponse.firstId shouldBe "msg_abc123"
    deserializedResponse.lastId shouldBe "msg_abc123"
    deserializedResponse.hasMore shouldBe false
    deserializedResponse.data should have size 1

    val inputMessage = deserializedResponse.data.head.asInstanceOf[InputItem.InputMessage]
    inputMessage.id shouldBe "msg_abc123"
    inputMessage.role shouldBe "user"
    inputMessage.content should have size 1

    val inputText = inputMessage.content.head.asInstanceOf[InputItem.InputContent.InputText]
    inputText.text shouldBe "Tell me a three sentence bedtime story about a unicorn."
  }
}
