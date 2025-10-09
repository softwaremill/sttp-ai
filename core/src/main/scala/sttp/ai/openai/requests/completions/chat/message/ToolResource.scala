package sttp.openai.requests.completions.chat.message

import sttp.openai.json.SnakePickle
import ujson._

sealed trait ToolResource

object ToolResource {

  /** Code interpreter tool
    *
    * The type of tool being defined: code_interpreter
    */
  case class CodeInterpreterToolResource(filesIds: Option[Seq[String]] = None) extends ToolResource

  implicit val codeInterpreterToolResourceRW: SnakePickle.ReadWriter[CodeInterpreterToolResource] = SnakePickle
    .readwriter[Value]
    .bimap[CodeInterpreterToolResource](
      resource =>
        resource.filesIds match {
          case Some(fileIds) => Obj("file_ids" -> fileIds)
          case None          => Obj()
        },
      json => {
        val map = json.obj
        if (map.nonEmpty) {
          val fileIds = map.get("file_ids").map(_.arr.map(_.str).toList)
          CodeInterpreterToolResource(fileIds)
        } else {
          null
        }
      }
    )

  /** file_search tool
    *
    * The type of tool being defined: file_search
    */
  case class FileSearchToolResource(vectorStoreIds: Option[Seq[String]] = None, vectorStores: Option[Seq[String]] = None)
      extends ToolResource

  implicit val fileSearchToolResourceRW: SnakePickle.ReadWriter[FileSearchToolResource] = SnakePickle
    .readwriter[Value]
    .bimap[FileSearchToolResource](
      resource =>
        (resource.vectorStoreIds, resource.vectorStores) match {
          case (Some(vectorStoreIds), Some(vectorStores)) => Obj("vector_store_ids" -> vectorStoreIds, "vector_stores" -> vectorStores)
          case (Some(vectorStoreIds), None)               => Obj("vector_store_ids" -> vectorStoreIds)
          case (None, Some(vectorStores))                 => Obj("vector_stores" -> vectorStores)
          case _                                          => Obj()
        },
      json => {
        val map = json.obj
        if (map.nonEmpty) {
          val storeIds: Option[List[String]] = map.get("vector_store_ids").map(_.arr.map(_.str).toList).filter(_.nonEmpty)
          val stores: Option[List[String]] = map.get("vector_stores").map(_.arr.map(_.str).toList).filter(_.nonEmpty)
          FileSearchToolResource(storeIds, stores)
        } else {
          null
        }
      }
    )
}
