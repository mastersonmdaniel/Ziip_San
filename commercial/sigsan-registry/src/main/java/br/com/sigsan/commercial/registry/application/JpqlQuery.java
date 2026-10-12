package br.com.sigsan.commercial.registry.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        String whereSql = where.isEmpty() ? "" : " where " + String.join(" and ", where);
        TypedQuery<T> query = em.createQuery("select " + alias + " from " + from + whereSql + " order by " + orderBy, type);
        TypedQuery<Long> count = em.createQuery("select count(" + alias + ") from " + from + whereSql, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        query.setFirstResult(spec.page() * spec.size());
        query.setMaxResults(spec.size());
        return PageResult.of(query.getResultList(), spec, count.getSingleResult());
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

    private static String escapeLike(String text) {
        return text.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Texto em branco vira nulo (filtro ausente). */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
