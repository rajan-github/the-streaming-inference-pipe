package com.llm.gateway.controller;

import com.llm.gateway.models.GenerateApiDelegate;
import com.llm.gateway.services.GrpcLlmClient;
import com.llm.gateway.services.RestWebClient;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.model.PromptRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GatewayController implements GenerateApiDelegate {
    private final RestWebClient restWebClient;
    private final GrpcLlmClient grpcLlmClient;

    @Autowired
    public GatewayController(RestWebClient restWebClient, GrpcLlmClient grpcLlmClient) {
        this.restWebClient = restWebClient;
        this.grpcLlmClient = grpcLlmClient;
    }

    @Override
    public Mono<ResponseEntity<Flux<String>>> generate(Mono<org.openapitools.model.PromptRequest> promptRequestMono, ServerWebExchange exchange) {
        log.info("GatewayController: generate is invoked");
        return promptRequestMono.map(promptRequest -> {
            log.info("GatewayController: generate is invoked with payload: {}", promptRequest);
            if (promptRequest.getPrompt() == null || promptRequest.getPrompt().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is empty");
            }
            var temp = promptRequest.getTemperature() == null ? 0 : promptRequest.getTemperature().getValue();
            int maxTokens = Math.min(promptRequest.getMaxTokens() == null ? 8000 : promptRequest.getMaxTokens(), 8000);
            if (promptRequest.getEncoding() == PromptRequest.EncodingEnum.JSON) {
                log.info("GatewayController: generate is invoked with encoding json, generating response with JSON encoding");
                return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(restWebClient.fetchLLMResponse(promptRequest.getPrompt(), maxTokens, temp));
            } else {
                log.info("GatewayController: generate is invoked with encoding json, generating response with Protobuf encoding");
                return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(grpcLlmClient.generateResponse(promptRequest.getPrompt(), maxTokens, temp));
            }
        });
    }
}
