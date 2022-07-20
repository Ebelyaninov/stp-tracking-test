package ru.qa.tinkoff.investTracking.configuration;


import com.datastax.driver.core.*;
import com.datastax.driver.mapping.MappingManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCqlTemplateConfiguration;
import org.springframework.data.cassandra.config.ClusterBuilderConfigurer;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;

@Configuration
@ComponentScan("ru.qa.tinkoff.investTracking")
@EnableConfigurationProperties(InvestTrackingCassandraDbConfigurationProperties.class)
public class InvestTrackingAutoConfiguration extends AbstractCqlTemplateConfiguration {

    @Getter
    private ConsistencyLevel defaultConsistencyLevel = ConsistencyLevel.LOCAL_QUORUM;
    @Getter
    private ConsistencyLevel writeConsistencyLevel = ConsistencyLevel.EACH_QUORUM;

    @Bean
    public InvestTrackingCassandraDbConfigurationProperties dbConf() {
        return new InvestTrackingCassandraDbConfigurationProperties();
    }

    @Override
    protected ClusterBuilderConfigurer getClusterBuilderConfigurer() {
        return Cluster.Builder::withoutJMXReporting;
    }

    @Override
    protected String getKeyspaceName() {
        return dbConf().getKeyspaceName();
    }

    @Override
    protected AuthProvider getAuthProvider() {
        return new PlainTextAuthProvider(dbConf().getUsername(), dbConf().getPassword());
    }


    @Override
    protected QueryOptions getQueryOptions() {
        var queryOptions = super.getQueryOptions();
        if (queryOptions == null) {
            queryOptions = new QueryOptions();
        }
        return queryOptions.setConsistencyLevel(defaultConsistencyLevel);
    }


    @Override
    protected String getClusterName() {
        return dbConf().getClusterName();
    }

    @Override
    protected String getContactPoints() {
        return dbConf().getContactPoints();
    }

    @Override
    protected int getPort() {
        return dbConf().getPort();
    }

    @Bean
    public MappingManager mappingManager(Session session) {
        MappingManager mappingManager = new MappingManager(session);
        mappingManager.udtCodec(MasterPortfolio.Position.class);
        mappingManager.udtCodec(MasterPortfolio.BaseMoneyPosition.class);
        mappingManager.udtCodec(SlavePortfolio.Position.class);
        mappingManager.udtCodec(SlavePortfolio.BaseMoneyPosition.class);
        mappingManager.udtCodec(MasterPortfolioTopPositions.TopPositions.class);
        return mappingManager;
    }

//    @Bean
//    public ObjectMapper contextMapper() {
//        var objectMapper = new ObjectMapper();
//        objectMapper.setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS);
//        return objectMapper;
//    }

}
