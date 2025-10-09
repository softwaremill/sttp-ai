package sttp.openai.json

import ujson._

/** An object that transforms all snake_case keys into camelCase [[https://com-lihaoyi.github.io/upickle/#CustomConfiguration]] */
object SnakePickle extends upickle.AttributeTagged {

  override def tagName: String = "type"

  private def camelToSnake(s: String): String =
    s.replaceAll("([A-Z])", "#$1").split('#').map(_.toLowerCase).mkString("_")

  private def snakeToCamel(s: String): String = {
    val res = s.split("_", -1).map(x => s"${x(0).toUpper}${x.drop(1)}").mkString
    s"${s(0).toLower}${res.drop(1)}"
  }

  override def objectAttributeKeyReadMap(s: CharSequence): String =
    snakeToCamel(s.toString)

  override def objectAttributeKeyWriteMap(s: CharSequence): String =
    camelToSnake(s.toString)

  /** This is required in order to parse null values into Scala's Option */
  override implicit def OptionWriter[T: SnakePickle.Writer]: Writer[Option[T]] =
    implicitly[SnakePickle.Writer[T]].comap[Option[T]] {
      case None    => null.asInstanceOf[T]
      case Some(x) => x
    }

  override implicit def OptionReader[T: SnakePickle.Reader]: Reader[Option[T]] =
    new Reader.Delegate[Any, Option[T]](implicitly[SnakePickle.Reader[T]].map(Some(_))) {
      override def visitNull(index: Int) = None
    }
}

object SerializationHelpers {

  /** Creates a ReadWriter for nested discriminator patterns where the object is wrapped in another object with a discriminator field
    * pointing to the nested content. Additionally, automatically added discriminator field is removed from the nested object.
    *
    * For example: {"type": "json_schema", "json_schema": {...actual object...}}
    *
    * @param discriminatorValue
    *   The value for the discriminator field (e.g., "json_schema")
    * @param nestedField
    *   The name of the field containing the nested object (e.g., "json_schema")
    * @param baseW
    *   The base Writer for the type T (typically SnakePickle.macroRW)
    * @return
    *   A Writer that wraps the object in the nested discriminator structure
    */
  def withNestedDiscriminatorWriter[T](discriminatorValue: String, nestedField: String)(implicit
      baseW: SnakePickle.Writer[T]
  ): SnakePickle.Writer[T] =
    SnakePickle
      .writer[Value]
      .comap { t =>
        val baseJson = SnakePickle.writeJs(t)
        // Filter out tagName from nested case class
        val cleanedJson = baseJson match {
          case obj: Obj =>
            val filtered = obj.obj.filterNot { case (key, _) =>
              key == SnakePickle.tagName
            }
            Obj.from(filtered)
          case other => other
        }
        Obj(
          SnakePickle.tagName -> discriminatorValue,
          nestedField -> cleanedJson
        )
      }

  def withNestedDiscriminatorReader[T](discriminatorValue: String, nestedField: String)(implicit
      baseR: SnakePickle.Reader[T]
  ): SnakePickle.Reader[T] =
    SnakePickle
      .reader[Value]
      .map { json =>
        json.obj.get(nestedField).map(_.obj.addOne(SnakePickle.tagName -> discriminatorValue)) match {
          case Some(modifiedJson) => SnakePickle.read(modifiedJson)
          case None               => throw MissingInnerObjectException(nestedField)
        }

      }

  def caseObjectWithDiscriminatorWriter[T](discriminatorValue: String): SnakePickle.Writer[T] =
    SnakePickle.writer[Value].comap(_ => Obj(SnakePickle.tagName -> Str(discriminatorValue)))

  def stringCaseObject[T](value: String): SnakePickle.Writer[T] =
    SnakePickle
      .writer[Value]
      .comap(_ => Str(value))

  private case class MissingInnerObjectException(nestedField: String)
      extends Exception(s"Couldn't find nested object under field=[$nestedField]")

}
