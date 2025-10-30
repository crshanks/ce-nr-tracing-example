package example

import cats.effect.{Async, Resource}
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.{Counter, Histogram, Meter}
import org.typelevel.otel4s.trace.{SpanKind, Tracer}
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

class Endpoints[F[_]: Async: Tracer: Meter] {

  private val F = Async[F]

  private val logger: Logger[F] = Slf4jFactory.create[F].getLogger

  // Create metrics using New Relic APM naming conventions
  private def getMetrics: F[(Counter[F, Long], Histogram[F, Double])] =
    for {
      counter <- Meter[F].counter[Long]("http.server.requests")
        .withDescription("Total number of HTTP requests")
        .withUnit("requests")
        .create
      // Use the metric name that New Relic APM expects
      histogram <- Meter[F].histogram[Double]("http.server.request.duration")
        .withDescription("HTTP request duration")
        .withUnit("ms")
        .create
    } yield (counter, histogram)

  val syncEndpoint: ServerEndpoint.Full[Unit, Unit, Unit, Unit, String, Any, F] =
    endpoint.get
      .in("sync")
      .out(stringBody)
      .serverLogicSuccess { _ =>
        val attributes = List(
          Attribute("http.method", "GET"),
          Attribute("http.route", "/sync"),
          Attribute("http.status_code", 200L)
        )

        getMetrics.flatMap { case (counter, histogram) =>
          Tracer[F].spanBuilder("GET /sync")
            .withSpanKind(SpanKind.Server)
            .addAttribute(Attribute("http.method", "GET"))
            .addAttribute(Attribute("http.route", "/sync"))
            .addAttribute(Attribute("http.target", "/sync"))
            .addAttribute(Attribute("http.scheme", "http"))
            .addAttribute(Attribute("http.status_code", 200L))
            .build
            .use { span =>
              for {
                start <- F.realTime
                _ <- counter.inc(attributes*)

                // everything in this sequence runs in the IO thread pool, most likely on the same thread
                _ <- logger.info("[Sync] Start sync endpoint...")
                _ <- F.delay(Thread.sleep(100))

                _ <- logger.info("[Sync] Calling database...")
                _ <- Tracer[F].span("Database Query").surround {
                  F.delay(Thread.sleep(500))
                }
                _ <- logger.info("[Sync] Database call completed!")

                _ <- F.cede // instructs to return to the IO thread pool, but we're already running there

                _ <- logger.info("[Sync] Another logging...")
                _ <- F.delay(Thread.sleep(100))

                _ <- logger.info("[Sync] Writing File IO...")
                _ <- Tracer[F].span("File IO").surround {
                  F.delay(Thread.sleep(1000))
                }
                _ <- logger.info("[Sync] File updated!")

                _ <- F.cede // instructs to return to the IO thread pool, but we're already running there

                response <- F.delay("Hello, I'm sync endpoint!")
                _        <- logger.info(s"[Sync] Computed result, producing: $response")

                end <- F.realTime
                duration = (end - start).toMillis.toDouble
                _ <- histogram.record(duration, attributes*)
              } yield response
            }
        }
      }

  val asyncEndpoint: ServerEndpoint.Full[Unit, Unit, Unit, Unit, String, Any, F] =
    endpoint.get
      .in("async")
      .out(stringBody)
      .serverLogicSuccess { _ =>
        val attributes = List(
          Attribute("http.method", "GET"),
          Attribute("http.route", "/async"),
          Attribute("http.status_code", 200L)
        )

        getMetrics.flatMap { case (counter, histogram) =>
          Tracer[F].spanBuilder("GET /async")
            .withSpanKind(SpanKind.Server)
            .addAttribute(Attribute("http.method", "GET"))
            .addAttribute(Attribute("http.route", "/async"))
            .addAttribute(Attribute("http.target", "/async"))
            .addAttribute(Attribute("http.scheme", "http"))
            .addAttribute(Attribute("http.status_code", 200L))
            .build
            .use { span =>
              for {
                start <- F.realTime
                _ <- counter.inc(attributes*)

                _ <- logger.info("[Async] Start async endpoint...")
                _ <- F.delay(Thread.sleep(100)) //  runs in the IO thread pool

                _ <- logger.info("[Async] Calling database...")
                _ <- Tracer[F].span("Async Database Query").surround {
                  F.blocking(Thread.sleep(500)) // `blocking` will shift the execution of the blocking operation to a separate threadpool
                }
                _ <- logger.info("[Async] Database call completed!") // this logs on blocking threadpool

                _ <- F.cede // back to the IO thread pool, but not necessary to the same thread

                _ <- logger.info("[Async] Another logging...")
                _ <- F.delay(Thread.sleep(100))

                _ <- logger.info("[Async] Writing File IO...")
                _ <- Tracer[F].span("Async File IO").surround {
                  F.blocking(Thread.sleep(1000)) // again a `blocking` operation
                }
                _ <- logger.info("[Async] File updated!") // this logs on blocking threadpool

                _ <- F.cede // back to the IO thread pool, but not necessary to the same thread

                response <- F.delay("Hello, I'm async endpoint!")
                _        <- logger.info(s"[Async] Computed result, producing: $response")

                end <- F.realTime
                duration = (end - start).toMillis.toDouble
                _ <- histogram.record(duration, attributes*)
              } yield response
            }
        }
      }

}
