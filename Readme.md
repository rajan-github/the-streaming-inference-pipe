## Problem Statement: Bidirectional Streaming Interface

### Objective

Design and implement a high-performance, bidirectional streaming interface between a **Java Gateway** and a **Python vLLM Server** utilizing **gRPC** and **Protocol Buffers**.

### Requirements

#### 1. Schema Definition

Define a robust Protobuf schema that includes the following message structures:

* `PromptRequest`
* `TokenChunk`
* `StopSignal`

#### 2. Core Implementation

* **Bidirectional Streaming RPC:** Establish a stable, two-way streaming channel between the Java Gateway and the Python vLLM Server.
* **Cancellation Propagation:** Ensure that if a client or the gateway cancels a request, the cancellation signal immediately propagates to the Python vLLM server to halt token generation.
* **Backpressure Handling:** Implement flow control to manage data rates between the producer and consumer, preventing memory exhaustion on either side.

#### 3. Optimization & Alternatives

* **DirectByteBuffer Pooling:** Implement pooling of `DirectByteBuffer` instances on the Java side to reduce memory allocation overhead and optimize off-heap memory usage.
* **REST Baseline:** Implement a baseline REST endpoint to serve as a standard comparison architecture against the gRPC implementation.





Here is a toned-down, more realistic version of the `README.md`. It reflects a standard, working implementation using default configurations while moving advanced production optimizations (like custom OOM safeguards and fine-tuned Netty configurations) to a "Future Roadmap" section.

---

# The Streaming Inference Pipe

A bidirectional streaming interface linking a **Java Gateway** (Spring WebFlux / Netty) to a **Python vLLM Server** using **gRPC** and **Protocol Buffers**.

This project establishes an asynchronous pipeline optimized for passing prompts and streaming back generated LLM token chunks over a persistent network boundary.

```
+--------------+                   +--------------------+
|              | --(Streaming)-->  |                    |
| Java Gateway |                   | Python vLLM Server |
|  (WebFlux)   | <-- (Tokens) ---- |    (Async vLLM)    |
+--------------+                   +--------------------+

```

## Features

* **Bidirectional Streaming RPC:** Utilizes gRPC over HTTP/2 to allow concurrent prompt submission and asynchronous token chunk responses.
* **Basic Cancellation Propagation:** Leverages gRPC's context cancellation; dropping or interrupting a stream on the Java side triggers a cleanup path to signal the Python vLLM server to halt token generation.
* **DirectByteBuffer Pooling:** Integrates standard `DirectByteBuffer` reuse patterns on the Java side to reduce object allocation overhead during chunk handling.
* **REST Baseline Endpoint:** Includes a basic REST/JSON controller alongside the gRPC configuration to serve as a comparative baseline.

---

## Architecture Blueprint

### 1. Protobuf Schema

The interface between the runtimes is governed by the Protocol Buffers definition (`/proto/inference.proto`):

```protobuf
syntax = "proto3";

package inference;

option java_multiple_files = true;
option java_package = "com.pipeline.inference";

message PromptRequest {
  string request_id = 1;
  string prompt = 2;
  float temperature = 3;
  int32 max_tokens = 4;
}

message TokenChunk {
  string text = 1;
  int32 token_id = 2;
  bool is_special = 3;
}

message StopSignal {
  enum Reason {
    FINISH = 0;
    LENGTH = 1;
    CANCELLED = 2;
  }
  Reason reason = 1;
  int32 total_tokens_generated = 2;
}

```

### 2. Implementation State

* **Java Gateway:** Built on **Spring WebFlux** and **Netty** using standard out-of-the-box configurations to manage the incoming client endpoints and map them to gRPC `StreamObserver` stubs.
* **Python Backend:** Uses `grpc.aio` alongside vLLM's `AsyncLLMEngine` to listen for requests and yield generated text blocks back to the Java client.

---

## Setup & Execution

### Prerequisites

* Java 17+
* Python 3.10+
* Protobuf Compiler (`protoc`)

### Installation

1. **Clone the repository:**
```bash
git clone https://github.com/rajan-github/the-streaming-inference-pipe.git
cd the-streaming-inference-pipe

```


2. **Generate Protobuf Stubs:**
```bash
# Java Stub Generation
mvn protobuf:compile protobuf:compile-custom

# Python Stub Generation
mkdir -p src/vllm_server/generated/python

python -m grpc_tools.protoc \
  -I proto \
  --python_out=src/vllm_server/generated/python \
  --grpc_python_out=src/vllm_server/generated/python \
  proto/llm_service.proto

```


3. **Install Python Dependencies:**
```bash
pip install -r requirements.txt

```



### Running the Services

* **Start the Python vLLM Server:**
```bash
python src/vllm_server/vllm_server.py

python src/vllm_server/vllm_server_rest.py (REST)
```


* **Start the Java Gateway:**
```bash
mvn clean package
java -jar target/java-gateway.jar

```



---

## Future Roadmap (Yet to Implement)

The project currently utilizes default WebFlux and Netty network settings. To make this pipeline enterprise-ready, the following architectural additions are planned:

* [ ] **Advanced Backpressure & OOM Protection:** Implement strict application-level reactive streams flow control to protect the JVM heap from being overwhelmed by high-throughput token streams.
* [ ] **Custom Netty Tuning:** Configure dedicated event loop groups, custom buffer watermarks, and keep-alive parameters rather than relying on WebFlux defaults.
* [ ] **Robust Exception Propagation:** Enhance the gRPC interceptor layer to map internal vLLM failures to standard HTTP/gRPC status codes cleanly.