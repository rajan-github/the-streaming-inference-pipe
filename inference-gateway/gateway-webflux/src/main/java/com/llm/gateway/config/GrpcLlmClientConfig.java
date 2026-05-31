package com.llm.gateway.config;

import com.llm.gateway.interceptors.GrpcSerializationInterceptor;
import com.llm.gateway.services.GrpcLlmClient;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcLlmClientConfig {
    private final LLMServerProperties llmServerProperties;
    private final GrpcSerializationInterceptor grpcSerializationInterceptor;
    private final MeterRegistry meterRegistry;

    @Autowired
    public GrpcLlmClientConfig(LLMServerProperties llmServerProperties, GrpcSerializationInterceptor grpcSerializationInterceptor, MeterRegistry meterRegistry) {
        this.llmServerProperties = llmServerProperties;
        this.grpcSerializationInterceptor = grpcSerializationInterceptor;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public GrpcLlmClient createGrpcLlmClient() {
        final String target = llmServerProperties.getGrpcLlmServerHost() + ":" + llmServerProperties.getGrpcLlmServerPort();
        final ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                .intercept(grpcSerializationInterceptor)
                .build();
        return new GrpcLlmClient(channel, meterRegistry);
    }
}
