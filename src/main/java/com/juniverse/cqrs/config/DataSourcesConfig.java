package com.juniverse.cqrs.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dos bases de datos distintas, cada una con su pool, su gestor de transacciones y su cliente JDBC:
 * <ul>
 *   <li>{@code write*}: PostgreSQL (lado de comandos).</li>
 *   <li>{@code read*}: MySQL (lado de consultas).</li>
 * </ul>
 * No se pueden agrupar en una sola transacción: por eso la sincronización es asíncrona y basada en eventos.
 */
@Configuration
public class DataSourcesConfig {

    /**
     * Pool de PostgreSQL (escritura). Spring enlaza las propiedades {@code app.datasource.write.*} después de crearlo.
     *
     * @return origen de datos de escritura
     */
    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.write")
    public DataSource writeDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Pool de MySQL (lectura). Spring enlaza las propiedades {@code app.datasource.read.*} después de crearlo.
     *
     * @return origen de datos de lectura
     */
    @Bean
    @ConfigurationProperties("app.datasource.read")
    public DataSource readDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Gestor de transacciones de PostgreSQL.
     *
     * @param dataSource origen de escritura
     * @return gestor
     */
    @Bean
    @Primary
    public PlatformTransactionManager writeTx(@Qualifier("writeDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * Gestor de transacciones de MySQL.
     *
     * @param dataSource origen de lectura
     * @return gestor
     */
    @Bean
    public PlatformTransactionManager readTx(@Qualifier("readDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * Cliente JDBC de escritura.
     *
     * @param dataSource origen de escritura
     * @return cliente
     */
    @Bean
    @Primary
    public JdbcClient writeJdbc(@Qualifier("writeDataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    /**
     * Cliente JDBC de lectura.
     *
     * @param dataSource origen de lectura
     * @return cliente
     */
    @Bean
    public JdbcClient readJdbc(@Qualifier("readDataSource") DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    /**
     * Plantilla para ejecutar bloques en una transacción de MySQL.
     *
     * @param readTx gestor de transacciones de lectura
     * @return plantilla
     */
    @Bean
    public TransactionTemplate readTransactionTemplate(@Qualifier("readTx") PlatformTransactionManager readTx) {
        return new TransactionTemplate(readTx);
    }
}
