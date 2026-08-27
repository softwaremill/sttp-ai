package sttp.ai.core.compat

/** Cross-version stand-in for `scala.annotation.unused`, which does not exist on Scala 2.12: inert here, aliased to the real annotation in
  * the scala-2.13 and scala-3 source trees.
  */
class unused extends scala.annotation.StaticAnnotation
