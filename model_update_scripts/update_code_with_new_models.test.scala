//> using test.dep org.scalameta::munit::1.3.5

// Run with: scala-cli test update_code_with_new_models.scala update_code_with_new_models.test.scala
class ParseCaseObjectsTest extends munit.FunSuite {

  private val className = "ChatCompletionModel"

  test("parses single-line definitions, with and without mixins") {
    val lines = List(
      """    case object GPT4 extends ChatCompletionModel("gpt-4") with Capability.ToolCalling""",
      """    case object GPT35TurboInstruct extends ChatCompletionModel("gpt-3.5-turbo-instruct")"""
    )
    val parsed = ModelUpdater.parseCaseObjects(lines, className)
    assertEquals(parsed.map(p => (p.name, p.originalModelName)), List(("GPT4", "gpt-4"), ("GPT35TurboInstruct", "gpt-3.5-turbo-instruct")))
  }

  test("parses scalafmt-wrapped definitions: extends on its own line, with-continuations consumed") {
    val lines = List(
      """    case object GPT5""",
      """        extends ChatCompletionModel("gpt-5")""",
      """        with Capability.All""",
      """    case object O3Mini""",
      """        extends ChatCompletionModel("o3-mini")""",
      """        with Capability.ToolCalling""",
      """        with Capability.StructuredOutput""",
      """        with Capability.Reasoning"""
    )
    val parsed = ModelUpdater.parseCaseObjects(lines, className)
    assertEquals(parsed.map(p => (p.name, p.originalModelName)), List(("GPT5", "gpt-5"), ("O3Mini", "o3-mini")))
  }

  test("tolerates blank lines between a case object line and its extends continuation") {
    val lines = List(
      """    case object GPT5""",
      "",
      """        extends ChatCompletionModel("gpt-5")""",
      """        with Capability.All"""
    )
    val parsed = ModelUpdater.parseCaseObjects(lines, className)
    assertEquals(parsed.map(p => (p.name, p.originalModelName)), List(("GPT5", "gpt-5")))
  }

  test("ignores case objects extending a different class") {
    val lines = List(
      """    case object Ash extends Standard""",
      """    case object Low""",
      """        extends SomeOtherModel("low")""",
      """    case object GPT4 extends ChatCompletionModel("gpt-4") with Capability.ToolCalling"""
    )
    val parsed = ModelUpdater.parseCaseObjects(lines, className)
    assertEquals(parsed.map(_.name), List("GPT4"))
  }
}
