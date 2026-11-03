package br.com.ziipsan.platform.console;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tela do tenant para propor, aprovar e aceitar mudança de parâmetro depois
 * da implantação (P1.6, sobre a V1_5 da P0.19): comparação entre versões,
 * vigência futura, alerta de risco e aprovação por pessoa diferente de quem
 * propôs.
 *
 * <p>Como {@code ServiceOrderService} (P1.31), cada método roda numa
 * transação própria com {@code app.current_tenant} local à transação: as
 * tabelas do tenant têm RLS forçada, e sem a GUC a policy esconderia as
 * linhas. A GUC morre com a transação, nunca vaza para outra conexão do
 * pool.
 *
 * <p>Limites do que esta classe confere, e o que fica com o módulo dono:
 * <ul>
 *   <li>chave sem definição no catálogo global é recusada — é por isso que
 *       a V9_7 registra as chaves so. e sst. da P1.31;</li>
 *   <li>limites legais do catálogo (MIN/MAX/FLOOR/ALLOWED_VALUES/LOCKED)
 *       e classe a travada são conferidos aqui, antes da gravação;</li>
 *   <li>regras semânticas por parâmetro (ex.: o "só amplia" de
 *       sst.required_controls_by_service_type, o "só reduz" por tipo de
 *       so.deadline_hours_by_type) vivem no módulo dono (P1.31) e não são
 *       duplicadas aqui — o console confere o catálogo, não a semântica
 *       de cada parâmetro;</li>
 *   <li>permissão fina de tenant: o catálogo da P0.13 não tem permissão
 *       para "mudar parâmetro", por isso a rota exige token de tenant
 *       autenticado e a separação exigida (proponente ≠ aprovador) é a
 *       trava aplicada aqui. Criar uma permissão nova sem rota que a use
 *       seria superfície sem prova (regra da P0.13); a permissão fina é
 *       pendência declarada, não inventada.</li>
 * </ul>
 */
public class TenantParameterService {

    /** Versão de parâmetro, como a tela a mostra (antes/depois, vigência, risco). */
    public record ParameterVersionView(UUID id, String definitionKey, String valueJson,
                                       LocalDate validFrom, LocalDate validTo, UUID proposedBy,
                                       UUID approvedBy, String status, String contentHash) {
    }

    /** Comparação entre a versão vigente aceita e a versão proposta. */
    public record ParameterChangeView(String definitionKey, String currentValueJson,
                                      String proposedValueJson, LocalDate proposedValidFrom,
                                      LocalDate proposedValidTo, boolean riskAlert,
                                      boolean approverDiffersFromProposer) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final Clock clock;

    public TenantParameterService(DataSource dataSource, String schema, UUID tenantId) {
        this(dataSource, schema, tenantId, Clock.systemUTC());
    }

    TenantParameterService(DataSource dataSource, String schema, UUID tenantId, Clock clock) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.clock = clock;
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                // Violação de unicidade ou do EXCLUDE de vigência (V1_5): a
                // tela mostra 409 "vigência sobreposta", não 500 genérico.
                for (Throwable t = e; t != null; t = t.getCause()) {
                    if (t instanceof SQLException sql
                            && ("23P01".equals(sql.getSQLState()) || "23505".equals(sql.getSQLState()))) {
                        throw conflict("A vigência se sobrepõe a outra versão do mesmo parâmetro.");
                    }
                }
                throw new IllegalStateException("Operação de parâmetro falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de parâmetro falhou: " + e.getMessage(), e);
        }
    }

    /** Definição do catálogo global, com os limites legais dela. */
    private record Definition(String key, String valueType, String paramClass, String factoryDefault,
                              List<String[]> limits) {
    }

    private Definition definition(Connection c, String key) throws SQLException {
        String factory = null;
        String valueType = null;
        String paramClass = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT definition_key, value_type, param_class, factory_default::text"
                        + " FROM public.parameter_definitions WHERE definition_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound(
                            "O parâmetro " + key + " não está no catálogo global: nenhum valor pode ser proposto para ele.");
                }
                valueType = rs.getString(2);
                paramClass = rs.getString(3);
                factory = rs.getString(4);
            }
        }
        List<String[]> limits = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT limit_kind, limit_value::text FROM public.parameter_legal_limits WHERE definition_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    limits.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
        }
        return new Definition(key, valueType, paramClass, factory, limits);
    }

    private static JsonNode parse(String valueJson, String key) {
        try {
            return Json.readTree(valueJson);
        } catch (Exception e) {
            throw validation("O valor do parâmetro " + key + " precisa ser um JSON válido.");
        }
    }

    /** Confere classe travada e limites legais do catálogo contra o valor proposto. */
    private void checkCatalogLimits(Definition def, JsonNode value) {
        if ("a".equals(def.paramClass()) && def.factoryDefault() != null
                && !parse(def.factoryDefault(), def.key()).equals(value)) {
            throw validation(
                    "O parâmetro " + def.key() + " é travado (classe a): o valor não pode sair do padrão.");
        }
        for (String[] limit : def.limits()) {
            String kind = limit[0];
            String limitValue = limit[1];
            switch (kind) {
                case "LOCKED" -> {
                    if (def.factoryDefault() != null && !parse(def.factoryDefault(), def.key()).equals(value)) {
                        throw validation(
                                "O parâmetro " + def.key() + " está travado pelo limite legal.");
                    }
                }
                case "MIN_VALUE", "FLOOR" -> {
                    if (value.isNumber() && limitValue != null
                            && value.asDouble() < Double.parseDouble(limitValue)) {
                        throw validation(
                                "O valor do parâmetro " + def.key() + " está abaixo do piso " + limitValue + ".");
                    }
                }
                case "MAX_VALUE" -> {
                    if (value.isNumber() && limitValue != null
                            && value.asDouble() > Double.parseDouble(limitValue)) {
                        throw validation(
                                "O valor do parâmetro " + def.key() + " está acima do teto " + limitValue + ".");
                    }
                }
                case "ALLOWED_VALUES" -> {
                    if (limitValue != null) {
                        JsonNode allowed = parse(limitValue, def.key());
                        boolean ok = false;
                        if (allowed.isArray()) {
                            for (JsonNode option : allowed) {
                                if (option.equals(value)
                                        || (value.isTextual() && option.isTextual()
                                                && option.asText().equals(value.asText()))
                                        || (value.isNumber() && option.isTextual()
                                                && option.asText().equals(value.asText()))) {
                                    ok = true;
                                    break;
                                }
                            }
                        }
                        if (!ok) {
                            throw validation(
                                    "O valor do parâmetro " + def.key() + " não está na lista permitida.");
                        }
                    }
                }
                default -> {
                    // MIN_COUNT, MAX_COUNT, REFERENCE, METHODOLOGY: sem valor escalar
                    // único para conferir aqui; valem para o módulo dono.
                }
            }
        }
    }

    /**
     * Propõe uma versão nova. A vigência pode ser futura (é o caso normal da
     * tela: a mudança vale a partir de uma data, nunca retroativa por aqui);
     * a sobreposição com outra versão da mesma chave é recusada pelo EXCLUDE
     * da V1_5 e vira 409 no tratador global.
     */
    public ParameterVersionView propose(String definitionKey, String valueJson,
                                        LocalDate validFrom, LocalDate validTo, UUID proposedBy) {
        if (definitionKey == null || definitionKey.isBlank() || validFrom == null || proposedBy == null) {
            throw validation("Chave, vigência inicial e proponente são obrigatórios.");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw validation("A vigência final não pode ser anterior à inicial.");
        }
        JsonNode value = parse(valueJson, definitionKey);
        String canonical = value.toString();
        return inTx(c -> {
            Definition def = definition(c, definitionKey);
            checkCatalogLimits(def, value);
            String hash = PlatformConsoleRules.contentHash(definitionKey, canonical, validFrom, validTo);
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("parameter_versions")
                            + " (tenant_id, definition_key, value, valid_from, valid_to, proposed_by, status, content_hash)"
                            + " VALUES (?, ?, ?::jsonb, ?, ?, ?, 'PROPOSED', ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, definitionKey);
                ps.setString(3, canonical);
                ps.setDate(4, Date.valueOf(validFrom));
                ps.setDate(5, validTo == null ? null : Date.valueOf(validTo));
                ps.setObject(6, proposedBy);
                ps.setString(7, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    id = rs.getObject(1, UUID.class);
                }
            }
            return new ParameterVersionView(id, definitionKey, canonical, validFrom, validTo,
                    proposedBy, null, "PROPOSED", hash);
        });
    }

    /** Aprova uma versão proposta. Quem propôs não aprova; a vigência precisa ser futura em relação à aprovação (CHECK da V1_5). */
    public ParameterVersionView approve(UUID versionId, UUID approvedBy) {
        if (versionId == null || approvedBy == null) {
            throw validation("Versão e aprovador são obrigatórios.");
        }
        return inTx(c -> {
            ParameterVersionView current = load(c, versionId);
            if (!"PROPOSED".equals(current.status())) {
                throw conflict("Só versão proposta pode ser aprovada.");
            }
            if (approvedBy.equals(current.proposedBy())) {
                throw new ApiException.Forbidden("Quem propôs não aprova a própria mudança de parâmetro.");
            }
            LocalDate approvedOn = LocalDate.now(clock);
            if (current.validFrom().isBefore(approvedOn)) {
                throw validation(
                        "A vigência da versão precisa ser futura em relação à aprovação.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("parameter_versions")
                            + " SET status = 'APPROVED', approved_by = ?, approved_at = NOW(), updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, approvedBy);
                ps.setObject(2, tenantId);
                ps.setObject(3, versionId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("A versão de parâmetro não existe para este tenant.");
                }
            }
            return new ParameterVersionView(current.id(), current.definitionKey(), current.valueJson(),
                    current.validFrom(), current.validTo(), current.proposedBy(), approvedBy, "APPROVED",
                    current.contentHash());
        });
    }

    /** Aceite formal da versão aprovada: quem aceita, cargo, hash conferido e alerta de risco reconhecido quando exigido. */
    public void accept(UUID versionId, UUID acceptedBy, String accepterName, String accepterRole,
                       boolean riskAlertAcknowledged) {
        if (versionId == null || acceptedBy == null || accepterName == null || accepterName.isBlank()
                || accepterRole == null || accepterRole.isBlank()) {
            throw validation("Versão, aceitante, nome e cargo são obrigatórios.");
        }
        inTx(c -> {
            ParameterVersionView current = load(c, versionId);
            if (!"APPROVED".equals(current.status())) {
                throw conflict("Só versão aprovada pode receber aceite formal.");
            }
            Definition def = definition(c, current.definitionKey());
            boolean alert = PlatformConsoleRules.requiresRiskAlert(
                    def.paramClass(), def.factoryDefault(), current.valueJson());
            if (alert && !riskAlertAcknowledged) {
                throw validation(
                        "O valor sai do padrão de fábrica (classe b): reconheça o alerta de risco no aceite.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("parameter_acceptances")
                            + " (tenant_id, version_id, accepted_by, accepter_name, accepter_role,"
                            + " content_hash, risk_alert_acknowledged)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, versionId);
                ps.setObject(3, acceptedBy);
                ps.setString(4, accepterName);
                ps.setString(5, accepterRole);
                ps.setString(6, current.contentHash());
                ps.setBoolean(7, riskAlertAcknowledged);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Comparação entre a versão vigente aceita e a versão proposta (a tela de mudança). */
    public ParameterChangeView changeView(String definitionKey, UUID proposedVersionId) {
        return inTx(c -> {
            Definition def = definition(c, definitionKey);
            // Sem versão vigente aceita, o valor em vigor é o padrão de fábrica
            // do catálogo (P0.19) — a tela compara contra ele, não contra vazio.
            String current = currentAcceptedValue(c, definitionKey);
            if (current == null) {
                current = def.factoryDefault();
            }
            ParameterVersionView proposed = load(c, proposedVersionId);
            if (!definitionKey.equals(proposed.definitionKey())) {
                throw validation("A versão proposta é de outro parâmetro.");
            }
            boolean alert = PlatformConsoleRules.requiresRiskAlert(
                    def.paramClass(), def.factoryDefault(), proposed.valueJson());
            return new ParameterChangeView(definitionKey, current, proposed.valueJson(),
                    proposed.validFrom(), proposed.validTo(), alert,
                    proposed.approvedBy() != null && !proposed.approvedBy().equals(proposed.proposedBy()));
        });
    }

    /** Valor vigente aceito hoje (versão aprovada + aceite com o mesmo hash + vigência cobrindo hoje), ou null. */
    public String currentAcceptedValue(String definitionKey) {
        return inTx(c -> currentAcceptedValue(c, definitionKey));
    }

    private String currentAcceptedValue(Connection c, String definitionKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT v.value::text FROM " + t("parameter_versions") + " v"
                        + " WHERE v.tenant_id = ? AND v.definition_key = ? AND v.status = 'APPROVED'"
                        + " AND v.valid_from <= CURRENT_DATE AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)"
                        + " AND EXISTS (SELECT 1 FROM " + t("parameter_acceptances") + " a"
                        + " WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id"
                        + " AND a.content_hash = v.content_hash)"
                        + " ORDER BY v.valid_from DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, definitionKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Gate de produção para este tenant: checklist 100% + perfil + nenhuma versão aprovada sem aceite. */
    public boolean productionGate(PlatformConsoleService console) {
        PlatformConsoleService.OnboardingPanel panel = console.onboardingPanel(tenantId);
        if (!PlatformConsoleRules.productionReady(panel.done(), panel.total(),
                console.profileExists(tenantId), approvedWithoutAcceptance())) {
            return false;
        }
        return true;
    }

    private long approvedWithoutAcceptance() {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT count(*) FROM " + t("parameter_versions") + " v"
                            + " WHERE v.tenant_id = ? AND v.status = 'APPROVED'"
                            + " AND NOT EXISTS (SELECT 1 FROM " + t("parameter_acceptances") + " a"
                            + " WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id"
                            + " AND a.content_hash = v.content_hash)")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    private ParameterVersionView load(Connection c, UUID versionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, definition_key, value::text, valid_from, valid_to, proposed_by, approved_by,"
                        + " status, content_hash FROM " + t("parameter_versions")
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, versionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A versão de parâmetro não existe para este tenant.");
                }
                Date from = rs.getDate(4);
                Date to = rs.getDate(5);
                return new ParameterVersionView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        from == null ? null : from.toLocalDate(), to == null ? null : to.toLocalDate(),
                        rs.getObject(6, UUID.class), rs.getObject(7, UUID.class),
                        rs.getString(8), rs.getString(9));
            }
        }
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }

    private static ApiException.Conflict conflict(String message) {
        return new ApiException.Conflict("state-conflict", message);
    }
}
