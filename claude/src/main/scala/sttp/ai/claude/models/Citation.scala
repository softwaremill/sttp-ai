package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}
import upickle.implicits.key

sealed trait Citation {
  def `type`: String
}

object Citation {
  @key("char_location")
  case class CharLocation(
      citedText: String,
      documentIndex: Int,
      documentTitle: Option[String] = None,
      endCharIndex: Int,
      fileId: Option[String] = None,
      startCharIndex: Int
  ) extends Citation {
    val `type`: String = "char_location"
  }

  @key("page_location")
  case class PageLocation(
      citedText: String,
      documentIndex: Int,
      documentTitle: Option[String] = None,
      endPageNumber: Int,
      fileId: Option[String] = None,
      startPageNumber: Int
  ) extends Citation {
    val `type`: String = "page_location"
  }

  @key("content_block_location")
  case class ContentBlockLocation(
      citedText: String,
      documentIndex: Int,
      documentTitle: Option[String] = None,
      endBlockIndex: Int,
      fileId: Option[String] = None,
      startBlockIndex: Int
  ) extends Citation {
    val `type`: String = "content_block_location"
  }

  @key("web_search_result_location")
  case class WebSearchResultLocation(
      citedText: String,
      encryptedIndex: String,
      title: Option[String] = None,
      url: String
  ) extends Citation {
    val `type`: String = "web_search_result_location"
  }

  @key("search_result_location")
  case class SearchResultLocation(
      citedText: String,
      endBlockIndex: Int,
      searchResultIndex: Int,
      source: String,
      startBlockIndex: Int,
      title: Option[String] = None
  ) extends Citation {
    val `type`: String = "search_result_location"
  }

  implicit val charLocationRW: ReadWriter[CharLocation] = macroRW
  implicit val pageLocationRW: ReadWriter[PageLocation] = macroRW
  implicit val contentBlockLocationRW: ReadWriter[ContentBlockLocation] = macroRW
  implicit val webSearchResultLocationRW: ReadWriter[WebSearchResultLocation] = macroRW
  implicit val searchResultLocationRW: ReadWriter[SearchResultLocation] = macroRW

  implicit val rw: ReadWriter[Citation] = ReadWriter.merge(
    charLocationRW,
    pageLocationRW,
    contentBlockLocationRW,
    webSearchResultLocationRW,
    searchResultLocationRW
  )
}
