package com.juniverse.cqrs;

import com.juniverse.cqrs.projection.CqrsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <b>CQRS</b> (Command Query Responsibility Segregation) con dos bases de datos:
 * <ul>
 *   <li><b>Escritura (PostgreSQL)</b>: el modelo del dominio, normalizado y con reglas de negocio, más el registro de
 *       eventos. Solo recibe <i>comandos</i> (crear, confirmar, cancelar pedidos).</li>
 *   <li><b>Lectura (MySQL)</b>: vistas desnormalizadas y pensadas para consultar (listados, estadísticas). Solo recibe
 *       <i>consultas</i>. Se construye a partir de los eventos, de forma asíncrona (consistencia eventual).</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsApplication {

    /**
     * Arranca la aplicación.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(CqrsApplication.class, args);
    }
}
