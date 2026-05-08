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
