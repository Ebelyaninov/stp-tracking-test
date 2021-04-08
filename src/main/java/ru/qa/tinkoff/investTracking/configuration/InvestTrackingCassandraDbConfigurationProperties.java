package ru.qa.tinkoff.investTracking.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.datasource.cassandra.tracking")
public class InvestTrackingCassandraDbConfigurationProperties {
    String contactPoints;
    String keyspaceName;
    String clusterName;
    String username;
    String password;
    Integer port;
}
