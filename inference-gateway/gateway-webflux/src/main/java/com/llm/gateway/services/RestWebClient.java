package com.llm.gateway.services;

import com.llm.gateway.models.LlmPromptRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.awt.image.DataBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class RestWebClient {
    private final WebClient webClient;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestWebClient(WebClient webClient, MeterRegistry meterRegistry) {
        this.webClient = webClient;
        this.meterRegistry = meterRegistry;
        this.objectMapper = new ObjectMapper();
    }

    public Flux<String> fetchLLMResponse(final String prompt, int maxTokens, Integer temp) {
        log.info("fetchLLMResponse is invoked with {}", prompt);
        final AtomicBoolean isFirstToken = new AtomicBoolean(true);
        final long startTime = System.nanoTime();
        final long startSerial = System.nanoTime();
        final byte[] requestBytes;
        try {
            requestBytes = objectMapper.writeValueAsBytes(new LlmPromptRequest(prompt, maxTokens));
            long serialDuration = System.nanoTime() - startSerial;
            Timer.builder("inference.pipe.serialization.overhead")
                    .tag("protocol", "REST")
                    .tag("direction", "outbound")
                    .register(meterRegistry)
                    .record(serialDuration, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            return Flux.error(new RuntimeException("fetchLLMResponse error"));
        }
        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBytes)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(dataBuffer -> {
                    long startDeserialization = System.nanoTime();
                    String token = dataBuffer.toString();
                    long duration = System.nanoTime() - startDeserialization;
                    Timer.builder("inference.pipe.serialization.overhead")
                            .tag("protocol", "REST")
                            .tag("direction", "inbound")
                            .register(meterRegistry)
                            .record(duration, TimeUnit.NANOSECONDS);
                    return token;
                })
                .doOnNext(token -> {
                    if (isFirstToken.compareAndSet(true, false)) {
                        long duration = System.nanoTime() - startTime;
                        Timer.builder("inference.pipe.ttft")
                                .description("Time to first token for llm response")
                                .tag("protocol", "REST")
                                .register(meterRegistry)
                                .record(duration, TimeUnit.NANOSECONDS);
                    }
                });
    }
}
