package com.llm.gateway.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class LLMServerProperties {
    @Value(value = "${llm.server.rest.base_url}")
    private String restServerBaseUrl;

    @Value(value = "${llm.server.grpc.host}")
    private String grpcLlmServerHost;

    @Value(value = "${llm.server.grpc.port}")
    private int grpcLlmServerPort;
}
