package com.juniverse.cqrs.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errores de la API en formato Problem Details (RFC 9457). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 404.
     *
     * @param e excepción
     * @return problema
     */
    @ExceptionHandler({OrderNotFoundException.class, NoSuchElementException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", e.getMessage());
    }

    /**
     * 400 por regla de dominio incumplida.
     *
     * @param e excepción
     * @return problema
     */
    @ExceptionHandler(InvalidCommandException.class)
    public ProblemDetail invalidCommand(InvalidCommandException e) {
        return problem(HttpStatus.BAD_REQUEST, "Comando inválido", e.getMessage());
    }

    /**
     * 409 por estado incompatible o modificación concurrente.
     *
     * @param e excepción
     * @return problema
     */
    @ExceptionHandler({InvalidOrderStateException.class, ConcurrentModificationConflictException.class})
    public ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Conflicto", e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Error de validación", "Uno o más campos no son válidos");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.juniverse.com/errors/" + status.value()));
        return problem;
    }
}
