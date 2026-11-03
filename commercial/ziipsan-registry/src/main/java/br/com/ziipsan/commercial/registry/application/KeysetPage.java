package br.com.ziipsan.commercial.registry.application;

import java.util.List;

/**
 * Página keyset (P0.22): ao contrário de {@link PageResult}, não traz total nem número
 * de páginas — contar o total anularia a vantagem do keyset nas listas grandes.
 * {@code nextCursor} é nulo quando não há próxima página; o cliente o devolve opaco
 * na próxima chamada.
 */
public record KeysetPage<T>(List<T> content, KeysetCursor nextCursor, boolean hasNext) {

    public <R> KeysetPage<R> map(java.util.function.Function<T, R> mapper) {
        return new KeysetPage<>(content.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}
