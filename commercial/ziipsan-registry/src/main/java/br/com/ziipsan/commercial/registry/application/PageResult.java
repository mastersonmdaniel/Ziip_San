package br.com.ziipsan.commercial.registry.application;

import java.util.List;
import java.util.function.Function;

/**
 * Página no formato que o frontend lê ({@code normalizePage} em frontend/src/services/registry/client.ts):
 * {@code {content, page, size, totalElements, totalPages}}, com {@code page} começando em 0.
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(List<T> content, PageSpec spec, long total) {
        int pages = (int) ((total + spec.size() - 1) / spec.size());
        return new PageResult<>(List.copyOf(content), spec.page(), spec.size(), total, pages);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }

    public <R> PageResult<R> withContent(List<R> mapped) {
        return new PageResult<>(List.copyOf(mapped), page, size, totalElements, totalPages);
    }
}
