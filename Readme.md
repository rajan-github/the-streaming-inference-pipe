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



## Request Flow
A. The Java Gateway (Client)
Request Arrival: A user sends a prompt. The Gateway "rents" a DirectByteBuffer from the Netty Pool.

Serialization: The PromptRequest (Protobuf) is serialized. Because we use gRPC, this isn't converted to a string (like JSON); it stays in a binary format that fits perfectly into our pooled buffer.

The Pipe: It’s sent over a HTTP/2 connection (the backbone of gRPC).

B. The Python vLLM Server (Server)
Async Reception: The gRPC server (running grpc.aio) receives the binary blob. It doesn't "block" a thread; it handles it like a notification.

The Yielding Loop: The server calls engine.generate(prompt). This returns an Asynchronous Generator (a stream).

Token Generation: Every time the GPU produces a token (e.g., "The"), the AsyncLLMEngine yields it.

Backpressure Check: Before sending the token back to Java, the Python server checks: "Is the Java pipe full?" If Java is struggling to keep up, Python will actually pause the generator to avoid memory overflow.

C. The Return Trip
Binary Streaming: The TokenChunk is sent back.

Java Reception: The Java Gateway receives the binary chunk. Because of our DirectByteBuffer setup, the data is placed directly into off-heap memory.

Final Delivery: The Gateway sends this to the end-user (perhaps via a WebSocket or SSE). Once sent, the buffer is released back to the pool.

