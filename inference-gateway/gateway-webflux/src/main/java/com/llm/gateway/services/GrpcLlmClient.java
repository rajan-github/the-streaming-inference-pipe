package com.llm.gateway.services;

import com.llm.LLMServiceGrpc;
import com.llm.Llm;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class GrpcLlmClient {
    private final LLMServiceGrpc.LLMServiceStub llmServiceStub;

    public GrpcLlmClient(final Channel channel) {
        this.llmServiceStub = LLMServiceGrpc.newStub(channel);
    }


    public Flux<String> generateResponse(final String prompt, final int maxTokens, final float temp) {
        log.debug("GrpcLlmClient- Generating Llm.TokenChunk response");
        return Flux.create(sink -> {
            StreamObserver<Llm.TokenResponse> responseObserver = new StreamObserver<Llm.TokenResponse>() {
                @Override
                public void onNext(Llm.TokenResponse tokenResponse) {
                    if (tokenResponse.hasChunk()) {
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
