# OpenTelemetry instrumentation with otel4s for Scala 3 + Cats Effect

## Overview

This branch demonstrates how to instrument a Scala 3 + Cats Effect 3.6.x application using **otel4s**, the native OpenTelemetry library for Scala, which exports traces to New Relic via OTLP (OpenTelemetry Protocol).

**Key advantages over the New Relic agent approach:**
- Native Cats Effect integration (no manual token management needed!)
- Automatic context propagation across async boundaries
- Vendor-agnostic (can switch to any OpenTelemetry-compatible backend)
- More idiomatic Scala 3 code
- Cleaner API with `.surround` for wrapping operations

## Architecture

```
Application Code (Scala 3 + Cats Effect)
         ↓
    otel4s API (Tracer[F])
         ↓
  OpenTelemetry Java SDK
         ↓
    OTLP Exporter
         ↓
  New Relic OTLP Endpoint
```

## Dependencies Added

```scala
// otel4s - Native Scala OpenTelemetry library
"org.typelevel" %% "otel4s-oteljava" % "0.14.0"

// OpenTelemetry Java SDK with OTLP exporter
"io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.55.0"
"io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.55.0"
```

## Configuration

### JVM Options (in build.sbt)

```scala
"-Dotel.java.global-autoconfigure.enabled=true"    // Enable auto-configuration
"-Dotel.service.name=Example"                       // Service name in New Relic
"-Dotel.exporter.otlp.protocol=http/protobuf"      // Use HTTP protocol
```

### Environment Variables (in build.sbt)

```scala
"OTEL_EXPORTER_OTLP_ENDPOINT" -> "https://otlp.nr-data.net:4318"           // New Relic OTLP endpoint
"OTEL_EXPORTER_OTLP_HEADERS"  -> "api-key=${NEW_RELIC_LICENSE_KEY}"       // Auth header
"OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT" -> "4095"                               // New Relic attribute limit
```

**Note**: The endpoint is for **US data center**. For EU, use `https://otlp.eu01.nr-data.net:4318`.

## Code Changes

### 1. Initialize otel4s in Main.scala

```scala
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.trace.Tracer

override def run(args: List[String]): IO[ExitCode] =
  OtelJava.autoConfigured[IO]().use { otel4s =>
    otel4s.tracerProvider.get("example-service").flatMap { implicit tracer: Tracer[IO] =>
      // Your application code with implicit tracer in scope
    }
  }
```

**Key points:**
- `OtelJava.autoConfigured[IO]()` reads config from system properties and env vars
- Creates a `Resource[IO, Otel4s[IO]]` that manages lifecycle
- `tracerProvider.get("example-service")` creates a tracer for your service
- Tracer is passed implicitly to components that need it

### 2. Add Tracer constraint to Endpoints

```scala
class Endpoints[F[_]: Async: Tracer] {  // Added : Tracer constraint
```

### 3. Wrap operations in spans

#### Sync endpoint pattern:
```scala
Tracer[F].span("GET /sync").surround {
  for {
    _ <- logger.info("[Sync] Start sync endpoint...")

    _ <- Tracer[F].span("Database Query").surround {
      F.delay(Thread.sleep(500))
    }

    _ <- Tracer[F].span("File IO").surround {
      F.delay(Thread.sleep(1000))
    }

    response <- F.delay("Hello, I'm sync endpoint!")
  } yield response
}
```

#### Async endpoint pattern:
```scala
Tracer[F].span("GET /async").surround {
  for {
    _ <- logger.info("[Async] Start async endpoint...")

    _ <- Tracer[F].span("Async Database Query").surround {
      F.blocking(Thread.sleep(500))  // Context automatically propagates!
    }

    _ <- Tracer[F].span("Async File IO").surround {
      F.blocking(Thread.sleep(1000))  // No manual token management needed!
    }

    response <- F.delay("Hello, I'm async endpoint!")
  } yield response
}
```

**Key advantages:**
- No manual `getToken()` / `link()` / `expire()` calls
- Context propagation happens automatically
- Same API for sync and async operations
- Clean, functional style

## Running the Application

### Build
```bash
sbt clean pack
cd target/pack
```

### Run
```bash
export NEW_RELIC_LICENSE_KEY=<your-license-key>
OTEL_EXPORTER_OTLP_HEADERS="api-key=${NEW_RELIC_LICENSE_KEY}" bin/runner.sh
```

**Important**: The `OTEL_EXPORTER_OTLP_HEADERS` must be set at runtime with your license key. This is the authentication header for New Relic's OTLP endpoint.

### Test
```bash
# In another terminal
curl localhost:8080/sync
curl localhost:8080/async
```

## What You'll See in New Relic

**Important**: OpenTelemetry data sent via OTLP appears in different locations than traditional APM data.

### Finding Your Service

**Option 1: All Entities**
Navigate to: **New Relic > All entities**
- Search for "Example-OTel4s"
- Your service will appear with entity type "Service - OpenTelemetry"

**Option 2: APM & Services (Works!)**
Navigate to: **New Relic > APM & Services**
- Your "Example-OTel4s" service will appear here
- Summary page shows throughput and response time charts

### What Works ✅

#### 1. **Distributed Tracing**
Navigate to: **New Relic > Distributed tracing** or via your service

You'll see traces for:
- `GET /sync` with child spans:
  - `Database Query` (500ms)
  - `File IO` (1000ms)

- `GET /async` with child spans:
  - `Async Database Query` (500ms)
  - `Async File IO` (1000ms)

Each span shows:
- Duration
- Start/end timestamps
- HTTP semantic attributes (method, route, status_code)
- Status (ok, error)

#### 2. **APM Summary Charts**
Navigate to: **New Relic > APM & Services > Example-OTel4s > Summary**

Charts now display:
- **Throughput** - Requests per minute from `http.server.requests` metric
- **Response time** - Duration from `http.server.request.duration` histogram
- **Web transactions time** - Populated with real data

**Note**: Data may take 5-15 seconds to appear due to metric export intervals.

#### 3. **Metrics**
Navigate to: **New Relic > Metrics explorer**

Available metrics:
- `http.server.requests` - Counter of HTTP requests
  - Attributes: `http.method`, `http.route`, `http.status_code`
- `http.server.request.duration` - Histogram of request durations
  - Attributes: `http.method`, `http.route`, `http.status_code`

#### 4. **Service Map**
Navigate to: **New Relic > Distributed tracing > Service map**

Your "Example-OTel4s" service appears with:
- Connected services (if any)
- Throughput
- Error rate
- Latency

### What Doesn't Work ❌

#### **Logs in Context**

**Issue**: otel4s uses functional context propagation (via `IOLocal`), while OpenTelemetry Java SDK logback appenders expect thread-local context. These two approaches don't communicate.

**Result**:
- Logs are NOT sent to New Relic
- No "Logs" tab in distributed traces
- No correlation between traces and logs

**Why**: otel4s propagates context through the effect type `F[_]`, but the OpenTelemetry logback appender reads from `ThreadLocal` storage that the Java SDK uses. Since otel4s doesn't populate ThreadLocal, the appender can't access trace context.

**Workarounds**:
1. **Use New Relic Agent approach** (see `add-manual-instrumentation` branch) if logs are critical
2. **Manual correlation**: Extract trace IDs from spans and add to log messages
3. **External log shipping**: Use Fluentd/Logstash to ship logs separately
4. **Future**: Wait for a log4cats → otel4s bridge (doesn't currently exist)

**What you keep**:
- Console logging still works normally
- Logs are visible in your application output
- Just not correlated in New Relic UI

## Comparison: New Relic API vs otel4s

### New Relic API Approach (see `add-manual-instrumentation` branch)

**Pros:**
- Directly integrates with New Relic agent
- Mature, production-ready
- **Logs work with trace correlation** via agent
- Agent provides some automatic instrumentation

**Cons:**
- Requires manual token management for async operations
- More boilerplate code for thread boundaries
- Vendor lock-in (New Relic specific)
- Agent doesn't fully support Cats Effect 3.6+ automatic instrumentation (only 3.2-3.3)
- No Scala 3 automatic instrumentation modules

**Code example:**
```scala
// Before async boundary
token <- F.delay(NewRelic.getAgent.getTransaction.getToken)
_ <- F.blocking {
  token.link()
  val segment = NewRelic.getAgent.getTransaction.startSegment("Operation")
  try {
    // operation
  } finally {
    segment.end()
    token.expire()
  }
}
```

### otel4s Approach (this branch)

**Pros:**
- Native Cats Effect integration
- Automatic context propagation (no manual token management!)
- Vendor-agnostic (works with any OpenTelemetry backend)
- Cleaner, more idiomatic Scala 3 code
- Works with Cats Effect 3.6.x
- Full Scala 3 support
- **APM Summary charts work** with metrics
- **Distributed tracing works fully**

**Cons:**
- Still requires manual instrumentation (no automatic instrumentation)
- **Logs in context don't work** (functional vs thread-local context mismatch)
- Currently experimental (otel4s not yet 1.0)
- Requires environment configuration for export
- No existing log4cats bridge

**Code example:**
```scala
Tracer[F].span("Operation").surround {
  F.blocking {
    // operation - context automatically propagated!
  }
}
```

## Best Practices

### 1. Span Naming
Use descriptive, hierarchical names:
- Good: `GET /sync`, `Database Query`, `Async File IO`
- Avoid: `operation1`, `doWork`, `span`

### 2. Span Granularity
Instrument:
- ✓ HTTP endpoints
- ✓ Database queries
- ✓ External API calls
- ✓ Expensive computations
- ✓ I/O operations

Avoid over-instrumenting:
- ✗ Individual variable assignments
- ✗ Simple arithmetic
- ✗ Logging statements

### 3. Adding Attributes
```scala
import org.typelevel.otel4s.Attribute

Tracer[F].span("Database Query",
  Attribute("db.system", "postgresql"),
  Attribute("db.statement", "SELECT * FROM users"),
  Attribute("db.rows_affected", 42L)
).surround {
  // query execution
}
```

### 4. Error Handling
Spans automatically record exceptions:
```scala
Tracer[F].span("risky-operation").surround {
  F.raiseError(new Exception("Something went wrong"))
  // Span will be marked as error with exception details
}
```

### 5. Resource Management
The `use` pattern ensures proper cleanup:
```scala
OtelJava.autoConfigured[IO]().use { otel4s =>
  // Application runs here
  // Resources cleaned up when this block exits
}
```

## Troubleshooting

### Traces not appearing in New Relic

1. **Check the endpoint for your region:**
   - US: `https://otlp.nr-data.net:4318`
   - EU: `https://otlp.eu01.nr-data.net:4318`

2. **Verify license key:**
   ```bash
   echo $NEW_RELIC_LICENSE_KEY
   ```

3. **Check application logs** for OpenTelemetry initialization messages

4. **Verify OTLP exporter is loaded:**
   ```bash
   ls target/pack/lib/ | grep otlp
   # Should show: opentelemetry-exporter-otlp-1.55.0.jar
   ```

5. **Test with console exporter** (for debugging):
   ```scala
   // In build.sbt, change:
   "-Dotel.traces.exporter=console"  // Prints to stdout instead of sending to New Relic
   ```

### Performance Issues

- **Overhead is minimal** when instrumentation is properly scoped
- **Sampling** can be configured: `-Dotel.traces.sampler=parentbased_traceidratio` `-Dotel.traces.sampler.arg=0.1` (10% sampling)
- **Batch export** is enabled by default (batches spans before sending)

## Migration Path

### From New Relic Agent + Manual API

1. Replace New Relic dependencies with otel4s
2. Remove `-javaagent` JVM argument
3. Add OpenTelemetry configuration
4. Replace `NewRelic.getAgent.getTransaction...` with `Tracer[F].span(...).surround`
5. Remove all token management code
6. Test and verify traces appear in New Relic

### From No Instrumentation

1. Add otel4s dependencies
2. Initialize `OtelJava.autoConfigured[IO]()`
3. Add `Tracer` constraint to components
4. Wrap operations in spans
5. Configure export to New Relic
6. Test and iterate

## Decision Matrix: Which Approach to Use?

| Requirement | New Relic Agent | otel4s |
|-------------|----------------|---------|
| **Traces** | ✅ (manual tokens) | ✅ (automatic) |
| **Metrics** | ✅ | ✅ |
| **Logs in Context** | ✅ | ❌ |
| **Cats Effect 3.6+** | ⚠️ (manual only) | ✅ |
| **Scala 3** | ⚠️ (API only) | ✅ |
| **Vendor Lock-in** | ❌ (New Relic only) | ✅ (any OTLP backend) |
| **Boilerplate Code** | ❌ (tokens everywhere) | ✅ (minimal) |
| **APM Charts** | ✅ | ✅ |
| **Production Ready** | ✅ | ⚠️ (experimental) |

### Choose New Relic Agent if:
- Logs in context are **critical** for your use case
- You're already committed to New Relic
- You need proven production stability
- You can tolerate manual token management boilerplate

### Choose otel4s if:
- You want vendor-agnostic observability
- Clean, idiomatic Scala 3 + Cats Effect code is important
- Traces and metrics are sufficient (logs optional)
- You're okay with experimental status
- You may want to switch observability backends later

### Hybrid Approach:
Consider using otel4s for traces/metrics and shipping logs separately via:
- Fluentd/Logstash with manual trace ID extraction
- ELK stack with correlation
- New Relic Log API with custom integration

## Resources

- [otel4s Documentation](https://typelevel.org/otel4s/)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [New Relic OTLP Configuration](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/get-started/opentelemetry-set-up-your-app/)
- [Cats Effect Documentation](https://typelevel.org/cats-effect/)
- [OpenTelemetry HTTP Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/http/)

## Expected Warnings

Similar to the New Relic approach, the `/sync` endpoint will generate Cats Effect warnings about blocking the IO thread pool. This is intentional and demonstrates why the async pattern is important. The `/async` endpoint should not generate these warnings.

## Next Steps

After verifying traces in New Relic:
1. Add attributes to spans for richer context
2. Implement error handling with proper span status
3. Add custom metrics using `otel4s.meterProvider`
4. Configure sampling for production workloads
5. Explore automatic instrumentation libraries (e.g., http4s-otel4s-middleware)
