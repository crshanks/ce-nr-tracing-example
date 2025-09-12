package example

import cats.effect.Async
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

class Endpoints[F[_]: Async] {

  private val F = Async[F]

  private val logger: Logger[F] = Slf4jFactory.create[F].getLogger

  val syncEndpoint: ServerEndpoint.Full[Unit, Unit, Unit, Unit, String, Any, F] =
    endpoint.get
      .in("sync")
      .out(stringBody)
      .serverLogicSuccess { _ =>
        for {
          // everything in this sequence runs in the IO thread pool, most likely on the same thread
          _ <- logger.info("[Sync] Start sync endpoint...")
          _ <- F.delay(Thread.sleep(100))

          _ <- logger.info("[Sync] Calling database...")
          _ <- F.delay(Thread.sleep(500))
          _ <- logger.info("[Sync] Database call completed!")

          _ <- F.cede // instructs to return to the IO thread pool, but we’re already running there

          _ <- logger.info("[Sync] Another logging...")
          _ <- F.delay(Thread.sleep(100))

          _ <- logger.info("[Sync] Writing File IO...")
          _ <- F.delay(Thread.sleep(1000))
          _ <- logger.info("[Sync] File updated!")

          _ <- F.cede // instructs to return to the IO thread pool, but we’re already running there

          response <- F.delay("Hello, I'm sync endpoint!")
          _        <- logger.info(s"[Sync] Computed result, producing: $response")
        } yield response
      }

  val asyncEndpoint: ServerEndpoint.Full[Unit, Unit, Unit, Unit, String, Any, F] =
    endpoint.get
      .in("async")
      .out(stringBody)
      .serverLogicSuccess { _ =>
        for {
          _ <- logger.info("[Async] Start async endpoint...")
          _ <- F.delay(Thread.sleep(100)) //  runs in the IO thread pool

          _ <- logger.info("[Async] Calling database...")
          _ <- F.blocking(Thread.sleep(500))                   // `blocking` will shift the execution of the blocking operation to a separate threadpool
          _ <- logger.info("[Async] Database call completed!") // this logs on blocking threadpool

          _ <- F.cede // back to the IO thread pool, but not necessary to the same thread

          _ <- logger.info("[Async] Another logging...")
          _ <- F.delay(Thread.sleep(100))

          _ <- logger.info("[Async] Writing File IO...")
          _ <- F.blocking(Thread.sleep(1000))       // again a `blocking` operation
          _ <- logger.info("[Async] File updated!") // this logs on blocking threadpool

          _ <- F.cede // back to the IO thread pool, but not necessary to the same thread

          response <- F.delay("Hello, I'm async endpoint!")
          _        <- logger.info(s"[Async] Computed result, producing: $response")
        } yield response
      }

}
