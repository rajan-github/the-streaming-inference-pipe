package com.llm.gateway.interceptors;

import io.grpc.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class GrpcSerializationInterceptor implements ClientInterceptor {
    private final MeterRegistry meterRegistry;


    @Autowired
    public GrpcSerializationInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {

        CallOptions callOptionsWithTracer = callOptions.withStreamTracerFactory(new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                return new ClientStreamTracer() {
                    private long outboundStart;
                    private long inboundStart;

                    @Override
                    public void outboundMessage(int id) {
                        outboundStart = System.nanoTime();
                    }

                    @Override
                    public void outboundMessageSent(int id, long wireBytes, long uncompressedBytes) {
                        long duration = System.nanoTime() - outboundStart;
                        Timer.builder("inference.pipe.serialization.overhead")
                                .tag("protocol", "grpc")
                                .tag("direction", "outbound")
                                .register(meterRegistry)
                                .record(duration, TimeUnit.NANOSECONDS);
                    }

                    @Override
                    public void inboundMessage(int id) {
                        inboundStart = System.nanoTime();
                    }

                    @Override
                    public void inboundMessageRead(int id, long wireBytes, long uncompressedBytes) {
                        long duration = System.nanoTime() - inboundStart;
                        Timer.builder("inference.pipe.serialization.overhead")
                                .tag("protocol", "grpc")
                                .tag("direction", "inbound")
                                .register(meterRegistry)
                                .record(duration, TimeUnit.NANOSECONDS);
                    }
                };
            }
        });

        return channel.newCall(methodDescriptor, callOptionsWithTracer);
    }
}
