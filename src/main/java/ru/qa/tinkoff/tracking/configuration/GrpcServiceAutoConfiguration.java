package ru.qa.tinkoff.tracking.configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.qa.tinkoff.tracking.services.grpc.TrackingGrpcService;

@Configuration
@EnableConfigurationProperties(GrpcServiceConfigurationProperties.class)
public class GrpcServiceAutoConfiguration {

    @Bean
    public ManagedChannel managedChannel(GrpcServiceConfigurationProperties properties) {
        return ManagedChannelBuilder.forAddress(properties.getUrl(), properties.getPort())
            .usePlaintext()
            .build();
    }

    @Bean
    public TrackingGrpcService trackingGrpcService(ManagedChannel managedChannel) {
        return new TrackingGrpcService(managedChannel);
    }
}
