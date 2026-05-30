# Sending Request
curl -X POST "http://server-ip-address:8080/api/v1/generate" -H "Content-Type: application/json" -d '{"prompt": "Hello world", "temperature": 0.7, "encoding": "protobuf"}'

# Testing Roadmap
# End-to-End Concrete Test Plan

This is a practical execution plan to benchmark:

```text id="fefth6"
Java Gateway ⇄ Python vLLM
REST(JSON) vs gRPC(Protobuf)
5k concurrent streaming requests
8k context window
```

The goal is to produce:

* TTFT
* throughput
* CPU
* GC pressure
* serialization overhead
* memory behavior
* backpressure validation

---

# PHASE 1 — Environment Setup

# Step 1: Prepare Machines

Use 2 machines minimum.

## Machine A — Gateway + Load Generator

Install:

* Java 21
* Maven/Gradle
* Linux tools
* Docker optional

## Machine B — vLLM GPU Server

Install:

* CUDA
* Python 3.11+
* vLLM

---

# Step 2: Start vLLM

Example:

```bash
python -m vllm.entrypoints.openai.api_server \
  --model meta-llama/Llama-3-8B-Instruct \
  --tensor-parallel-size 1 \
  --max-model-len 8192 \
  --gpu-memory-utilization 0.95 \
  --port 8000
```

Verify:

```bash
curl http://GPU_SERVER:8000/v1/models
```

---

# Step 3: Start Java Gateway

Run:

* gRPC endpoint
* REST endpoint

Example:

```text id="nl25cl"
gRPC:
localhost:50051

REST:
localhost:8080/generate
```

---

# PHASE 2 — Instrumentation

You CANNOT benchmark without instrumentation.

---

# Step 4: Add TTFT Metrics

## Add Timestamp at Request Start

In Java gateway:

```java
long requestStartNs = System.nanoTime();
```

When first token arrives:

```java
long ttftNs = System.nanoTime() - requestStartNs;
```

Record:

* histogram
* p50
* p95
* p99

---

# Step 5: Add Serialization Timers

## REST JSON

Measure:

```java
long s = System.nanoTime();
String json = mapper.writeValueAsString(req);
long e = System.nanoTime();
```

Record:

* encode time
* decode time

---

## gRPC Protobuf

Measure:

```java
long s = System.nanoTime();
byte[] bytes = proto.toByteArray();
long e = System.nanoTime();
```

and:

```java
PromptRequest.parseFrom(bytes);
```

---

# Step 6: Add Buffer Pool Metrics

Track:

```java
AtomicLong poolHits
AtomicLong poolMisses
AtomicLong allocations
AtomicLong releases
```

Expose metrics:

* hit ratio
* fallback allocations
* active buffers

---

# Step 7: Enable GC Logging

Run JVM with:

```bash
-Xms16g
-Xmx16g
-XX:+UseG1GC
-Xlog:gc*:file=gc.log
```

---

# Step 8: Enable JFR

Add:

```bash
-XX:StartFlightRecording=filename=grpc.jfr
```

This captures:

* allocations
* lock contention
* CPU
* sockets
* threads

---

# Step 9: Install System Monitoring Tools

Install:

```bash
sudo apt install sysstat
sudo apt install linux-tools-common
```

Use:

```bash
pidstat -u -r -d 1
```

and:

```bash
perf stat -p PID
```

---

# PHASE 3 — Build Load Generator

DO NOT use Postman/JMeter.

You need a custom async load generator.

---

# Step 10: Build gRPC Streaming Load Generator

## Java Async Stub Example

Create:

* 5k async bidi streams
* each sends 8k-token prompt

Pseudo-flow:

```text id="d6zrxk"
for i in 1..5000:
    open grpc stream
    send PromptRequest
    wait for streamed TokenChunks
```

---

# Step 11: Measure TTFT Client-Side

In client:

```java
long requestStart = System.nanoTime();
AtomicBoolean first = new AtomicBoolean(false);

onNext(TokenChunk chunk) {
   if (first.compareAndSet(false, true)) {
      long ttft = System.nanoTime() - requestStart;
   }
}
```

Store results in:

* HDR Histogram
* Micrometer
* Prometheus

---

# Step 12: Build REST Streaming Load Generator

Use:

* chunked transfer
* SSE
* streaming response body

NOT normal blocking REST.

---

# Step 13: Generate Realistic 8k Prompts

Create deterministic prompts.

Example Python:

```python
prompt = "Explain distributed systems in detail. " * 1000
```

Verify token count:

Use tokenizer:

* 8k input tokens

---

# PHASE 4 — Warmup

# Step 14: Warmup GPU + JVM

Before measuring:

Run:

* 200 requests
* for 3–5 minutes

This stabilizes:

* JIT
* CUDA kernels
* vLLM cache
* allocator behavior

---

# PHASE 5 — Execute Benchmarks

# Step 15: Run Baseline REST Test

## Concurrency Levels

Run sequentially:

```text id="f3e8wo"
100
500
1000
2000
5000
```

For each:

* duration = 5 min
* collect metrics

Command example:

```bash
java -jar loadgen.jar \
  --mode=rest \
  --concurrency=5000 \
  --duration=300
```

---

# Step 16: Capture Metrics During Test

Run simultaneously:

## CPU

```bash
pidstat -u -r -d 1 > cpu.log
```

## JVM

```bash
jcmd PID GC.heap_info
```

## GPU

```bash
nvidia-smi dmon
```

---

# Step 17: Run gRPC Benchmark

Same exact:

* prompts
* concurrency
* duration
* output length

Only transport changes.

Example:

```bash
java -jar loadgen.jar \
  --mode=grpc \
  --concurrency=5000 \
  --duration=300
```

---

# PHASE 6 — Backpressure Testing

# Step 18: Simulate Slow Consumer

Modify client:

```java
onNext(TokenChunk chunk) {
   Thread.sleep(50);
}
```

This creates downstream pressure.

Verify:

* memory stays bounded
* no queue explosion
* no OOM

Monitor:

* queue depth
* pending writes
* direct memory

---

# Step 19: Observe Netty Flow Control

Track:

* writable state
* pending bytes

Add logs:

```java
channel.isWritable()
```

---

# PHASE 7 — Cancellation Testing

# Step 20: Randomly Cancel Streams

Cancel 20% of streams.

Example:

```java
if (random.nextInt(100) < 20) {
    call.cancel("test cancel", null);
}
```

Verify:

* vLLM stops generation
* buffers released
* streams cleaned
* GPU work removed

Measure:

* cancellation latency

---

# PHASE 8 — Buffer Pool Validation

# Step 21: Disable Pool

Run benchmark:

```text id="g5xw75"
pool_enabled=false
```

Record:

* GC pauses
* allocations/sec
* TTFT

---

# Step 22: Enable Pool

Run again:

```text id="k7g0h1"
pool_enabled=true
```

Compare:

* allocation rate
* p99 latency
* direct memory usage

---

# PHASE 9 — Analyze Results

# Step 23: Analyze GC

Open:

```bash
gc.log
```

Look for:

* pause duration
* allocation spikes
* promotion failures

---

# Step 24: Analyze JFR

Open `.jfr` in:

JDK Mission Control

Inspect:

* allocation hotspots
* lock contention
* socket waits
* thread scheduling

---

# Step 25: Analyze Serialization Overhead

Compute:

```text id="6z9lfy"
serialization_time / total_request_time
```

Usually:

* REST JSON much higher
* protobuf negligible

---

# PHASE 10 — Final Report

# Step 26: Produce Comparison Table

Example:

| Metric            | REST       | gRPC       |
| ----------------- | ---------- | ---------- |
| p50 TTFT          | 420ms      | 180ms      |
| p99 TTFT          | 2.1s       | 850ms      |
| Throughput        | 1.8k req/s | 4.9k req/s |
| CPU               | 88%        | 54%        |
| Allocation Rate   | 14 GB/s    | 3 GB/s     |
| GC Pause p99      | 180ms      | 22ms       |
| Avg Serialization | 8ms        | 0.8ms      |

---

# PHASE 11 — Soak Test

# Step 27: Run Long Duration Test

Run:

```text id="8j0m7j"
5000 concurrent
6 hours
```

Watch for:

* memory leaks
* direct memory exhaustion
* thread leaks
* queue growth

---

# PHASE 12 — Optional Advanced Validation

# Step 28: Add Network Latency

Use:

```bash
sudo tc qdisc add dev eth0 root netem delay 50ms
```

Then rerun benchmarks.

This reveals:

* HTTP/JSON sensitivity
* gRPC streaming resilience

---

# PHASE 13 — Recommended Dashboard

Use:

* Prometheus
* Grafana

Dashboard panels:

* active streams
* TTFT histogram
* queue depth
* buffer pool hit ratio
* GC pause
* req/sec
* tok/sec

---

# Most Important Validation

Your system is successful if at 5k concurrent streams:

* TTFT remains stable
* memory remains bounded
* cancellation works instantly
* no OOM
* no excessive GC
* protobuf CPU cost is minimal
* direct buffer pool drastically reduces allocations
* gRPC significantly outperforms REST under load
