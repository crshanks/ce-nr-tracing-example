# NewRelic Distributed tracing issue in cats-effect IO

## Prepare

```shell
export NEW_RELIC_LICENSE_KEY=<your NR license key>
```

## Build and run

### Run Server in a shell

```shell
sbt clean pack
cd target/pack
bin/runner.sh
```

### Run Server as a container

```shell
docker build -t example .
docker run --rm -p 8080:8080 -e NEW_RELIC_LICENSE_KEY=$NEW_RELIC_LICENSE_KEY example
```

## Test endpoints

```shell
curl localhost:8080/sync
curl localhost:8080/async
```