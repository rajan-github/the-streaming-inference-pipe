package com.llm.gateway.services;

import com.llm.LLMServiceGrpc;
import com.llm.Llm;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class GrpcLlmClient {
    private final LLMServiceGrpc.LLMServiceStub llmServiceStub;
    private final MeterRegistry meterRegistry;

    public GrpcLlmClient(final Channel channel, MeterRegistry meterRegistry) {
        this.llmServiceStub = LLMServiceGrpc.newStub(channel);
        this.meterRegistry = meterRegistry;
    }


    public Flux<String> generateResponse(final String prompt, final int maxTokens, final float temp) {
        log.debug("GrpcLlmClient- Generating Llm.TokenChunk response");
        return Flux.create(sink -> {
            final AtomicBoolean isFirstToken=new AtomicBoolean(true);
            final long startTime=System.nanoTime();
            StreamObserver<Llm.TokenResponse> responseObserver = new StreamObserver<Llm.TokenResponse>() {
                @Override
                public void onNext(Llm.TokenResponse tokenResponse) {
                    if (tokenResponse.hasChunk()) {
                        if(isFirstToken.compareAndSet(true, false)){
                            long duration=System.nanoTime() - startTime;
                            Timer.builder("inference.pipe.ttft")
                                    .description("Time to first token for llm response")
                                    .tag("protocol", "grpc")
                                    .register(meterRegistry)
                                    .record(duration, TimeUnit.NANOSECONDS);
                        }
                        sink.next(tokenResponse.getChunk().getText());
                    } else if (tokenResponse.hasStop()) {
                        log.info("Streaming stopped by remote engine. Reason: {}", tokenResponse.getStop().getReason());
                        sink.complete();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Grpc stream encountered an error", throwable);
                    sink.error(throwable);
                }

                @Override
                public void onCompleted() {
                    log.info("Streaming completed by remote engine successfully.");
                    sink.complete();
                }
            };

            StreamObserver<Llm.PromptRequest> requestObserver = llmServiceStub.streamGenerate(responseObserver);
            try {
                Llm.PromptRequest promptRequest = Llm.PromptRequest.newBuilder()
                        .setPrompt(prompt)
                        .setMaxTokens(maxTokens)
                        .setTemperature(temp)
                        .build();
                requestObserver.onNext(promptRequest);
                requestObserver.onCompleted();
            } catch (Exception e) {
                log.error("Failed to transmit initial frame down gRPC stream pipeline", e);
                requestObserver.onError(e);
                sink.error(e);
            }

            sink.onCancel(() -> {
                responseObserver.onError(Status.CANCELLED.withDescription("Web client disconnected").asRuntimeException());
            });
        });
    }
}
