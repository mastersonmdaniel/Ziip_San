package br.com.sigsan.commercial.registry.application;

import br.com.sigsan.core.api.ApiException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Paginação e ordenação pedidas pelo cliente ({@code page}, {@code size}, {@code sort=campo,asc}).
 *
 * <p>A ordenação só aceita chaves da lista de cada recurso, traduzidas para caminhos JPQL fixos: o
 * texto do cliente nunca entra na consulta.
 */
public record PageSpec(int page, int size, String sortKey, boolean ascending) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public static PageSpec of(Integer page, Integer size, String sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw new ApiException.BadRequest("pagina-invalida", "A página começa em 0.");
        }
        if (s < 1) {
            throw new ApiException.BadRequest("pagina-invalida", "O tamanho da página deve ser ao menos 1.");
        }
        String key = null;
        boolean asc = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            key = parts[0].trim();
            if (parts.length > 1) {
                String dir = parts[1].trim().toLowerCase(Locale.ROOT);
                if (!dir.equals("asc") && !dir.equals("desc")) {
                    throw new ApiException.BadRequest("ordenacao-invalida", "A direção da ordenação é asc ou desc.");
                }
                asc = dir.equals("asc");
            }
        }
        return new PageSpec(p, Math.min(s, MAX_SIZE), key, asc);
    }

    /**
     * Cláusula ORDER BY. {@code allowed} liga a chave pública aos caminhos JPQL; o desempate pelo id
     * deixa a paginação estável quando a chave se repete.
     */
    public String orderBy(Map<String, List<String>> allowed, String defaultKey, String idPath) {
        String key = sortKey == null ? defaultKey : sortKey;
        List<String> paths = allowed.get(key);
        if (paths == null) {
            throw new ApiException.BadRequest("ordenacao-invalida",
                    "Ordenação não suportada. Use uma destas: " + String.join(", ", allowed.keySet()) + ".");
        }
        String dir = ascending ? " asc" : " desc";
        return paths.stream().map(p -> p + dir).collect(Collectors.joining(", ")) + ", " + idPath + " asc";
    }
}
