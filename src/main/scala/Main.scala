package org.quantum.application

import zio._
import zio.http._

object Main extends ZIOAppDefault {
  val anyRoute = handler { (req: Request) =>
//    Response.text("Hello, World!")
    req.body.asString.map(Response.text)
  }

  override val run =
    Server
      .serve(
        anyRoute.sandbox.toHttpApp
      )
      .provide(Server.defaultWithPort(8080))
}