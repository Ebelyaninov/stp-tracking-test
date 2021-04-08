package ru.qa.tinkoff.investTracking.configuration;


import com.datastax.driver.core.AuthProvider;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PlainTextAuthProvider;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.MappingManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCqlTemplateConfiguration;
import org.springframework.data.cassandra.config.ClusterBuilderConfigurer;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;

@Configuration
@ComponentScan("ru.qa.tinkoff.investTracking")
@EnableConfigurationProperties(InvestTrackingCassandraDbConfigurationProperties.class)
public class InvestTrackingAutoConfiguration extends AbstractCqlTemplateConfiguration {

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
        return mappingManager;
    }
}
