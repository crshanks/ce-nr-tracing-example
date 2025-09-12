package example

import cats.effect.{ExitCode, IO, IOApp}
import sttp.client3.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.Uri

import scala.concurrent.duration.*

object TestClient extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    HttpClientCatsBackend
      .resource[IO]()
      .use { backend =>
        IO.race(
          pingSync(backend),
          pingAsync(backend)
        )
      }
      .as(ExitCode.Success)

  private def pingSync(backend: SttpBackend[IO, Any]): IO[Unit] =
    IO.sleep(800.millis) >> runRequest(backend, uri"http://localhost:8080/sync").start >> pingSync(backend)

  private def pingAsync(backend: SttpBackend[IO, Any]): IO[Unit] =
    IO.sleep(500.millis) >> runRequest(backend, uri"http://localhost:8080/async").start >> pingAsync(backend)

  private def runRequest(backend: SttpBackend[IO, Any], uri: Uri): IO[Unit] = {
    basicRequest
      .get(uri)
      .send(backend)
      .timed
      .flatMap((d, resp) => IO.println(f"$uri%-70s\t${resp.code.code}%3d\t${d.toMillis}%5d ms"))
  }

}
