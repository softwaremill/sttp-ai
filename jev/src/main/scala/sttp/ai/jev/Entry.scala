package sttp.ai.jev

import io.circe.Json

/** Text, or a JSON object/array, that the model reads as context: the state, instructions, option and level descriptions. Must be a string,
  * object or array; the server rejects anything else.
  */
type Entry = String | Json
