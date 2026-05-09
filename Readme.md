# Problem

A bidirectional streaming interface:
Java Gateway ⇄ Python vLLM Server
Using:
gRPC


Protocol Buffers


vLLM


Requirements
Define Protobuf schema:


PromptRequest


TokenChunk


StopSignal


Implement:


Bidi-streaming RPC


Cancellation propagation


Backpressure handling


Add REST baseline endpoint for comparison


Implement DirectByteBuffer pooling



How to Test
Load test:
5k concurrent streaming requests


Context window = 8k tokens


Measure:
Time to First Token (TTFT)


Serialization overhead


CPU usage


GC pressure


Compare:
 REST (JSON) vs gRPC (Protobuf)


# Request Flow
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
