package org.quantum.application
package chatbot

import zio._
import zio.http.*

trait ChatbotService {
  def getResponse(input: Request): ZIO[Client, Throwable, String]
}

class ChatbotServiceImpl(url: String) extends ChatbotService {
  def getResponse(input: Request): ZIO[Client, Throwable, String] = {
    ZIO.scoped {
      for {
        client <- ZIO.service[Client]
        bodyString <- input.body.asString
        _ <- ZIO.logInfo(s"Proxying request with URL: $url, method: ${input.method}, body: $bodyString")
        res <- client.url(URL.decode(url).toOption.get).request(input).timeout(90.seconds).catchAll(e =>
          for {
            _ <- ZIO.logError(s"Request failed with error: $e")
          } yield None
        )
        body <- res match {
          case Some(response) => response.body.asString
          case None => ZIO.succeed("Unknown Error")
        }
        _ <- ZIO.logInfo(s"Response body: $body")
      } yield body
    }
  }
}

object ChatbotService {
  def layer(url: String): ZLayer[Client, Throwable, ChatbotService] =
    ZLayer.succeed(new ChatbotServiceImpl(url))

  def getResponse(input: Request): ZIO[ChatbotService & Client, Throwable, String] =
    ZIO.serviceWithZIO[ChatbotService](_.getResponse(input))
}