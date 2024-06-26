package org.quantum.application
package buffer

import org.quantum.application.chatbot.ChatbotService
import zio.*
import zio.http.*

type SessionId = String

trait BufferService {
  def bufferRequest(request: Request, sessionId: SessionId): ZIO[ChatbotService & Client, Throwable, Unit]

  def getChannelResponse(request: Request, sessionId: SessionId): ZIO[Any, NoSessionIdFound, Response]
  
  def closeSession(sessionId: SessionId): ZIO[Any, NoSessionIdFound, Unit]
}

class BufferServiceImpl(queueMapping: Ref[Map[SessionId, (Queue[Request], Queue[String], Queue[Boolean])]]) extends BufferService {
  def bufferRequest(request: Request, sessionId: SessionId): ZIO[ChatbotService & Client, Throwable, Unit] = {
    for {
      _ <- queueMapping.get.flatMap { mapping =>
        mapping.get(sessionId) match {
          case Some((requestQueue, _, _)) =>
            ZIO.logInfo(s"Offering $sessionId request to queue") *> requestQueue.offer(request)

          case None =>
            for {
              requestQueue <- Queue.dropping[Request](10)
              responseQueue <- Queue.dropping[String](10)
              exitQueue <- Queue.bounded[Boolean](1)
              _ <- queueMapping.update(_ + (sessionId -> (requestQueue, responseQueue, exitQueue)))
              _ <- ZIO.logInfo(s"Starting session processing for $sessionId")
              _ <- startSessionProcessing(sessionId, requestQueue, responseQueue, exitQueue).forkDaemon
              _ <- requestQueue.offer(request)
            } yield ()
        }
      }
    } yield ()
  }
  
  def closeSession(sessionId: SessionId): ZIO[Any, NoSessionIdFound, Unit] = {
    for {
      mapping <- queueMapping.get.flatMap {
        case mapping if mapping.contains(sessionId) => ZIO.succeed(mapping)
        case _ => ZIO.fail(NoSessionIdFound(sessionId))
      }
      exitQueue = mapping(sessionId)._3
      _ <- exitQueue.offer(true)
      _ <- queueMapping.update(_ - sessionId)
    } yield ()
  }

  private def startSessionProcessing(sessionId: SessionId, requestQueue: Queue[Request], responseQueue: Queue[String], exitQueue: Queue[Boolean]): ZIO[ChatbotService & Client, Throwable, Unit] = {
    val loop =
      for {
        _ <- ZIO.logInfo("Taking request from Queue")
        request <- requestQueue.take
        _ <- ZIO.logInfo("Processing request")
        response <- ChatbotService.getResponse(request)
        _ <- ZIO.logInfo("Offering response to Queue")
        _ <- responseQueue.offer(response)
      } yield ()
    loop.onInterrupt(ZIO.logInfo(s"Fiber listening for $sessionId stopped")).forever.fork.flatMap(fiber => exitQueue.take *> fiber.interrupt *> ZIO.unit)
  }

  def getChannelResponse(request: Request, sessionId: SessionId): ZIO[Any, NoSessionIdFound, Response] = {
    for {
      mapping <- queueMapping.get.flatMap {
        case mapping if mapping.contains(sessionId) => ZIO.succeed(mapping)
        case _ => ZIO.fail(NoSessionIdFound(request.path.toString))
      }
      responseQueue = mapping(sessionId)._2
      response <- responseQueue.take
      _ <- ZIO.logInfo(s"Returning response for $sessionId from Queue")
    } yield Response.json(response)
  }
}

object BufferService {

  def layer(queueMapping: Ref[Map[SessionId, (Queue[Request], Queue[String], Queue[Boolean])]]): ZLayer[Any, Nothing, BufferService] = ZLayer.succeed(new BufferServiceImpl(queueMapping))

  def bufferRequest(request: Request, sessionId: SessionId): ZIO[BufferService & ChatbotService & Client, Throwable, Unit] = ZIO.serviceWithZIO[BufferService](_.bufferRequest(request, sessionId))

  def getChannelResponse(request: Request, sessionId: SessionId): ZIO[BufferService, NoSessionIdFound, Response] = ZIO.serviceWithZIO[BufferService](_.getChannelResponse(request, sessionId))
  
  def closeSession(sessionId: SessionId): ZIO[BufferService, NoSessionIdFound, Unit] = ZIO.serviceWithZIO[BufferService](_.closeSession(sessionId))
}

case class NoSessionIdFound(path: String) extends Throwable