package sttp.openai.utils

import sttp.client4.{IsOption, JsonInput}
import sttp.openai.json.SnakePickle.{read, write}
import sttp.openai.json.SttpUpickleApiExtension.upickleApi

object JsonUtils {
  def compactJson(json: String): String = write(read[ujson.Value](json))

  def deserializeJsonSnake[B: upickleApi.Reader: IsOption]: String => Either[Exception, B] = { (s: String) =>
    try
      Right(upickleApi.read[B](JsonInput.sanitize[B].apply(s)))
    catch {
      case e: Exception => Left(e)
      case t: Throwable =>
        // in ScalaJS, ArrayIndexOutOfBoundsException exceptions are wrapped in org.scalajs.linker.runtime.UndefinedBehaviorError
        t.getCause match {
          case e: ArrayIndexOutOfBoundsException => Left(e)
          case _                                 => throw t
        }
    }
  }
}
