package ru.qa.tinkoff.tariff.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import java.util.HashMap;

@ComponentScan("ru.qa.tinkoff.tariff")
@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "ru.qa.tinkoff.tariff.repositories",
    entityManagerFactoryRef = "tariffEntityManagerFactory",
    transactionManagerRef = "tariffTransactionManager")
public class TariffDataBaseAutoConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource.tariff")
    public DataSourceProperties tariffDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.tariff.configuration")
    public HikariDataSource tariffDataSource() {
        return tariffDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    @Bean(name = "tariffEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tariffEntityManagerFactory(
        @Qualifier("tariffBuilder") EntityManagerFactoryBuilder builder) {
        return builder
            .dataSource(tariffDataSource())
            .packages("ru.qa.tinkoff.tariff.entities")
            .build();
    }

    @Bean(name = "tariffTransactionManager")
    public PlatformTransactionManager tariffTransactionManager(
        @Qualifier("tariffEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean(name = "tariffBuilder")
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        return new EntityManagerFactoryBuilder(new HibernateJpaVendorAdapter(), new HashMap<>(), null);
    }
}
