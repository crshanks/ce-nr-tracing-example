package example

import cats.effect.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  private val exampleEndpoints = new Endpoints[IO]

  private val routes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      List(
        exampleEndpoints.syncEndpoint,
        exampleEndpoints.asyncEndpoint
      )
    )

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(Router("/" -> routes).orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
