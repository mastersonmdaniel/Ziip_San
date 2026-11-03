package br.com.ziipsan.commercial.registry.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Function;
import br.com.ziipsan.core.api.ApiException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consulta de lista com filtros opcionais, montada com cláusulas fixas e parâmetros nomeados.
 *
 * <p>Existe porque {@code (:p is null or campo = :p)} no Spring Data esbarra no PostgreSQL ("could not
 * determine data type of parameter") quando o parâmetro chega nulo: aqui o filtro ausente simplesmente
 * não entra na consulta. Nada do cliente vira texto de JPQL; só valores de parâmetro.
 */
public final class JpqlQuery {

    private final String alias;
    private final String from;
    private final List<String> where = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    /**
     * @param alias apelido da entidade selecionada (ex.: {@code l})
     * @param from  entidade e junções (ex.: {@code Locality l} ou {@code ConsumerUnit u left join Block b on b.id = u.blockId})
     */
    public JpqlQuery(String alias, String from) {
        this.alias = alias;
        this.from = from;
    }

    public JpqlQuery where(String clause) {
        where.add(clause);
        return this;
    }

    public JpqlQuery where(String clause, String param, Object value) {
        where.add(clause);
        params.put(param, value);
        return this;
    }

    /** Aplica o filtro só quando o valor veio. */
    public JpqlQuery whereIfPresent(String clause, String param, Object value) {
        if (value != null) {
            where(clause, param, value);
        }
        return this;
    }

    public JpqlQuery param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public <T> PageResult<T> page(EntityManager em, Class<T> type, PageSpec spec, String orderBy) {
        // P0.16: page*size em int estourava silenciosamente acima de page ≈ 21.474.836
        // (size=100) e o setFirstResult recebia um valor enrolado. O teto é aqui, e
        // não só em PageSpec.of, para cobrir construções diretas de PageSpec.
        // Página acima do teto: 400 (ApiException.BadRequest), nunca 500.
        long offset = (long) spec.page() * spec.size();
        if (spec.page() < 0 || spec.size() < 1 || offset > Integer.MAX_VALUE) {
            throw new ApiException.BadRequest("invalid-page",
                    "A página pedida excede o limite suportado.");
        }
        String whereSql = where.isEmpty() ? "" : " where " + String.join(" and ", where);
        TypedQuery<T> query = em.createQuery("select " + alias + " from " + from + whereSql + " order by " + orderBy, type);
        TypedQuery<Long> count = em.createQuery("select count(" + alias + ") from " + from + whereSql, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        query.setFirstResult((int) offset);
        query.setMaxResults(spec.size());
        return PageResult.of(query.getResultList(), spec, count.getSingleResult());
    }

    /**
     * Paginação keyset (cursor) sobre uma ordenação fixa {@code sortPath asc, idPath asc}.
     *
     * <p>Por que keyset nas listas grandes (P0.22): a paginação por offset reexecuta o
     * COUNT total e salta N linhas a cada página — custo que cresce com a página. O
     * keyset pede "as próximas {@code size} linhas depois deste cursor" com UMA única
     * consulta, sem COUNT, e o resultado é estável mesmo que linhas entrem no meio da
     * navegação (o offset deslocaria tudo). O cursor é o par (valor da ordenação, id)
     * da última linha da página anterior; a primeira página usa {@code cursor == null}.
     *
     * <p>O teto de página da P0.16 vale aqui também: {@code size} acima de
     * {@link PageSpec#MAX_SIZE} é cortado para o teto, nunca rejeitado nem estourado.
     * Busca-se {@code limit + 1} linhas para saber se há próxima página sem COUNT.
     * Só ordenação crescente é suportada: o chamador que precisa de outra ordenação
     * usa a paginação por offset ({@link #page}) e recebe 400 se tentar o keyset.
     */
    public <T> KeysetPage<T> pageAfter(EntityManager em, Class<T> type, int size, String sortPath,
                                       String idPath, KeysetCursor cursor, Function<T, KeysetCursor> extractor) {
        if (size < 1) {
            throw new ApiException.BadRequest("invalid-page", "O tamanho da página deve ser ao menos 1.");
        }
        int limit = Math.min(size, PageSpec.MAX_SIZE);
        List<String> clauses = new ArrayList<>(where);
        if (cursor != null) {
            clauses.add("(" + sortPath + " > :cursorSort or (" + sortPath + " = :cursorSort and "
                    + idPath + " > :cursorId))");
        }
        String whereSql = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
        TypedQuery<T> query = em.createQuery("select " + alias + " from " + from + whereSql
                + " order by " + sortPath + " asc, " + idPath + " asc", type);
        params.forEach(query::setParameter);
        if (cursor != null) {
            query.setParameter("cursorSort", cursor.sortValue());
            query.setParameter("cursorId", cursor.id());
        }
        query.setMaxResults(limit + 1);
        List<T> rows = query.getResultList();
        boolean hasNext = rows.size() > limit;
        List<T> content = hasNext ? rows.subList(0, limit) : rows;
        KeysetCursor next = null;
        if (hasNext && !content.isEmpty()) {
            next = extractor.apply(content.get(content.size() - 1));
        }
        return new KeysetPage<>(List.copyOf(content), next, hasNext);
    }

    /** Existe linha para a consulta de contagem? {@code namesAndValues} alterna nome e valor de parâmetro (não nulos). */
    public static boolean exists(EntityManager em, String countJpql, Object... namesAndValues) {
        TypedQuery<Long> query = em.createQuery(countJpql, Long.class);
        for (int i = 0; i < namesAndValues.length; i += 2) {
            query.setParameter((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return query.getSingleResult() > 0;
    }

    /**
     * Sufixo do LIKE com os padrões abaixo. O escape é {@code !}, e não a barra invertida: no HQL a
     * barra inicia sequência de escape dentro do literal, e {@code '\'} não fecharia a string.
     */
    public static final String LIKE_ESCAPE = " escape '!'";

    /** Padrão de LIKE "contém", em minúsculas, com os curingas do usuário escapados (usar com {@link #LIKE_ESCAPE}). */
    public static String contains(String text) {
        return "%" + escapeLike(text) + "%";
    }

    public static String startsWith(String text) {
        return escapeLike(text) + "%";
    }

    /**
     * Padrão de LIKE "contém" com dobra de acento (P0.22): o texto digitado perde os
     * acentos antes de virar padrão, para casar com a consulta que usa
     * {@code immutable_unaccent(lower(nome))} e o índice trigram da V3_9. NÃO substitui
     * {@link #contains}: as listas de catálogo ainda consultam {@code lower(nome)} sem
     * dobra, e dobrar só o texto digitado ali quebraria a busca acentuada delas
     * ("joao" deixaria de casar "João").
     */
    public static String containsFolded(String text) {
        return "%" + escapeLikeFolded(text) + "%";
    }

    /** Como {@link #containsFolded}, para o modo startsWith. */
    public static String startsWithFolded(String text) {
        return escapeLikeFolded(text) + "%";
    }

    /** Remove acentos (NFD + tira marcas combinantes) e passa para minúsculas. */
    public static String foldAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String text) {
        return text.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String escapeLikeFolded(String text) {
        return foldAccents(text.trim())
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Texto em branco vira nulo (filtro ausente). */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
