package org.quantum.application

import buffer.*
import chatbot.*

import zio.*
import zio.http.*

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
  }

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