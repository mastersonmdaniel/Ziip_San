package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catálogos R2 do cadastro (P1.3, V3_12) sem entidade JPA própria: leitura e
 * escrita simples (código, nome, ativo + flags do catálogo) sobre as tabelas
 * nativas, sempre no tenant da requisição e só nas tabelas da allowlist de
 * {@link CatalogLookup}. O código nunca vira opcional: é obrigatório na criação
 * e único por tenant (o índice único do banco é a garantia final).
 *
 * <p>Flags por tabela (colunas extras, quando existirem): customer_types →
 * applies_to (PF, PJ, BOTH) e is_public_entity; property_profiles →
 * provisional_only; relation_roles → system_role (OWNER, USER, RESPONSIBLE,
 * obrigatório); document_exemption_reasons → requires_permission. Outras
 * tabelas não aceitam extras: um extra desconhecido é recusado com 400.
 */
@Service
@Transactional(readOnly = true)
public class RegistryCatalogService {

    /**
     * Tabelas com escrita por esta rota: só as de código+nome. block_faces (exige
     * block_id), street_names e neighborhoods (sem coluna code) e ibge_municipalities
     * (exige state) são só leitura aqui — a escrita delas pertence às telas próprias.
     */
    static final List<String> WRITABLE = List.of(
            "customer_types", "property_profiles", "relation_roles", "document_exemption_reasons",
            "customer_delete_reasons", "unit_link_end_reasons", "sewer_discharges", "consent_purposes");

    /** Tabela → colunas extras aceitas. */
    static final Map<String, List<String>> EXTRAS = Map.of(
            "customer_types", List.of("applies_to", "is_public_entity"),
            "property_profiles", List.of("provisional_only"),
            "relation_roles", List.of("system_role"),
            "document_exemption_reasons", List.of("requires_permission"));

    @PersistenceContext
    private EntityManager em;

    private final CatalogLookup lookup;

    public RegistryCatalogService(CatalogLookup lookup) {
        this.lookup = lookup;
    }

    public PageResult<Ref> list(String table, Boolean active, PageSpec spec) {
        List<CatalogLookup.CatalogRow> rows;
        try {
            rows = lookup.list(table, Boolean.TRUE.equals(active));
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("table", "Catálogo desconhecido: " + table + ".");
        }
        List<Ref> refs = rows.stream().map(r -> new Ref(r.id(), r.code(), r.name())).toList();
        int from = Math.min(spec.page() * spec.size(), refs.size());
        int to = Math.min(from + spec.size(), refs.size());
        return new PageResult<>(refs.subList(from, to), spec.page(), spec.size(), refs.size(),
                (int) ((refs.size() + spec.size() - 1) / spec.size()));
    }

    public Ref get(String table, UUID id) {
        CatalogLookup.CatalogRow row = lookup.find(table, id);
        if (row == null) {
            throw new ApiException.NotFound("Item do catálogo não encontrado.");
        }
        return new Ref(row.id(), row.code(), row.name());
    }

    @Transactional
    public Ref create(String table, CatalogInput in) {
        checkInput(table, in);
        if (lookup.byCode(table, in.code()) != null) {
            throw new ApiException.BadRequest("code", "Já existe item com este código no catálogo.");
        }
        UUID id = UUID.randomUUID();
        insert(table, id, in);
        return new Ref(id, in.code(), in.name());
    }

    @Transactional
    public Ref update(String table, UUID id, CatalogInput in) {
        checkInput(table, in);
        CatalogLookup.CatalogRow current = lookup.find(table, id);
        if (current == null) {
            throw new ApiException.NotFound("Item do catálogo não encontrado.");
        }
        CatalogLookup.CatalogRow clash = lookup.byCode(table, in.code());
        if (clash != null && !clash.id().equals(id)) {
            throw new ApiException.BadRequest("code", "Já existe item com este código no catálogo.");
        }
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("code", in.code());
        columns.put("name", in.name());
        columns.put("active", in.active() != null && in.active());
        columns.putAll(filteredExtras(table, in.extra()));
        String set = String.join(", ", columns.keySet().stream().map(c -> c + " = :" + c).toList());
        var query = em.createNativeQuery("UPDATE " + table + " SET " + set + " WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", id);
        columns.forEach(query::setParameter);
        query.executeUpdate();
        return new Ref(id, in.code(), in.name());
    }

    @Transactional
    public void deactivate(String table, UUID id) {
        if (!WRITABLE.contains(table)) {
            throw new ApiException.BadRequest("table", "Catálogo só leitura por esta rota: " + table + ".");
        }
        if (lookup.find(table, id) == null) {
            throw new ApiException.NotFound("Item do catálogo não encontrado.");
        }
        em.createNativeQuery("UPDATE " + table + " SET active = false WHERE tenant_id = :t AND id = :id")
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("id", id)
                .executeUpdate();
    }

    private void checkInput(String table, CatalogInput in) {
        if (!WRITABLE.contains(table)) {
            throw new ApiException.BadRequest("table", "Catálogo só leitura por esta rota: " + table + ".");
        }
        if (in.code() == null || in.code().isBlank()) {
            throw new ApiException.BadRequest("code", "Informe o código do item do catálogo.");
        }
        if (in.name() == null || in.name().isBlank()) {
            throw new ApiException.BadRequest("name", "Informe o nome do item do catálogo.");
        }
        Map<String, Object> extras = filteredExtras(table, in.extra());
        if ("relation_roles".equals(table) && !extras.containsKey("system_role")) {
            throw new ApiException.BadRequest("system_role", "Informe o papel do sistema (OWNER, USER ou RESPONSIBLE).");
        }
    }

    private Map<String, Object> filteredExtras(String table, Map<String, Object> extra) {
        List<String> allowed = EXTRAS.getOrDefault(table, List.of());
        Map<String, Object> out = new LinkedHashMap<>();
        if (extra != null) {
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                if (!allowed.contains(entry.getKey())) {
                    throw new ApiException.BadRequest(entry.getKey(), "Campo extra desconhecido para este catálogo.");
                }
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private void insert(String table, UUID id, CatalogInput in) {
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("id", id);
        columns.put("tenant_id", RegistryContext.tenantId());
        columns.put("code", in.code());
        columns.put("name", in.name());
        columns.put("active", in.active() == null || in.active());
        columns.putAll(filteredExtras(table, in.extra()));
        String names = String.join(", ", columns.keySet());
        String params = String.join(", ", columns.keySet().stream().map(c -> ":" + c).toList());
        // tenant_id como parâmetro tem nome com underscore: o placeholder é :tenant_id.
        var query = em.createNativeQuery("INSERT INTO " + table + " (" + names + ") VALUES (" + params + ")");
        columns.forEach(query::setParameter);
        query.executeUpdate();
    }

    public record CatalogInput(String code, String name, Boolean active, Map<String, Object> extra) {
    }
}
