package ru.qa.tinkoff.tracking.services.grpc.utils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import ru.qa.tinkoff.tracking.configuration.GrpcServiceConfigurationProperties;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;

//@ComponentScan("ru.qa.tinkoff.tracking.services.grpc")
@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties(GrpcServiceConfigurationProperties.class)
public class GrpcServicesAutoConfiguration {

//    @Bean
//    public ManagedChannel managedChannel(GrpcServiceConfigurationProperties properties) {
//        return ManagedChannelBuilder.forAddress(properties.getUrl(), properties.getPort())
//            .usePlaintext()
//            .build();
//    }

    @Bean
    public MiddleGrpcService middleGrpcService(ManagedChannel managedChannel) {
        return new MiddleGrpcService(managedChannel);
    }

}
