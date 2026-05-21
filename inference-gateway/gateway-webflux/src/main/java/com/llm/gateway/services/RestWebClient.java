package com.llm.gateway.services;

import com.llm.gateway.models.LlmPromptRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class RestWebClient {
    private final WebClient webClient;

    @Autowired
    public RestWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<String> fetchLLMResponse(final String prompt, int maxTokens, Integer temp) {
        log.info("fetchLLMResponse is invoked with {}", prompt);
        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LlmPromptRequest(prompt, maxTokens))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class);
    }
}
