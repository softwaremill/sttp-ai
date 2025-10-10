//> using dep com.softwaremill.sttp.openai::ox:0.3.10
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.33
//> using dep com.softwaremill.sttp.client4::ox:4.0.8
//> using dep ch.qos.logback:logback-classic:1.5.19

// remember to set the OPENAI_KEY env variable!
// run with: OPENAI_KEY=... scala-cli run ChatProxy.scala

// test by connecting to ws://localhost:8080/chat using a WebSocket client

package examples

import org.slf4j.{Logger, LoggerFactory}
import ox.*
import ox.either.orThrow
import sttp.client4.{DefaultSyncBackend, SyncBackend}
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message}
import sttp.ai.openai.streaming.ox.*
import sttp.tapir.*
import sttp.tapir.CodecFormat.*
import sttp.tapir.server.netty.sync.{NettySyncServer, OxStreams}
import ox.flow.Flow

//

val logger: Logger = LoggerFactory.getLogger("ChatProxy")

// model for sending & receiving chat messages to/from the end-user
case class ChatMessage(message: String)
given Codec[String, ChatMessage, TextPlain] = Codec.string.map(ChatMessage(_))(_.message)

// the description of the endpoint, that will be exposed: GET /chat -> WS(consuming & producing ChatMessage-s)
val chatProxyEndpoint = infallibleEndpoint.get
  .in("chat")
  .out(webSocketBody[ChatMessage, TextPlain, ChatMessage, TextPlain](OxStreams))

def chat(sttpBackend: SyncBackend, openAI: OpenAI): OxStreams.Pipe[ChatMessage, ChatMessage] =
  // the OxStreams.Pipe converts a flow of *incoming* messages (sent by the end-user over the web socket), to a flow
  // of *outgoing* messages (sent to the end-user over the web socket)t
  incoming =>
    // we're returning an *outgoing* flow where we can freely emit elements (in our case - incremental chat responses)
    Flow.usingEmit { emit =>
      incoming
        // main processing loop: receives messages from the WS and queries OpenAI with the chat's history
        .mapStateful(() => Vector.empty[Message]) { (history, nextMessage) =>
          val nextHistory = history.apply() :+ Message.UserMessage(content = Content.TextContent(nextMessage.message))

          // querying OpenAI with the entire chat history, as each request is stateless
          val chatRequestBody: ChatBody = ChatBody(
            model = ChatCompletionModel.GPT4oMini,
            messages = nextHistory
          )

          // requesting a streaming completion, so that we can get back to the user as the answer is being generated
          val chatCompletionFlow = openAI
            .createStreamedChatCompletion(chatRequestBody)
            .send(sttpBackend)
            .body
            .orThrow // there might be an OpenAI HTTP-error

          // extracting the response increments
          val responseList = chatCompletionFlow
            .map(_.orThrow.choices.head.delta.content)
            .collect { case Some(msg) => ChatMessage(msg) }
            .tap(emit.apply) // emitting each to the *outgoing* flow
            .runToList() // accumulating all repsonses so they become part of the history for the next request

          val entireResponse = responseList.map(_.message).mkString
          val nextNextHistory = nextHistory :+ Message.AssistantMessage(content = entireResponse)

          (() => nextNextHistory, ())
        }
        // when the outer flow is run, running the incoming flow as well; it doesn't produce any meaningful results
        // (apart from emitting responses to the outer flow), so discarding its result
        .runDrain()
    }

object ChatProxy extends OxApp:
  override def run(args: Vector[String])(using Ox): ExitCode =
    val openAI = new OpenAI(System.getenv("OPENAI_KEY"))
    val sttpBackend = useCloseableInScope(DefaultSyncBackend())
    val chatProxyServerEndpoint = chatProxyEndpoint.handleSuccess(_ => chat(sttpBackend, openAI))
    val binding = NettySyncServer().addEndpoint(chatProxyServerEndpoint).start()
    logger.info(s"Server started at ${binding.hostName}:${binding.port}")
    never
