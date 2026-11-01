package com.juniverse.cqrs.dto;

import java.util.List;

/**
 * Página de resultados.
 *
 * @param content elementos
 * @param page número de página (desde 0)
 * @param size tamaño
 * @param totalElements total de elementos
 * @param totalPages total de páginas
 * @param <T> tipo de elemento
 */
public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
