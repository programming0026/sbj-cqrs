package com.juniverse.cqrs.projection;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el proyector periódicamente. Se puede pausar para observar la <b>consistencia eventual</b>: mientras está en
 * pausa, los comandos siguen funcionando pero las consultas no ven los cambios hasta que se reanude.
 */
@Component
public class ProjectionRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectionRunner.class);

    private final OrderProjector projector;
    private final AtomicBoolean paused = new AtomicBoolean();

    /**
     * Crea el ejecutor.
     *
     * @param projector proyector de pedidos
     */
    public ProjectionRunner(OrderProjector projector) {
        this.projector = projector;
    }

    /** Proyecta los eventos pendientes; la siguiente vuelta empieza {@code cqrs.projection-delay-ms} después de terminar la anterior. */
    @Scheduled(fixedDelayString = "${cqrs.projection-delay-ms}")
    public void run() {
        if (paused.get()) {
            return;
        }
        try {
            int applied = projector.catchUp();
            if (applied > 0) {
                LOG.info("Proyección: {} evento(s) aplicados a las vistas de lectura", applied);
            }
        } catch (RuntimeException e) {
            LOG.error("Falló la proyección (se reintentará en la siguiente vuelta)", e);
        }
    }

    /** Pausa la proyección. */
    public void pause() {
        paused.set(true);
    }

    /** Reanuda la proyección. */
    public void resume() {
        paused.set(false);
    }

    /**
     * Indica si está en pausa.
     *
     * @return {@code true} si está en pausa
     */
    public boolean isPaused() {
        return paused.get();
    }
}
