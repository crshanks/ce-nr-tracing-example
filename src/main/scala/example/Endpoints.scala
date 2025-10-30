package example

import cats.effect.Async
import cats.implicits.*
import com.newrelic.api.agent.{NewRelic, Trace}
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
          _ <- F.delay {
            val segment = NewRelic.getAgent.getTransaction.startSegment("Database Query")
            try {
              Thread.sleep(500)
            } finally {
              segment.end()
            }
          }
          _ <- logger.info("[Sync] Database call completed!")

          _ <- F.cede // instructs to return to the IO thread pool, but we're already running there

          _ <- logger.info("[Sync] Another logging...")
          _ <- F.delay(Thread.sleep(100))

          _ <- logger.info("[Sync] Writing File IO...")
          _ <- F.delay {
            val segment = NewRelic.getAgent.getTransaction.startSegment("File IO")
            try {
              Thread.sleep(1000)
            } finally {
              segment.end()
            }
          }
          _ <- logger.info("[Sync] File updated!")

          _ <- F.cede // instructs to return to the IO thread pool, but we're already running there

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
          // Capture token before async boundary
          token <- F.delay(NewRelic.getAgent.getTransaction.getToken)
          _ <- F.blocking {
            // Link token in new thread
            token.link()
            val segment = NewRelic.getAgent.getTransaction.startSegment("Async Database Query")
            try {
              Thread.sleep(500)
            } finally {
              segment.end()
              token.expire()
            }
          }
          _ <- logger.info("[Async] Database call completed!") // this logs on blocking threadpool

          _ <- F.cede // back to the IO thread pool, but not necessary to the same thread

          _ <- logger.info("[Async] Another logging...")
          _ <- F.delay(Thread.sleep(100))

          _ <- logger.info("[Async] Writing File IO...")
          // Capture token before async boundary
          token2 <- F.delay(NewRelic.getAgent.getTransaction.getToken)
          _ <- F.blocking {
            // Link token in new thread
            token2.link()
            val segment = NewRelic.getAgent.getTransaction.startSegment("Async File IO")
            try {
              Thread.sleep(1000)
            } finally {
              segment.end()
              token2.expire()
            }
          }
          _ <- logger.info("[Async] File updated!") // this logs on blocking threadpool

          _ <- F.cede // back to the IO thread pool, but not necessary to the same thread

          response <- F.delay("Hello, I'm async endpoint!")
          _        <- logger.info(s"[Async] Computed result, producing: $response")
        } yield response
      }

}
