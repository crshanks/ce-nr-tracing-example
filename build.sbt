lazy val `ce-nr-tracing-example` = project
  .in(file("."))
  .enablePlugins(PackPlugin)
  .settings(
    scalaVersion         := "3.3.5",
    Compile / run / fork := false,
    Test / run / fork    := true
  )
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir"   %% "tapir-core"                                % "1.11.44",
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server"                       % "1.11.44",
      "com.softwaremill.sttp.tapir"   %% "tapir-pekko-http-server"                   % "1.11.44",
      "org.http4s"                    %% "http4s-blaze-server"                       % "0.23.17",
      "com.softwaremill.sttp.client3" %% "cats"                                      % "3.11.0",
      "ch.qos.logback"                 % "logback-classic"                           % "1.5.18",
      "org.typelevel"                 %% "otel4s-oteljava"                           % "0.14.0",
      "io.opentelemetry"               % "opentelemetry-exporter-otlp"               % "1.55.0",
      "io.opentelemetry"               % "opentelemetry-sdk-extension-autoconfigure" % "1.55.0"
    )
  )
  .settings(
    packMain    := Map("runner" -> "example.Main"),
    packJvmOpts := Map(
      "runner" -> Seq(
        "-server",
        "-XshowSettings:vm",
        "-XX:+UseG1GC",
        "-XX:+UseStringDeduplication",
        "-Dotel.java.global-autoconfigure.enabled=true",
        "-Dotel.service.name=Example-OTel4s",
        "-Dotel.exporter.otlp.protocol=http/protobuf",
        "-Dotel.metrics.exporter=otlp",
        "-Dotel.traces.exporter=otlp",
        "-Dotel.metric.export.interval=5000"
      )
    ),
    packEnvVars := Map(
      "runner" -> Map(
        "OTEL_EXPORTER_OTLP_ENDPOINT"       -> "https://otlp.nr-data.net:4318",
        "OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT" -> "4095"
      )
    )
  )
