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
      "com.softwaremill.sttp.tapir"   %% "tapir-core"              % "1.11.44",
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server"     % "1.11.44",
      "com.softwaremill.sttp.tapir"   %% "tapir-pekko-http-server" % "1.11.44",
      "org.http4s"                    %% "http4s-blaze-server"     % "0.23.17",
      "com.softwaremill.sttp.client3" %% "cats"                    % "3.11.0",
      "ch.qos.logback"                 % "logback-classic"         % "1.5.18",
      "com.newrelic.agent.java"        % "newrelic-agent"          % "8.24.0"
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
        "-javaagent:lib/newrelic-agent-8.24.0.jar"
      )
    ),
    packEnvVars := Map(
      "runner" -> Map(
        "NEW_RELIC_APP_NAME" -> "Example"
      )
    )
  )
