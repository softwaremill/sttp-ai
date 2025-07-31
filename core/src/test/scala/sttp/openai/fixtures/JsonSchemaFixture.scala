package sttp.openai.fixtures

object JsonSchemaFixture {

  val stringSchema: String =
    """{
      |  "type": "json_schema",
      |  "json_schema": {
      |    "name": "testString",
      |    "strict": true,
      |    "description": "description",
      |    "schema": {
      |      "type": "string"
      |    }
      |  }
      |}""".stripMargin

  val stringSchemaWithoutStrictField: String =
    """{
      |  "type": "json_schema",
      |  "json_schema": {
      |    "name": "testString",
      |    "schema": {
      |      "type": "string"
      |    }
      |  }
      |}""".stripMargin

  val numberSchema: String =
    """{
      |  "type": "json_schema",
      |  "json_schema": {
      |    "name": "testNumber",
      |    "strict": true,
      |    "schema": {
      |      "type": "number"
      |    }
      |  }
      |}""".stripMargin

  val objectSchema: String =
    """{
      |  "type": "json_schema",
      |  "json_schema": {
      |    "name": "testObject",
      |    "strict": true,
      |    "schema": {
      |      "additionalProperties": false,
      |      "required": [
      |        "foo",
      |        "bar"
      |      ],
      |      "type": "object",
      |      "properties": {
      |        "foo": {
      |          "type": "string"
      |        },
      |        "bar": {
      |          "type": "number"
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin

  val arraySchema: String =
    """{
      |  "type": "json_schema",
      |  "json_schema": {
      |    "name": "testArray",
      |    "strict": true,
      |    "schema": {
      |      "type": "array",
      |      "items": {
      |        "type": "string"
      |      }
      |    }
      |  }
      |}""".stripMargin

}
