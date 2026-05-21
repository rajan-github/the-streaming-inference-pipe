package com.llm.gateway.config;

import com.llm.gateway.services.GrpcLlmClient;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcLlmClientConfig {
    private final LLMServerProperties llmServerProperties;

    @Autowired
    public GrpcLlmClientConfig(LLMServerProperties llmServerProperties) {
        this.llmServerProperties = llmServerProperties;
    }

    @Bean
    public GrpcLlmClient createGrpcLlmClient() {
        final String target = llmServerProperties.getGrpcLlmServerHost() + ":" + llmServerProperties.getGrpcLlmServerPort();
        final ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();
        return new GrpcLlmClient(channel);
    }
}
