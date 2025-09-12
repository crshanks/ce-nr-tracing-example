FROM sbtscala/scala-sbt:eclipse-temurin-21.0.6_7_1.10.10_3.3.5 AS builder

WORKDIR /project
COPY project/*.sbt project/*.scala project/*.properties project/
COPY build.sbt build.sbt
RUN sbt update
COPY src src
RUN sbt clean pack


FROM public.ecr.aws/docker/library/eclipse-temurin:21.0.2_13-jre-jammy AS mainstage

COPY --from=builder /project/target/pack /usr/local/example

WORKDIR /usr/local/example
ENTRYPOINT ["sh", "./bin/runner"]
