package com.llm.gateway.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class LLMServerProperties {
    @Value(value = "${llm.server.base_url}")
    private String baseUrl;
}
