package br.com.ziipsan.commercial.registry.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura de catálogos do cadastro sem entidade JPA própria: os catálogos R2
 * (V3_12) são tabelas simples (code, name, active, mais flags) e o serviço só
 * precisa resolver id → (code, name, active) e checar existência. A consulta é
 * nativa, no schema do tenant da requisição (o mesmo padrão do
 * {@code nextval('registry_matricula_seq')} de ConsumerUnitService).
 *
 * <p>Tabelas permitidas: só as da allowlist. Qualquer outra tabela dá
 * IllegalArgumentException — o nome da tabela nunca vem do corpo da requisição.
 */
@Component
public class CatalogLookup {

    private static final Set<String> TABLES = Set.of(
            "customer_types", "property_profiles", "relation_roles", "document_exemption_reasons",
            "customer_delete_reasons", "unit_link_end_reasons", "sewer_discharges", "block_faces",
            "street_names", "neighborhoods", "consent_purposes", "ibge_municipalities");

    @PersistenceContext
    private EntityManager em;

    public record CatalogRow(UUID id, String code, String name, boolean active, Map<String, Object> extra) {
    }

    /** Linha do catálogo, ou nulo (inexistente ou de outro tenant, que a RLS já esconde). */
    public CatalogRow find(String table, UUID id) {
        if (id == null || !TABLES.contains(table)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, code, name, active FROM " + table + " WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        return new CatalogRow((UUID) row[0], (String) row[1], (String) row[2], (Boolean) row[3], Map.of());
    }

    /** Todas as linhas ativas (ou todas, com activeOnly=false), ordenadas por código. */
    public List<CatalogRow> list(String table, boolean activeOnly) {
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("Catálogo desconhecido: " + table);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, code, name, active FROM " + table + " WHERE tenant_id = :t"
                                + (activeOnly ? " AND active" : "") + " ORDER BY code")
                .setParameter("t", RegistryContext.tenantId())
                .getResultList();
        return rows.stream()
                .map(row -> new CatalogRow((UUID) row[0], (String) row[1], (String) row[2], (Boolean) row[3], Map.of()))
                .toList();
    }

    /** Ref da API (id, code, name) ou nulo. Nome nulo quando a linha não existe. */
    public Ref ref(String table, UUID id) {
        CatalogRow row = find(table, id);
        return row == null ? (id == null ? null : new Ref(id, null, null))
                : new Ref(row.id(), row.code(), row.name());
    }

    /** Coluna booleana extra de uma linha (ex.: is_public_entity, provisional_only). */
    public Boolean flag(String table, UUID id, String column) {
        if (id == null || !TABLES.contains(table) || !column.matches("[a-z_]+")) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                        "SELECT " + column + " FROM " + table + " WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", id)
                .getResultList();
        return rows.isEmpty() ? null : (Boolean) rows.get(0);
    }

    /** Coluna de texto extra de uma linha (ex.: applies_to de customer_types). */
    public String text(String table, UUID id, String column) {
        if (id == null || !TABLES.contains(table) || !column.matches("[a-z_]+")) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                        "SELECT " + column + " FROM " + table + " WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", id)
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    /** Linha de relation_roles pelo system_role (OWNER, USER, RESPONSIBLE), ou nulo. */
    public CatalogRow roleBySystemRole(String systemRole) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, code, name, active FROM relation_roles WHERE tenant_id = :t AND system_role = :r")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("r", systemRole)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        return new CatalogRow((UUID) row[0], (String) row[1], (String) row[2], (Boolean) row[3], Map.of());
    }

    /** Linha de catálogo pelo código, ou nulo. */
    public CatalogRow byCode(String table, String code) {
        if (code == null || !TABLES.contains(table)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, code, name, active FROM " + table + " WHERE tenant_id = :t AND code = :c")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("c", code)
                .getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        return new CatalogRow((UUID) row[0], (String) row[1], (String) row[2], (Boolean) row[3], Map.of());
    }
}
