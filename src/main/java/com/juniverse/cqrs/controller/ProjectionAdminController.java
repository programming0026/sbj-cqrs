package com.juniverse.cqrs.controller;

import com.juniverse.cqrs.repository.EventStore;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.juniverse.cqrs.service.projection.OrderProjector;
import com.juniverse.cqrs.service.projection.ProjectionRunner;

/** Operación y diagnóstico de las proyecciones: estado, retraso, pausa y reconstrucción. */
@RestController
@RequestMapping("/api/admin/projections")
public class ProjectionAdminController {

    private final EventStore events;
    private final OrderProjector projector;
    private final ProjectionRunner runner;

    /**
     * Crea el controlador.
     *
     * @param events registro de eventos
     * @param projector proyector
     * @param runner ejecutor periódico
     */
    public ProjectionAdminController(EventStore events, OrderProjector projector, ProjectionRunner runner) {
        this.events = events;
        this.projector = projector;
        this.runner = runner;
    }

    /**
     * Estado de la sincronización entre escritura y lectura.
     *
     * @return último evento escrito, último proyectado y el retraso entre ambos
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        long written = events.lastSeq();
        long projected = projector.projectedSeq();
        return Map.of("lastEventWritten", written, "lastEventProjected", projected, "lag", written - projected, "paused", runner.isPaused());
    }

    /**
     * Pausa la proyección (para observar la consistencia eventual).
     *
     * @return estado
     */
    @PostMapping("/pause")
    public Map<String, Object> pause() {
        runner.pause();
        return Map.of("paused", true);
    }

    /**
     * Reanuda la proyección.
     *
     * @return estado
     */
    @PostMapping("/resume")
    public Map<String, Object> resume() {
        runner.resume();
        return Map.of("paused", false);
    }

    /**
     * Borra las vistas de lectura y las reconstruye desde los eventos.
     *
     * @return cuántos eventos se reaplicaron
     */
    @PostMapping("/rebuild")
    public Map<String, Object> rebuild() {
        return Map.of("eventsReplayed", projector.rebuild());
    }
}
