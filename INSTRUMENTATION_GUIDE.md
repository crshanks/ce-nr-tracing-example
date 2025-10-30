# Manual New Relic Instrumentation for Scala 3 + Cats Effect

## Overview

This guide demonstrates how to manually instrument a Scala 3 application using Cats Effect with New Relic when automatic instrumentation is not fully available.

**Important Note**: This repository intentionally contrasts two patterns:
- **`/sync` endpoint**: Demonstrates the *wrong* way (blocking IO thread pool) - you'll see Cats Effect warnings
- **`/async` endpoint**: Demonstrates the *right* way (using `F.blocking` for blocking operations) - no warnings

Both endpoints can be instrumented with New Relic, but the async pattern is the recommended approach for production code. The warnings you see are educational and highlight why proper async handling matters.

## Background

The New Relic Java agent currently has limited automatic instrumentation for:
- **Cats Effect 3.6.x**: Only versions 3.2.x - 3.3.x are automatically instrumented
- **Scala 3**: Automatic instrumentation modules only exist for Scala 2.12 and 2.13
- **HTTP4s 0.23.x**: Supported for Scala 2.x but not Scala 3

This project uses:
- Scala 3.3.5
- Cats Effect 3.6.3 (transitive dependency)
- HTTP4s Blaze Server 0.23.17

## Changes Made

### 1. Added Scala 3 API Dependency

```scala
"com.newrelic.agent.java" % "newrelic-scala-api_3" % "8.24.0"
```

This provides access to the New Relic Java API from Scala 3 code.

### 2. Manual Instrumentation Patterns

#### Pattern 1: Synchronous Operations (F.delay)

For operations that stay on the same thread:

```scala
import com.newrelic.api.agent.NewRelic

_ <- F.delay {
  val segment = NewRelic.getAgent.getTransaction.startSegment("Database Query")
  try {
    Thread.sleep(500) // Your actual operation here
  } finally {
    segment.end()
  }
}
```

**Key points:**
- Create segment with descriptive name
- Wrap operation in try/finally to ensure segment ends
- Segment appears as a sub-span in the transaction trace

#### Pattern 2: Asynchronous Operations (F.blocking)

For operations that cross thread boundaries (e.g., `F.blocking`, `F.cede`):

```scala
// Before the async boundary - capture a token
token <- F.delay(NewRelic.getAgent.getTransaction.getToken)

// After crossing to a new thread - link the token
_ <- F.blocking {
  token.link() // Links this thread to the parent transaction
  val segment = NewRelic.getAgent.getTransaction.startSegment("Async Database Query")
  try {
    Thread.sleep(500) // Your actual operation here
  } finally {
    segment.end()
    token.expire() // Clean up the token
  }
}
```

**Key points:**
- Tokens enable distributed tracing across threads
- Call `getToken()` before the async boundary
- Call `link()` immediately after crossing to the new thread
- Always `expire()` the token when done to prevent memory leaks
- This maintains trace continuity across Cats Effect's thread pool transitions

## What You'll See in New Relic

### Without Manual Instrumentation

- Basic HTTP transaction tracking (from automatic HTTP4s instrumentation on Scala 2.x)
- No visibility into individual operations
- Lost trace context when crossing thread boundaries with `F.blocking`
- Incomplete distributed traces

### With Manual Instrumentation

1. **Transaction-level metrics**
   - Total request duration
   - Throughput (requests per minute)
   - Error rate

2. **Detailed segment breakdown**
   - `Database Query` segments (500ms each)
   - `File IO` segments (1000ms each)
   - `Async Database Query` segments (500ms each)
   - `Async File IO` segments (1000ms each)

3. **Thread transitions visible**
   - See when operations move from IO thread pool to blocking thread pool
   - Trace continuity maintained across `F.cede` and `F.blocking`

4. **Transaction traces**
   - Waterfall view showing timing of each segment
   - Thread names and IDs for each operation
   - Clear visualization of sync vs async execution patterns

5. **Service map**
   - Your service appears in distributed tracing
   - Can track requests across multiple services

## Testing the Instrumentation

### Start the Server

```bash
cd target/pack
export NEW_RELIC_LICENSE_KEY=<your-license-key>
bin/runner.sh
```

### Send Test Requests

```bash
# Test sync endpoint (expect Cats Effect warnings about thread starvation)
curl localhost:8080/sync

# Test async endpoint (should not produce warnings)
curl localhost:8080/async
```

**Expected behavior:**
- `/sync`: You'll see Cats Effect warnings about blocking the IO thread pool. This is intentional and demonstrates why proper async patterns matter.
- `/async`: No warnings should appear because blocking operations are properly isolated to the blocking thread pool.

### View in New Relic

1. Go to New Relic APM
2. Find your application (named "Example")
3. Navigate to "Transactions"
4. Click on `/sync` or `/async` transactions
5. View transaction traces to see the segment breakdown

## Key Differences Between Endpoints

### /sync Endpoint
- All operations on IO thread pool
- `F.cede` has minimal effect (already on IO pool)
- Simpler instrumentation (no tokens needed)
- Segments show sequential execution
- **⚠️ Warning**: This endpoint intentionally demonstrates poor practice by using `Thread.sleep()` in `F.delay`, which blocks the IO thread pool. You'll see Cats Effect warnings about thread starvation:
  ```
  Your app's responsiveness to a new asynchronous event was in excess of 100 milliseconds.
  Your CPU is probably starving. Consider increasing the granularity of your delays or adding
  more cedes. This may also be a sign that you are unintentionally running blocking I/O
  operations without the blocking combinator.
  ```
  This is expected and illustrates why the async pattern is important.

### /async Endpoint
- Operations explicitly move to blocking thread pool via `F.blocking`
- Tokens required to maintain trace context
- Shows true async execution pattern
- Segments clearly show thread transitions
- **✓ Best Practice**: Properly uses `F.blocking` for blocking operations, preventing IO thread starvation
- No Cats Effect warnings should appear

## Best Practices

1. **Name segments descriptively**: Use names that indicate the operation type (e.g., "Database Query", "External API Call")

2. **Always use try/finally**: Ensures segments are ended even if exceptions occur

3. **Token lifecycle management**:
   - Get token just before async boundary
   - Link immediately in new thread
   - Expire when operation completes
   - Don't reuse tokens

4. **Granularity**: Don't over-instrument - focus on:
   - External service calls
   - Database operations
   - Expensive computations
   - I/O operations

5. **Error handling**: Add custom attributes for debugging:
   ```scala
   NewRelic.addCustomParameter("operation_type", "database")
   NewRelic.addCustomParameter("query_id", queryId)
   ```

## Limitations

- **Manual effort required**: Unlike automatic instrumentation, you must explicitly instrument each operation
- **Maintenance burden**: Need to update instrumentation as code changes
- **No automatic context propagation**: Must manually handle tokens for async operations
- **Cats Effect specific**: Token management pattern is specific to managing Cats Effect's execution model

## Alternative: otel4s

For a more idiomatic Scala solution, consider [otel4s](https://typelevel.org/otel4s/):
- Native Cats Effect integration
- Vendor-agnostic (works with New Relic, Datadog, etc.)
- More idiomatic Scala 3 APIs
- Still requires manual instrumentation
- Currently experimental

## Resources

- [New Relic Java Agent API](https://docs.newrelic.com/docs/apm/agents/java-agent/api-guides/guide-using-java-agent-api/)
- [New Relic Async Token Guide](https://docs.newrelic.com/docs/apm/agents/java-agent/async-instrumentation/java-agent-api-asynchronous-applications/)
- [Cats Effect Documentation](https://typelevel.org/cats-effect/)
- [HTTP4s Documentation](https://http4s.org/)

## Comparing Before/After

### Before Instrumentation
```
Transaction: GET /async [1600ms total]
  └─ (no detail)
```

### After Instrumentation
```
Transaction: GET /async [1600ms total]
  ├─ Async Database Query [500ms] (blocking thread pool)
  └─ Async File IO [1000ms] (blocking thread pool)
```

The difference is dramatic - you now have visibility into what's taking time within your requests.
