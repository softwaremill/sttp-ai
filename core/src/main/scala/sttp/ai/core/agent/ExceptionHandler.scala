package sttp.ai.core.agent

trait ExceptionHandler {

  /** Handles an exception from tool execution
    *
    * @return
    *   Left(errorMessage) to send to LLM and continue loop Right(exception) to propagate and terminate loop
    */
  def handleToolException(
      toolName: String,
      exception: Exception
  ): Either[String, Exception]

  /** Formats error message when tool call arguments fail to parse
    *
    * @param toolName
    *   The name of the tool that was called
    * @param rawArguments
    *   The raw argument string that failed to parse
    * @param parseException
    *   The parsing exception
    * @return
    *   Left(errorMessage) to send to LLM, or Right(exception) to propagate
    */
  def handleParseError(
      toolName: String,
      rawArguments: String,
      parseException: Exception
  ): Either[String, Exception]
}

object ExceptionHandler {

  /** Propagate IO/system errors, send logic errors to LLM with descriptive messages */
  val default: ExceptionHandler = new ExceptionHandler {
    def handleToolException(toolName: String, exception: Exception): Either[String, Exception] =
      exception match {
        case _: java.io.IOException  => Right(exception)
        case _: InterruptedException => Right(exception)
        case other                   => Left(s"Error executing tool '$toolName': ${other.getMessage}")
      }

    def handleParseError(
        toolName: String,
        rawArguments: String,
        parseException: Exception
    ): Either[String, Exception] = {
      val message = s"""Invalid arguments for tool '$toolName'.
        |Error: ${parseException.getMessage}
        |
        |Please check the tool definition and provide valid arguments.""".stripMargin
      Left(message)
    }
  }

  /** Send all errors to LLM */
  val sendAllToLLM: ExceptionHandler = new ExceptionHandler {
    def handleToolException(toolName: String, exception: Exception): Either[String, Exception] =
      Left(s"Error executing tool '$toolName': ${exception.getMessage}")

    def handleParseError(
        toolName: String,
        rawArguments: String,
        parseException: Exception
    ): Either[String, Exception] =
      Left(s"Failed to parse arguments for tool '$toolName': ${parseException.getMessage}")
  }

  /** Propagate all errors (strict mode) */
  val propagateAll: ExceptionHandler = new ExceptionHandler {
    def handleToolException(toolName: String, exception: Exception): Either[String, Exception] =
      Right(exception)

    def handleParseError(
        toolName: String,
        rawArguments: String,
        parseException: Exception
    ): Either[String, Exception] =
      Right(parseException)
  }
}
