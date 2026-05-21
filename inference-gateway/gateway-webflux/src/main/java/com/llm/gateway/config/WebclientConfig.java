package com.llm.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebclientConfig {

    private final LLMServerProperties llmServerProperties;

    @Autowired
    public WebclientConfig(LLMServerProperties llmServerProperties) {
        this.llmServerProperties = llmServerProperties;
    }

    @Bean
    public WebClient createWebClient() {
        return WebClient.builder().baseUrl(llmServerProperties.getRestServerBaseUrl()).build();
    }
}
