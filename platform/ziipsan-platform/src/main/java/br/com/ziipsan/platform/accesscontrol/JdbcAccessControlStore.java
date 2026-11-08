package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Loja JDBC da ED-06 (tabelas do SQL pendente desta entrega, schema do
 * tenant, RLS pela GUC {@code app.current_tenant} como nos demais serviços
 * de platform). Conjuntos de permissões/perfis são gravados em JSONB SEMPRE
 * via {@link Json} do core. A política de senha e o SSO NÃO são tabelas
 * novas: são parâmetros do tenant (catálogo da P0.19, valores na V1_5),
 * lidos pela porta {@link TenantParameterReader} — a loja devolve o padrão
 * de fábrica alinhado ao NIST quando o tenant ainda não configurou a chave.
 */
public class JdbcAccessControlStore implements AccessControlPorts.AccessControlStore {

    /** Leitura do valor vigente de um parâmetro do tenant (JSON cru, ou nulo). */
    @FunctionalInterface
    public interface TenantParameterReader {
        String currentValueJson(String definitionKey);
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final TenantParameterReader parameters;

    public JdbcAccessControlStore(DataSource dataSource, String schema, UUID tenantId,
                                  TenantParameterReader parameters) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null || parameters == null) {
            throw new IllegalArgumentException("dataSource, schema, tenant e parâmetros são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.parameters = parameters;
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> T inTx(Work<T> work) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof RuntimeException re) throw re;
                throw new IllegalStateException("Operação de controle de acesso falhou", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de controle de acesso falhou", e);
        }
    }

    private static Set<String> readStringSet(String json) {
        JsonNode node = Json.readTree(json);
        Set<String> result = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(item -> result.add(item.asString()));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Perfis compostos
    // ------------------------------------------------------------------

    private CompositeProfile mapProfile(ResultSet rs) throws SQLException {
        return new CompositeProfile(rs.getObject("id", UUID.class), tenantId, rs.getString("code"),
                rs.getString("name"), readStringSet(rs.getString("factory_profiles")),
                readStringSet(rs.getString("direct_permissions")), rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("active"));
    }

    @Override
    public List<CompositeProfile> listCompositeProfiles() {
        return inTx(c -> {
            List<CompositeProfile> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM " + t("composite_profiles") + " WHERE tenant_id = ? ORDER BY code")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapProfile(rs));
                }
            }
            return result;
        });
    }

    @Override
    public Optional<CompositeProfile> findCompositeProfile(UUID profileId) {
        return listCompositeProfiles().stream().filter(p -> p.id().equals(profileId)).findFirst();
    }

    @Override
    public void insertCompositeProfile(CompositeProfile profile) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("composite_profiles")
                    + " (id, tenant_id, code, name, factory_profiles, direct_permissions, created_by, created_at, active)"
                    + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)")) {
                ps.setObject(1, profile.id());
                ps.setObject(2, tenantId);
                ps.setString(3, profile.code());
                ps.setString(4, profile.name());
                ps.setString(5, Json.write(profile.factoryProfileCodes()));
                ps.setString(6, Json.write(profile.directPermissions()));
                ps.setObject(7, profile.createdBy());
                ps.setTimestamp(8, Timestamp.from(profile.createdAt()));
                ps.setBoolean(9, profile.active());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void assignCompositeProfile(UUID userId, UUID profileId, UUID assignedBy, Instant at) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("user_composite_profiles")
                    + " (tenant_id, user_id, profile_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?, ?)"
                    + " ON CONFLICT DO NOTHING")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setObject(3, profileId);
                ps.setObject(4, assignedBy);
                ps.setTimestamp(5, Timestamp.from(at));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<CompositeProfile> compositeProfilesOfUser(UUID userId) {
        return inTx(c -> {
            List<CompositeProfile> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT p.* FROM " + t("composite_profiles")
                    + " p JOIN " + t("user_composite_profiles")
                    + " a ON a.profile_id = p.id AND a.tenant_id = p.tenant_id"
                    + " WHERE p.tenant_id = ? AND a.user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapProfile(rs));
                }
            }
            return result;
        });
    }

    @Override
    public Set<String> factoryProfilesOfUser(UUID userId) {
        // Atribuições de fábrica vivem na gestão de usuários da P0.13
        // (user_profiles); leitura direta, sem duplicar a escrita.
        return inTx(c -> {
            Set<String> result = new LinkedHashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT profile FROM " + t("user_profiles")
                    + " WHERE tenant_id = ? AND user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(rs.getString(1));
                }
            } catch (SQLException e) {
                // Tabela da P0.13 ainda não integrada neste schema: sem fábrica.
                if (!"42P01".equals(e.getSQLState())) throw e;
            }
            return result;
        });
    }

    // ------------------------------------------------------------------
    // Restrição negativa e delegações
    // ------------------------------------------------------------------

    @Override
    public Optional<EffectivePermissions.NegativeRestriction> restrictionOfUser(UUID userId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT denied_permissions, created_by FROM "
                    + t("user_negative_restrictions") + " WHERE tenant_id = ? AND user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new EffectivePermissions.NegativeRestriction(userId,
                            readStringSet(rs.getString(1)), rs.getObject(2, UUID.class)));
                }
            }
        });
    }

    @Override
    public void upsertRestriction(EffectivePermissions.NegativeRestriction restriction) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("user_negative_restrictions")
                    + " (tenant_id, user_id, denied_permissions, created_by) VALUES (?, ?, ?::jsonb, ?)"
                    + " ON CONFLICT (tenant_id, user_id) DO UPDATE SET denied_permissions = EXCLUDED.denied_permissions")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, restriction.userId());
                ps.setString(3, Json.write(restriction.deniedPermissions()));
                ps.setObject(4, restriction.createdBy());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<EffectivePermissions.Delegation> delegationsOfDelegate(UUID delegateId) {
        return inTx(c -> {
            List<EffectivePermissions.Delegation> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + t("access_delegations")
                    + " WHERE tenant_id = ? AND delegate_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, delegateId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new EffectivePermissions.Delegation(rs.getObject("id", UUID.class), tenantId,
                                rs.getObject("delegator_id", UUID.class), delegateId,
                                readStringSet(rs.getString("permissions")),
                                rs.getTimestamp("valid_from").toInstant(), rs.getTimestamp("valid_to").toInstant(),
                                rs.getTimestamp("revoked_at") == null ? null
                                        : rs.getTimestamp("revoked_at").toInstant()));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public void insertDelegation(EffectivePermissions.Delegation delegation) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("access_delegations")
                    + " (id, tenant_id, delegator_id, delegate_id, permissions, valid_from, valid_to)"
                    + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                ps.setObject(1, delegation.id());
                ps.setObject(2, tenantId);
                ps.setObject(3, delegation.delegatorId());
                ps.setObject(4, delegation.delegateId());
                ps.setString(5, Json.write(delegation.permissions()));
                ps.setTimestamp(6, Timestamp.from(delegation.validFrom()));
                ps.setTimestamp(7, Timestamp.from(delegation.validTo()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void revokeDelegation(UUID delegationId, Instant at) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + t("access_delegations")
                    + " SET revoked_at = ? WHERE tenant_id = ? AND id = ?")) {
                ps.setTimestamp(1, Timestamp.from(at));
                ps.setObject(2, tenantId);
                ps.setObject(3, delegationId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Política (parâmetros do tenant) e aceites
    // ------------------------------------------------------------------

    private Integer intParam(String key) {
        String json = parameters.currentValueJson(key);
        if (json == null || json.isBlank()) return null;
        JsonNode node = Json.readTree(json);
        return node.isNumber() ? node.asInt() : null;
    }

    private boolean boolParam(String key, boolean fallback) {
        String json = parameters.currentValueJson(key);
        if (json == null || json.isBlank()) return fallback;
        JsonNode node = Json.readTree(json);
        return node.isBoolean() ? node.asBoolean() : fallback;
    }

    @Override
    public TenantSecurityPolicy securityPolicy() {
        TenantSecurityPolicy d = TenantSecurityPolicy.factoryDefault();
        Integer min = intParam("sec.password_min_length");
        Integer history = intParam("sec.password_history");
        Integer lockout = intParam("sec.lockout_attempts");
        return new TenantSecurityPolicy(
                min == null ? d.passwordMinLength() : min,
                intParam("sec.password_max_age_days"),
                history == null ? d.passwordHistory() : history,
                lockout == null ? d.lockoutAttempts() : lockout,
                intParam("sec.user_inactivity_days"),
                boolParam("sec.password_composition_required", false),
                boolParam("portal.passkey_enabled", false));
    }

    @Override
    public TenantSsoConfig ssoConfig() {
        String modeJson = parameters.currentValueJson("sec.sso_mode");
        TenantSsoConfig.SsoMode mode = TenantSsoConfig.SsoMode.LOCAL;
        if (modeJson != null && !modeJson.isBlank()) {
            JsonNode node = Json.readTree(modeJson);
            if (node.isString()) mode = TenantSsoConfig.SsoMode.valueOf(node.asString());
        }
        return new TenantSsoConfig(mode, boolParam("sec.sso_enforced", false),
                boolParam("sec.scim_enabled", false));
    }

    @Override
    public List<NistAcceptance> nistAcceptances() {
        return inTx(c -> {
            List<NistAcceptance> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + t("nist_policy_acceptances")
                    + " WHERE tenant_id = ?")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new NistAcceptance(rs.getObject("id", UUID.class), tenantId,
                                rs.getObject("accepted_by", UUID.class), rs.getString("option_key"),
                                rs.getTimestamp("accepted_at").toInstant(),
                                rs.getTimestamp("revoked_at") == null ? null
                                        : rs.getTimestamp("revoked_at").toInstant()));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public void insertNistAcceptance(NistAcceptance acceptance) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("nist_policy_acceptances")
                    + " (id, tenant_id, accepted_by, option_key, accepted_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, acceptance.id());
                ps.setObject(2, tenantId);
                ps.setObject(3, acceptance.acceptedBy());
                ps.setString(4, acceptance.optionKey());
                ps.setTimestamp(5, Timestamp.from(acceptance.acceptedAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Tentativas e sessões
    // ------------------------------------------------------------------

    @Override
    public LoginGuard.AttemptState attemptStateOf(UUID userId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT consecutive_failures, locked_until, last_login_at"
                    + " FROM " + t("login_attempt_states") + " WHERE tenant_id = ? AND user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return LoginGuard.AttemptState.NONE;
                    return new LoginGuard.AttemptState(rs.getInt(1),
                            rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant(),
                            rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant());
                }
            }
        });
    }

    @Override
    public void saveAttemptState(UUID userId, LoginGuard.AttemptState state) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("login_attempt_states")
                    + " (tenant_id, user_id, consecutive_failures, locked_until, last_login_at) VALUES (?, ?, ?, ?, ?)"
                    + " ON CONFLICT (tenant_id, user_id) DO UPDATE SET consecutive_failures = EXCLUDED.consecutive_failures,"
                    + " locked_until = EXCLUDED.locked_until, last_login_at = EXCLUDED.last_login_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                ps.setInt(3, state.consecutiveFailures());
                ps.setTimestamp(4, state.lockedUntil() == null ? null : Timestamp.from(state.lockedUntil()));
                ps.setTimestamp(5, state.lastLoginAt() == null ? null : Timestamp.from(state.lastLoginAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<LoginGuard.ActiveSession> activeSessionOf(UUID userId) {
        return activeSessions().stream().filter(s -> s.userId().equals(userId)).findFirst();
    }

    @Override
    public void openSession(LoginGuard.ActiveSession session) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("active_sessions")
                    + " WHERE tenant_id = ? AND user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, session.userId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("active_sessions")
                    + " (tenant_id, user_id, session_id, opened_at) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, session.userId());
                ps.setString(3, session.sessionId());
                ps.setTimestamp(4, Timestamp.from(session.openedAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void removeSession(String sessionId) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + t("active_sessions")
                    + " WHERE tenant_id = ? AND session_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, sessionId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<LoginGuard.ActiveSession> activeSessions() {
        return inTx(c -> {
            List<LoginGuard.ActiveSession> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT user_id, session_id, opened_at FROM "
                    + t("active_sessions") + " WHERE tenant_id = ? ORDER BY opened_at")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new LoginGuard.ActiveSession(rs.getObject(1, UUID.class), rs.getString(2),
                                rs.getTimestamp(3).toInstant()));
                    }
                }
            }
            return result;
        });
    }

    // ------------------------------------------------------------------
    // Recertificação
    // ------------------------------------------------------------------

    @Override
    public void insertRecertificationCampaign(RecertificationService.Campaign campaign) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("recertification_campaigns")
                    + " (id, tenant_id, name, opens_at, due_at, created_by) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, campaign.id());
                ps.setObject(2, tenantId);
                ps.setString(3, campaign.name());
                ps.setTimestamp(4, Timestamp.from(campaign.opensAt()));
                ps.setTimestamp(5, Timestamp.from(campaign.dueAt()));
                ps.setObject(6, campaign.createdBy());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<RecertificationService.Campaign> findCampaign(UUID campaignId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + t("recertification_campaigns")
                    + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, campaignId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new RecertificationService.Campaign(campaignId, tenantId,
                            rs.getString("name"), rs.getTimestamp("opens_at").toInstant(),
                            rs.getTimestamp("due_at").toInstant(), rs.getObject("created_by", UUID.class)));
                }
            }
        });
    }

    @Override
    public void insertRecertificationDecision(RecertificationService.Decision decision) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + t("recertification_decisions")
                    + " (id, tenant_id, campaign_id, subject_user_id, decided_by, kind, note, decided_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, decision.id());
                ps.setObject(2, tenantId);
                ps.setObject(3, decision.campaignId());
                ps.setObject(4, decision.subjectUserId());
                ps.setObject(5, decision.decidedBy());
                ps.setString(6, decision.kind().name());
                ps.setString(7, decision.note());
                ps.setTimestamp(8, Timestamp.from(decision.decidedAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<RecertificationService.Decision> decisionsOfCampaign(UUID campaignId) {
        return inTx(c -> {
            List<RecertificationService.Decision> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + t("recertification_decisions")
                    + " WHERE tenant_id = ? AND campaign_id = ? ORDER BY decided_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, campaignId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new RecertificationService.Decision(rs.getObject("id", UUID.class), campaignId,
                                rs.getObject("subject_user_id", UUID.class), rs.getObject("decided_by", UUID.class),
                                RecertificationService.DecisionKind.valueOf(rs.getString("kind")),
                                rs.getString("note"), rs.getTimestamp("decided_at").toInstant()));
                    }
                }
            }
            return result;
        });
    }
}
