package example

import cats.effect.*
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.trace.Tracer
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def run(args: List[String]): IO[ExitCode] =
    OtelJava.autoConfigured[IO]().use { otel4s =>
      (
        otel4s.tracerProvider.get("example-service"),
        otel4s.meterProvider.get("example-service")
      ).flatMapN { (tracer: Tracer[IO], meter: Meter[IO]) =>
        implicit val t: Tracer[IO] = tracer
        implicit val m: Meter[IO] = meter

        val exampleEndpoints = new Endpoints[IO]

        val routes: HttpRoutes[IO] =
          Http4sServerInterpreter[IO]().toRoutes(
            List(
              exampleEndpoints.syncEndpoint,
              exampleEndpoints.asyncEndpoint
            )
          )

        BlazeServerBuilder[IO]
          .withExecutionContext(ec)
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(Router("/" -> routes).orNotFound)
          .serve
          .compile
          .drain
          .as(ExitCode.Success)
      }
    }
}
