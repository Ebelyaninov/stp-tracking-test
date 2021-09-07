package ru.qa.tinkoff.tracking.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import java.util.HashMap;

@ComponentScan("ru.qa.tinkoff.tracking")
@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "ru.qa.tinkoff.tracking.repositories",
    entityManagerFactoryRef = "trackingEntityManagerFactory",
    transactionManagerRef = "trackingTransactionManager")
public class TrackingDatabaseAutoConfiguration {

    @Bean
//    @Primary
    @ConfigurationProperties("app.datasource.tracking")
    public DataSourceProperties trackingDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.tracking.configuration")
    public HikariDataSource trackingDataSource() {
        return trackingDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    @Bean(name = "trackingEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean trackingEntityManagerFactory(
        @Qualifier("trackingBuilder") EntityManagerFactoryBuilder builder) {
        return builder
            .dataSource(trackingDataSource())
            .packages("ru.qa.tinkoff.tracking.entities")
            .build();
    }

    @Bean(name = "trackingTransactionManager")
    public PlatformTransactionManager trackingTransactionManager(
        @Qualifier("trackingEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean(name = "trackingBuilder")
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        return new EntityManagerFactoryBuilder(new HibernateJpaVendorAdapter(), new HashMap<>(), null);
    }
}
