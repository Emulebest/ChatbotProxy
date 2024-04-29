package org.quantum.application

import org.quantum.application.buffer.*
import org.quantum.application.chatbot.*
import zio.*
import zio.http.*
import zio.http.ConnectionPoolConfig.{Disabled, Dynamic, Fixed}
import zio.http.netty.NettyConfig

object Main extends ZIOAppDefault {
  val anyRoute = handler { (req: Request) =>
    for {
      // full path http://localhost:8000/api/v1/chat/retail//clients/{client_id}/chatbots/{chatbot_id}/sessions/{session_id}/answer
      _ <- ZIO.logInfo(s"Received request: ${req.path.toString.split('/').toList.drop(1)}")
      response <- req.path.toString.split("/").toList.drop(1) match {
        case "api" :: "v1" :: "chat" :: "retail" :: "clients" :: clientId :: "chatbots" :: chatbotId :: "sessions" :: sessionId :: "answer" :: Nil =>
          for {
            _ <- ZIO.logInfo(s"Buffering request for client $clientId, chatbot $chatbotId, session $sessionId")
            _ <- BufferService.bufferRequest(req, sessionId)
            response <- BufferService.getResponse(req, sessionId)
          } yield response
        case _ =>
          for {
            chatbotResponse <- ChatbotService.getResponse(req)
          } yield Response.json(chatbotResponse)
      }
    } yield response
    //    Response.text("Hello, World!")
    //      req.body.asString.map(Response.text)
  }

  val clientConfig = ZClient.Config(
    ssl = None,
    proxy = None,
    connectionPool = Fixed(10),
    maxInitialLineLength = 4096,
    maxHeaderSize = 8192,
    requestDecompression = Decompression.No,
    localAddress = None,
    addUserAgentHeader = true,
    webSocketConfig = WebSocketConfig.default,
    idleTimeout = None,
    connectionTimeout = None,
  )

//  lazy val clientConfigured: ZLayer[Any, Throwable, Client] = {
//    implicit val trace: Trace = Trace.empty
//    (ZLayer.succeed(clientConfig) ++ ZLayer.succeed(NettyConfig.default) ++
//      DnsResolver.default) >>> ZClient.live
//  }

  override val run =
    for {
      _ <- Console.printLine("Starting server on port 8082")
      queueMapping <- Ref.make(Map.empty[SessionId, (Queue[Request], Queue[String])])
      server <- Server
        .serve(
          anyRoute.sandbox.toHttpApp
        )
        .provide(Server.defaultWithPort(8082), Client.default, ChatbotService.layer("http://localhost:8000"), BufferService.layer(queueMapping))
    } yield server
}