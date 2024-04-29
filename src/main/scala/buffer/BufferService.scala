package org.quantum.application
package buffer

import zio._
import zio.http.*

type SessionId = String

trait BufferService {
  def bufferRequest(request: Request): ZIO[Any, Throwable, Response]
}

class BufferServiceImpl(queueMapping: Ref[Map[SessionId, (Queue[Request], Queue[Response])]]) extends BufferService {
  def bufferRequest(request: Request): ZIO[Any, Throwable, Response] = {
    for {
      path <- ZIO.succeed(request.url.path.toString.split("/"))
      sessionId <- BufferService.getSessionIdFromPath(request.url.path.toString)
      _ <- queueMapping.get.flatMap { mapping =>
        mapping.get(sessionId) match {
          case Some((requestQueue, responseQueue)) =>
            requestQueue.offer(request)
            
          case None =>
            ZIO.fail(new Throwable(s"Session $sessionId not found"))
        }
      }
    } yield ()
  }
  
  def readResponse(sessionId: SessionId): ZIO[Any, Throwable, Response] = {
    for {
      mapping <- queueMapping.get
      (_, responseQueue) <- mapping.get(sessionId) match {
        case Some(queues) => ZIO.succeed(queues)
        case None => ZIO.fail(new Throwable(s"Session $sessionId not found"))
      }
      response <- responseQueue.take
    } yield response
  }
}

object BufferService {
  def getSessionIdFromPath(path: String): ZIO[Any, NoSessionIdFound, SessionId] = path.split("/") match {
    case Array(_, _, _, sessionId, _*) => ZIO.succeed(sessionId)
    case _ => ZIO.fail(NoSessionIdFound(path))
  }
}

case class NoSessionIdFound(path: String) extends Throwable