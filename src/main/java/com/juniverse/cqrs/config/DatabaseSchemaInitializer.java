package com.juniverse.cqrs.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Crea las tablas de cada lado al arrancar (los scripts son idempotentes: {@code create table if not exists}).
 * Se ejecuta en el constructor, cuando los pools ya tienen sus propiedades; los componentes que usan las bases lo reciben
 * como dependencia para garantizar que el esquema existe antes que ellos.
 */
@Component
public class DatabaseSchemaInitializer {

    /**
     * Aplica {@code write-schema.sql} a PostgreSQL y {@code read-schema.sql} a MySQL.
     *
     * @param writeDataSource base de escritura
     * @param readDataSource base de lectura
     */
    public DatabaseSchemaInitializer(@Qualifier("writeDataSource") DataSource writeDataSource, @Qualifier("readDataSource") DataSource readDataSource) {
        new ResourceDatabasePopulator(new ClassPathResource("write-schema.sql")).execute(writeDataSource);
        new ResourceDatabasePopulator(new ClassPathResource("read-schema.sql")).execute(readDataSource);
    }
}
