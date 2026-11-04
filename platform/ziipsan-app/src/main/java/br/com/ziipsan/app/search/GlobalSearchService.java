package br.com.ziipsan.app.search;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitService;
import br.com.ziipsan.commercial.registry.application.CustomerDtos;
import br.com.ziipsan.commercial.registry.application.CustomerService;
import br.com.ziipsan.commercial.registry.rules.Inscription;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.commercial.registry.service.PersonalDataAccessService;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Busca global (P1.33, 22c PLT-15, ADR-0008).
 *
 * <p>POST com o texto no corpo, nunca na URL: o texto pode ser um CPF/CNPJ, e documento
 * nunca vai na URL (o mesmo motivo do POST /customers/search da P0.16).
 *
 * <p>Permissão, por seção — o portão {@code search:read} do controller NÃO basta: cada
 * seção exige ainda a permissão do próprio recurso, e a seção sem ela volta em
 * {@code deniedTypes}, sem dado (negação por padrão, sem oráculo de existência):
 * cliente e unidade exigem {@code registry:read}; OS exige {@code service-order:read};
 * conta exige {@code billing:read}; documento exige {@code document:read}.
 * Protocolo (P1.36) volta sempre em {@code pendingTypes}, sem resultado fingido.
 * Nota da integração P1-C: a tabela da P1.36 JÁ existe na árvore integrada
 * (V8_1), mas a seção continua pendente por decisão, não por falta de tabela:
 * não há superfície de detalhe de protocolo para onde um hit apontaria e a
 * semântica da busca (por número? por requerente? por assunto?) não foi
 * desenhada — ligar um LIKE ingênuo na integração inventaria comportamento.
 * A seção de protocolo é frente de acompanhamento, não item de integração.
 * Escopo por área (P3.30) ainda não existe: sem {@code org_units},
 * a busca vale para o tenant inteiro (a RLS por tenant continua valendo); quando a
 * P3.30 chegar, o filtro obrigatório de repositório passa a valer aqui também.
 *
 * <p>Documento (o ponto do ADR-0008): um texto que é um CPF/CNPJ completo e válido
 * SÓ casa por igualdade com o índice cego — nunca por prefixo, máscara ou nome — e
 * passa pelo gate da P0.11 exatamente uma vez por busca global
 * ({@link PersonalDataAccessService#authorizeDocumentSearch}: sem
 * {@code registry:search-document}, 403 com DENIED registrado; cota por usuário por
 * hora excedida, 429 com QUOTA_EXCEEDED registrado; concluída, um único registro
 * FOUND/NOT_FOUND). Por isso o caminho por documento NÃO reutiliza
 * {@code CustomerService.search} e {@code ConsumerUnitService.search}: cada um deles
 * autorizaria e registraria de novo, e uma busca global consumiria duas cotas.
 * Texto que NÃO é documento válido cai no caminho por nome/matrícula, sem gate e
 * sem registro de busca por documento — e um documento parcial (prefixo, dígitos
 * sem DV válido) nunca casa com documento: ele é tratado como texto, e nenhuma
 * seção consulta {@code document_hash} ou {@code document_masked} com LIKE.
 *
 * <p>Seções sem serviço de aplicação no contexto (OS, conta, documento) são JDBC
 * direto no schema do tenant, no padrão do {@code JdbcDocumentRepository} da P1.20:
 * transação explícita com {@code SET LOCAL search_path} + {@code SET LOCAL
 * app.current_tenant}, schema validado por regex antes de interpolar, filtro
 * explícito por {@code tenant_id} além da RLS. Só documentos com
 * {@code scan_status = CLEAN} aparecem: em quarentena, infectado ou não varrido
 * nunca é servido (a mesma regra do download da P1.20).
 */
@Service
public class GlobalSearchService {

    /** Um resultado por seção, no máximo: a paleta é atalho, não lista. */
    public static final int PER_TYPE_LIMIT = 5;

    public enum SearchType {
        CUSTOMER, CONSUMER_UNIT, SERVICE_ORDER, BILL, DOCUMENT, PROTOCOL
    }

    /** Seções implementadas (PROTOCOL é pendente da P1.36 e nunca entra aqui). */
    public static final Set<SearchType> IMPLEMENTED =
            EnumSet.of(SearchType.CUSTOMER, SearchType.CONSUMER_UNIT, SearchType.SERVICE_ORDER,
                    SearchType.BILL, SearchType.DOCUMENT);

    private static final Map<SearchType, String> TYPE_PERMISSION = Map.of(
            SearchType.CUSTOMER, "registry:read",
            SearchType.CONSUMER_UNIT, "registry:read",
            SearchType.SERVICE_ORDER, "service-order:read",
            SearchType.BILL, "billing:read",
            SearchType.DOCUMENT, "document:read");

    private static final Pattern SCHEMA = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final Pattern MATRICULA = Pattern.compile("\\d{1,18}(-\\d)?");
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public record GlobalSearchRequest(
            @NotBlank(message = "Informe o texto da busca.") @Size(max = 200, message = "A busca aceita no máximo 200 caracteres.") String q,
            List<String> types) {
    }

    public record Hit(String type, UUID id, String title, String subtitle, String href) {
    }

    public record GlobalSearchResult(String q, List<Hit> results, List<String> deniedTypes,
                                     List<String> pendingTypes, boolean documentSearch) {
    }

    private final CustomerService customers;
    private final ConsumerUnitService units;
    private final CustomerDocumentService documents;
    private final PersonalDataAccessService accessLog;
    private final DataSource dataSource;

    public GlobalSearchService(CustomerService customers, ConsumerUnitService units,
                               CustomerDocumentService documents, PersonalDataAccessService accessLog,
                               DataSource dataSource) {
        this.customers = customers;
        this.units = units;
        this.documents = documents;
        this.accessLog = accessLog;
        this.dataSource = dataSource;
    }

    /**
     * Busca global. {@code types} vazio ou nulo = todas as seções implementadas; tipo
     * desconhecido dá 400 (o texto do cliente nunca vira nome de seção na consulta).
     */
    @Transactional(readOnly = true)
    public GlobalSearchResult search(GlobalSearchRequest request) {
        String q = request.q() == null ? "" : request.q().trim();
        if (q.isEmpty()) {
            throw ApiException.Validation.of(
                    new ApiException.FieldError("q", "REQUIRED", "Informe o texto da busca."));
        }
        Set<SearchType> wanted = wantedTypes(request.types());
        UUID tenantId = requireTenant();

        List<Hit> results = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        if (wanted.contains(SearchType.PROTOCOL)) {
            // Integração P1-C: tabela da P1.36 existe, seção segue pendente por
            // decisão (ver javadoc da classe) — sem resultado fingido.
            pending.add(SearchType.PROTOCOL.name());
        }

        // Caminho por documento: um único gate + um único registro para a busca global.
        var documentHash = documents.searchIndex(q);
        boolean documentSearch = documentHash.isPresent();
        if (documentSearch) {
            accessLog.authorizeDocumentSearch(documentHash.get());
            boolean found = false;
            if (wanted.contains(SearchType.CUSTOMER) && hasPermission("registry:read")) {
                List<Hit> hits = customersByDocument(tenantId, documentHash.get());
                found |= !hits.isEmpty();
                results.addAll(hits);
            } else if (wanted.contains(SearchType.CUSTOMER)) {
                denied.add(SearchType.CUSTOMER.name());
            }
            if (wanted.contains(SearchType.CONSUMER_UNIT) && hasPermission("registry:read")) {
                List<Hit> hits = unitsByDocument(tenantId, documentHash.get());
                found |= !hits.isEmpty();
                results.addAll(hits);
            } else if (wanted.contains(SearchType.CONSUMER_UNIT)) {
                denied.add(SearchType.CONSUMER_UNIT.name());
            }
            // As demais seções não casam por documento e não são "negadas": OS, conta e
            // documento não têm índice cego de titular, então uma busca por documento
            // simplesmente não as abrange (o frontend explica isso com documentSearch).
            // Casar por elas exigiria outro gate por documento, que não existe.
            accessLog.recordDocumentSearch(documentHash.get(), found);
            return new GlobalSearchResult(q, results, denied, pending, true);
        }

        // Caminho por texto (nome, matrícula, tipo de OS, nome de arquivo): sem gate de documento.
        if (wanted.contains(SearchType.CUSTOMER)) {
            if (hasPermission("registry:read")) {
                var page = customers.search(new CustomerDtos.CustomerSearch(
                        q, "contains", null, null, null, false, 0, PER_TYPE_LIMIT, null));
                for (CustomerDtos.CustomerSummary s : page.content()) {
                    results.add(new Hit(SearchType.CUSTOMER.name(), s.id(), s.name(),
                            s.documentMasked() == null ? "Cliente" : "Cliente · " + s.documentMasked(),
                            "/cadastro/clientes/" + s.id()));
                }
            } else {
                denied.add(SearchType.CUSTOMER.name());
            }
        }
        if (wanted.contains(SearchType.CONSUMER_UNIT)) {
            if (hasPermission("registry:read")) {
                // Prefixo pontuado só de dígitos (ex.: "529.982") NÃO é chamado no serviço:
                // o parse de inscrição lançaria BadRequest dentro desta transação e, mesmo
                // capturado, o Spring a marcaria como rollback-only (500 no commit). Sem
                // casa em unidade, ele é só texto — o mesmo resultado, sem exceção.
                if (!isInvalidInscriptionPrefix(q)) {
                    var page = units.search(new ConsumerUnitDtos.UnitSearch(
                            q, null, null, null, null, null, null, null, null, null, null, null,
                            false, 0, PER_TYPE_LIMIT, null));
                    for (ConsumerUnitDtos.UnitSummary s : page.content()) {
                        results.add(new Hit(SearchType.CONSUMER_UNIT.name(), s.id(),
                                "Matrícula " + s.registration()
                                        + (s.registrationDv() == null ? "" : "-" + s.registrationDv()),
                                s.userName() == null ? "Unidade consumidora" : "Unidade consumidora · " + s.userName(),
                                "/cadastro/imoveis/" + s.id()));
                    }
                }
            } else {
                denied.add(SearchType.CONSUMER_UNIT.name());
            }
        }
        if (wanted.contains(SearchType.SERVICE_ORDER)) {
            if (hasPermission("service-order:read")) {
                results.addAll(serviceOrdersByText(tenantId, q));
            } else {
                denied.add(SearchType.SERVICE_ORDER.name());
            }
        }
        if (wanted.contains(SearchType.BILL)) {
            if (hasPermission("billing:read")) {
                results.addAll(billsByText(tenantId, q));
            } else {
                denied.add(SearchType.BILL.name());
            }
        }
        if (wanted.contains(SearchType.DOCUMENT)) {
            if (hasPermission("document:read")) {
                results.addAll(documentsByText(tenantId, q));
            } else {
                denied.add(SearchType.DOCUMENT.name());
            }
        }
        return new GlobalSearchResult(q, results, denied, pending, false);
    }

    private static Set<SearchType> wantedTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return EnumSet.allOf(SearchType.class);
        }
        Set<SearchType> wanted = EnumSet.noneOf(SearchType.class);
        for (String raw : types) {
            if (raw == null) {
                throw ApiException.Validation.of(new ApiException.FieldError("types", "INVALID",
                        "Tipo de busca desconhecido: null."));
            }
            try {
                wanted.add(SearchType.valueOf(raw));
            } catch (IllegalArgumentException e) {
                throw ApiException.Validation.of(new ApiException.FieldError("types", "INVALID",
                        "Tipo de busca desconhecido: " + raw + "."));
            }
        }
        return wanted;
    }

    static boolean hasPermission(String permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> permission.equals(a.getAuthority()));
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }

    // ------------------------------------------------------------------ documento (JDBC)

    private List<Hit> customersByDocument(UUID tenantId, String documentHash) {
        return inTenantTransaction(tenantId, connection -> {
            List<Hit> hits = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, name, document_masked FROM customers"
                            + " WHERE tenant_id = ? AND document_hash = ? AND deleted_at IS NULL"
                            + " ORDER BY name LIMIT " + PER_TYPE_LIMIT)) {
                ps.setObject(1, tenantId);
                ps.setString(2, documentHash);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = (UUID) rs.getObject(1);
                        String name = rs.getString(2);
                        String masked = rs.getString(3);
                        hits.add(new Hit(SearchType.CUSTOMER.name(), id, name,
                                masked == null ? "Cliente" : "Cliente · " + masked,
                                "/cadastro/clientes/" + id));
                    }
                }
            }
            return hits;
        });
    }

    private List<Hit> unitsByDocument(UUID tenantId, String documentHash) {
        return inTenantTransaction(tenantId, connection -> {
            List<Hit> hits = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT u.id, u.registration_number, u.check_digit, c.name FROM consumer_units u"
                            + " JOIN unit_customers uc ON uc.unit_id = u.id AND uc.end_date IS NULL"
                            + " JOIN customers c ON c.id = uc.customer_id"
                            + " WHERE u.tenant_id = ? AND c.tenant_id = ? AND c.document_hash = ?"
                            + " AND u.deleted_at IS NULL AND c.deleted_at IS NULL"
                            + " ORDER BY u.registration_number LIMIT " + PER_TYPE_LIMIT)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, tenantId);
                ps.setString(3, documentHash);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = (UUID) rs.getObject(1);
                        String matricula = rs.getString(2);
                        String dv = rs.getString(3);
                        String holder = rs.getString(4);
                        hits.add(new Hit(SearchType.CONSUMER_UNIT.name(), id,
                                "Matrícula " + matricula + (dv == null ? "" : "-" + dv),
                                holder == null ? "Unidade consumidora" : "Unidade consumidora · " + holder,
                                "/cadastro/imoveis/" + id));
                    }
                }
            }
            return hits;
        });
    }

    // ------------------------------------------------------------------ texto (JDBC)

    private List<Hit> serviceOrdersByText(UUID tenantId, String q) {
        return inTenantTransaction(tenantId, connection -> {
            List<Hit> hits = new ArrayList<>();
            boolean uuid = UUID_TEXT.matcher(q).matches();
            String sql = "SELECT so.id, so.status, st.code, st.name FROM service_orders so"
                    + " LEFT JOIN service_types st ON st.id = so.service_type_id AND st.tenant_id = so.tenant_id"
                    + " WHERE so.tenant_id = ? AND ("
                    + (uuid ? "so.id = ? OR " : "")
                    + "immutable_unaccent(lower(st.code)) LIKE ? ESCAPE '\\'"
                    + " OR immutable_unaccent(lower(st.name)) LIKE ? ESCAPE '\\')"
                    + " ORDER BY so.created_at DESC LIMIT " + PER_TYPE_LIMIT;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, tenantId);
                if (uuid) {
                    ps.setObject(i++, UUID.fromString(q));
                }
                String pattern = likePattern(q);
                ps.setString(i++, pattern);
                ps.setString(i, pattern);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = (UUID) rs.getObject(1);
                        String status = rs.getString(2);
                        String code = rs.getString(3);
                        String typeName = rs.getString(4);
                        hits.add(new Hit(SearchType.SERVICE_ORDER.name(), id,
                                typeName == null ? "Ordem de serviço" : typeName,
                                "OS · " + status + (code == null ? "" : " · " + code) + " · " + id,
                                null));
                    }
                }
            }
            return hits;
        });
    }

    private List<Hit> billsByText(UUID tenantId, String q) {
        // Conta não tem texto livre próprio: casa por id (UUID) ou pela matrícula da unidade.
        if (UUID_TEXT.matcher(q).matches()) {
            return inTenantTransaction(tenantId, connection -> {
                List<Hit> hits = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT b.id, b.reference_month, b.status, b.due_date, u.registration_number FROM bills b"
                                + " JOIN consumer_units u ON u.id = b.unit_id"
                                + " WHERE b.tenant_id = ? AND b.id = ? LIMIT " + PER_TYPE_LIMIT)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, UUID.fromString(q));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            hits.add(billHit(rs));
                        }
                    }
                }
                return hits;
            });
        }
        if (!MATRICULA.matcher(q).matches()) {
            return List.of();
        }
        String[] parts = q.split("-");
        return inTenantTransaction(tenantId, connection -> {
            List<Hit> hits = new ArrayList<>();
            String sql = "SELECT b.id, b.reference_month, b.status, b.due_date, u.registration_number FROM bills b"
                    + " JOIN consumer_units u ON u.id = b.unit_id"
                    + " WHERE b.tenant_id = ? AND u.registration_number = ?"
                    + (parts.length > 1 ? " AND u.check_digit = ?" : "")
                    + " ORDER BY b.reference_month DESC LIMIT " + PER_TYPE_LIMIT;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setString(2, parts[0]);
                if (parts.length > 1) {
                    ps.setString(3, parts[1]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        hits.add(billHit(rs));
                    }
                }
            }
            return hits;
        });
    }

    private static Hit billHit(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject(1);
        java.sql.Date reference = rs.getDate(2);
        String status = rs.getString(3);
        java.sql.Date due = rs.getDate(4);
        String matricula = rs.getString(5);
        return new Hit(SearchType.BILL.name(), id,
                "Conta " + (reference == null ? "" : reference.toLocalDate().toString().substring(0, 7))
                        + " · matrícula " + matricula,
                "Conta · " + status + (due == null ? "" : " · vencimento " + due.toLocalDate()),
                null);
    }

    private List<Hit> documentsByText(UUID tenantId, String q) {
        return inTenantTransaction(tenantId, connection -> {
            List<Hit> hits = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT d.id, d.original_filename, dt.code FROM documents d"
                            + " JOIN document_types dt ON dt.id = d.document_type_id"
                            + " WHERE d.tenant_id = ? AND d.scan_status = 'CLEAN'"
                            + " AND immutable_unaccent(lower(d.original_filename)) LIKE ? ESCAPE '\\'"
                            + " ORDER BY d.created_at DESC LIMIT " + PER_TYPE_LIMIT)) {
                ps.setObject(1, tenantId);
                ps.setString(2, likePattern(q));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = (UUID) rs.getObject(1);
                        String filename = rs.getString(2);
                        String typeCode = rs.getString(3);
                        hits.add(new Hit(SearchType.DOCUMENT.name(), id, filename,
                                "Documento · " + typeCode + " · liberado",
                                null));
                    }
                }
            }
            return hits;
        });
    }

    // ------------------------------------------------------------------ infra JDBC

    /** Dobra de acento + minúsculas (o mesmo `immutable_unaccent(lower(...))` do cadastro). */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Padrão LIKE com escape: o texto do usuário nunca vira curinga. */
    static String likePattern(String q) {
        String folded = fold(q);
        String escaped = folded.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Prefixo pontuado só de dígitos cujo parse de inscrição falha (ex.: "529.982"). */
    private static boolean isInvalidInscriptionPrefix(String q) {
        if (q == null || !q.contains(".") || !q.matches("[0-9.]+")) {
            return false;
        }
        try {
            Inscription.parse(q);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T inTenantTransaction(UUID tenantId, Work<T> work) {
        String schema = TenantContextHolder.getTenant();
        if (schema == null || !SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("schema do tenant inválido no contexto");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL search_path TO " + schema + ", public");
                }
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    setTenant.setString(1, tenantId.toString());
                    setTenant.executeQuery();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // O erro original é o que importa; o rollback falho não o substitui.
                }
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de acesso ao banco da busca global", e);
        }
    }

    /** Apenas para os testes de unidade do padrão LIKE. */
    static String likePatternForTest(String q) {
        return likePattern(q);
    }
}
