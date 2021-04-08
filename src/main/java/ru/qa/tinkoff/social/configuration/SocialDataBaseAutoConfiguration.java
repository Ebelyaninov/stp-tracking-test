package ru.qa.tinkoff.social.configuration;

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

@ComponentScan("ru.qa.tinkoff.social")
@Configuration
@EnableAutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "ru.qa.tinkoff.social.repositories",
    entityManagerFactoryRef = "socialEntityManagerFactory",
    transactionManagerRef = "socialTransactionManager")
public class SocialDataBaseAutoConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource.social")
    public DataSourceProperties socialDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.social.configuration")
    public HikariDataSource socialDataSource() {
        return socialDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    @Bean(name = "socialEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean socialEntityManagerFactory(
        @Qualifier("socialBuilder") EntityManagerFactoryBuilder builder) {
        return builder
            .dataSource(socialDataSource())
            .packages("ru.qa.tinkoff.social.entities")
            .build();
    }

    @Bean(name = "socialTransactionManager")
    public PlatformTransactionManager socialTransactionManager(
        @Qualifier("socialEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean(name = "socialBuilder")
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        return new EntityManagerFactoryBuilder(new HibernateJpaVendorAdapter(), new HashMap<>(), null);
    }


}
