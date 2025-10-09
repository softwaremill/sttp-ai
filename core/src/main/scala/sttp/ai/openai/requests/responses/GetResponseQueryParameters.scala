package sttp.openai.requests.responses

/** @param include
  *   Additional fields to include in the response. See the include parameter for Response creation for more information.
  * @param includeObfuscation
  *   When `true`, stream obfuscation will be enabled. Stream obfuscation adds random characters to an obfuscation field on streaming delta
  *   events to normalize payload sizes as a mitigation to certain side-channel attacks. These obfuscation fields are included by default,
  *   but add a small amount of overhead to the data stream. You can set `includeObfuscation` to false to optimize for bandwidth if you
  *   trust the network links between your application and the OpenAI API.
  * @param startingAfter
  *   The sequence number of the event after which to start streaming.
  * @param stream
  *   If set to true, the model response data will be streamed to the client as it is generated using server-sent events.
  */
case class GetResponseQueryParameters(
    include: Option[List[String]] = None,
    includeObfuscation: Option[Boolean] = None,
    startingAfter: Option[Int] = None,
    stream: Option[Boolean] = None
) {
  def toMap: Map[String, String] = {
    val queryParams = include.map(list => "include" -> list.mkString(",")) ++
      includeObfuscation.map(value => "include_obfuscation" -> value.toString) ++
      startingAfter.map(value => "starting_after" -> value.toString) ++
      stream.map(value => "stream" -> value.toString)
    queryParams.toMap
  }
}

object GetResponseQueryParameters {
  val empty: GetResponseQueryParameters = GetResponseQueryParameters(None, None, None, None)
}
