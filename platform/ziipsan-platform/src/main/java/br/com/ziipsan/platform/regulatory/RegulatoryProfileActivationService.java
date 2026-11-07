package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.regulatory.RegulatoryActivationRule.PlannedVersion;

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
import java.util.Map;
import java.util.UUID;

/**
 * Ativação de perfil regulatório pelo envelope de proposta, aprovação e
 * aceite da P0.19 (P2.31), sobre as tabelas da V1_5 e da V1_13.
 *
 * <p>Fluxo, sem atalho:
 * <ol>
 *   <li>{@link #propose} valida o perfil na guarda de carga, exige os
 *       valores que o tenant precisa informar (chaves sem valor de
 *       fábrica) e grava a ativação {@code PROPOSED} com uma
 *       {@code parameter_versions} {@code PROPOSED} por valor;</li>
 *   <li>{@link #approve} exige aprovador diferente do proponente e
 *       vigência futura em relação à aprovação (regra e CHECKs da V1_5)
 *       e aprova a ativação e todas as versões;</li>
 *   <li>{@link #accept} registra o aceite formal de cada versão em
 *       {@code parameter_acceptances} (hash conferido) e só então cria a
 *       linha em {@code regulatory_profiles} para a agência e o
 *       município, encerrando a vigência do perfil anterior da mesma
 *       agência e município.</li>
 * </ol>
 *
 * <p>Como o {@code TenantParameterService}, cada método roda numa
 * transação própria com {@code app.current_tenant} local: as tabelas têm
 * RLS forçada e a GUC morre com a transação.
 *
 * <p>A simulação pré-ativação NÃO passa por aqui: ela é a classe pura
 * {@link RegulatorySimulation}, que não grava nada.
 */
public class RegulatoryProfileActivationService {

    /** Estado da ativação, como a tela o mostra. */
    public record ActivationView(UUID id, String profileKey, String agencyCode,
                                 String municipalityIbge, String municipalityName,
                                 LocalDate validFrom, LocalDate validTo,
                                 String status, UUID proposedBy, UUID approvedBy,
                                 UUID regulatoryProfileId) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final Clock clock;

    public RegulatoryProfileActivationService(DataSource dataSource, String schema, UUID tenantId) {
        this(dataSource, schema, tenantId, Clock.systemUTC());
    }

    RegulatoryProfileActivationService(DataSource dataSource, String schema, UUID tenantId, Clock clock) {
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
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
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
                for (Throwable t = e; t != null; t = t.getCause()) {
                    if (t instanceof SQLException sql
                            && ("23P01".equals(sql.getSQLState()) || "23505".equals(sql.getSQLState()))) {
                        throw conflict("A vigência se sobrepõe a outra versão ou perfil vigente.");
                    }
                }
                throw new IllegalStateException("Operação de ativação de perfil falhou: "
                        + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de ativação de perfil falhou: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Propõe a ativação do perfil para um município (ou para o tenant
     * inteiro, com {@code municipalityIbge} nulo), a partir de uma data.
     * Valores de chaves que exigem o tenant vêm em {@code tenantValues}
     * (as {@code REQUIRES_TENANT_VALUE} e a lista concreta de dias de
     * {@code bill.due_date_options}, cujo mínimo o perfil confirma);
     * faltando algum, a proposta é recusada com a lista das chaves, e
     * valor para chave confirmada ou de ausência confirmada é recusado
     * explicitamente (ver {@code RegulatoryActivationRule.planVersions}).
     */
    public ActivationView propose(String profileKey, String municipalityIbge, String municipalityName,
                                  LocalDate validFrom, LocalDate validTo,
                                  Map<String, String> tenantValues, UUID proposedBy) {
        RegulatoryProfileDef profile = ScRegulatoryProfiles.byKey(profileKey);
        if (profile == null) {
            throw new ApiException.NotFound("Perfil regulatório de fábrica não existe: " + profileKey);
        }
        if (validFrom == null || proposedBy == null) {
            throw validation("Vigência inicial e proponente são obrigatórios.");
        }
        List<PlannedVersion> planned = RegulatoryActivationRule.planVersions(
                profile, tenantValues, validFrom, validTo);
        return inTx(c -> {
            UUID activationId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("regulatory_profile_activations")
                            + " (tenant_id, profile_key, agency_code, municipality_ibge,"
                            + " municipality_name, valid_from, valid_to, status, proposed_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, 'PROPOSED', ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, profile.profileKey());
                ps.setString(3, profile.agencyCode());
                ps.setString(4, municipalityIbge);
                ps.setString(5, municipalityName);
                ps.setDate(6, Date.valueOf(validFrom));
                ps.setDate(7, validTo == null ? null : Date.valueOf(validTo));
                ps.setObject(8, proposedBy);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    activationId = rs.getObject(1, UUID.class);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("parameter_versions")
                            + " (tenant_id, definition_key, value, valid_from, valid_to, proposed_by,"
                            + " status, content_hash, norm_citation, regulatory_activation_id)"
                            + " VALUES (?, ?, ?::jsonb, ?, ?, ?, 'PROPOSED', ?, ?, ?)")) {
                for (PlannedVersion version : planned) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, version.definitionKey());
                    ps.setString(3, version.valueJson());
                    ps.setDate(4, Date.valueOf(version.validFrom()));
                    ps.setDate(5, version.validTo() == null ? null : Date.valueOf(version.validTo()));
                    ps.setObject(6, proposedBy);
                    ps.setString(7, version.contentHash());
                    ps.setString(8, version.normCitation());
                    ps.setObject(9, activationId);
                    ps.executeUpdate();
                }
            }
            return new ActivationView(activationId, profile.profileKey(), profile.agencyCode(),
                    municipalityIbge, municipalityName, validFrom, validTo, "PROPOSED",
                    proposedBy, null, null);
        });
    }

    /** Aprova a ativação e todas as suas versões. Quem propôs não aprova. */
    public ActivationView approve(UUID activationId, UUID approvedBy) {
        if (activationId == null || approvedBy == null) {
            throw validation("Ativação e aprovador são obrigatórios.");
        }
        return inTx(c -> {
            ActivationView current = load(c, activationId);
            if (!"PROPOSED".equals(current.status())) {
                throw conflict("Só ativação proposta pode ser aprovada.");
            }
            RegulatoryActivationRule.validateApproval(current.proposedBy(), approvedBy,
                    LocalDate.now(clock), current.validFrom());
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("parameter_versions")
                            + " SET status = 'APPROVED', approved_by = ?, approved_at = NOW(),"
                            + " updated_at = NOW()"
                            + " WHERE tenant_id = ? AND regulatory_activation_id = ?"
                            + " AND status = 'PROPOSED'")) {
                ps.setObject(1, approvedBy);
                ps.setObject(2, tenantId);
                ps.setObject(3, activationId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("regulatory_profile_activations")
                            + " SET status = 'APPROVED', approved_by = ?, approved_at = NOW(),"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, approvedBy);
                ps.setObject(2, tenantId);
                ps.setObject(3, activationId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("A ativação não existe para este tenant.");
                }
            }
            return new ActivationView(current.id(), current.profileKey(), current.agencyCode(),
                    current.municipalityIbge(), current.municipalityName(), current.validFrom(),
                    current.validTo(), "APPROVED", current.proposedBy(), approvedBy, null);
        });
    }

    /**
     * Aceite formal da ativação aprovada: registra o aceite de cada versão
     * (hash conferido contra a versão) e cria o perfil em
     * {@code regulatory_profiles}, encerrando o perfil anterior da mesma
     * agência e município na véspera do início do novo.
     */
    public ActivationView accept(UUID activationId, UUID acceptedBy, String accepterName,
                                 String accepterRole, String normCitation) {
        if (activationId == null || acceptedBy == null || accepterName == null
                || accepterName.isBlank() || accepterRole == null || accepterRole.isBlank()) {
            throw validation("Ativação, aceitante, nome e cargo são obrigatórios.");
        }
        return inTx(c -> {
            ActivationView current = load(c, activationId);
            if (!"APPROVED".equals(current.status())) {
                throw conflict("Só ativação aprovada pode receber aceite formal.");
            }
            RegulatoryProfileDef profile = ScRegulatoryProfiles.byKey(current.profileKey());
            if (profile == null) {
                throw new ApiException.NotFound(
                        "Perfil regulatório de fábrica não existe: " + current.profileKey());
            }
            // Ligação DAD-28 (correção pós-revisão): a adoção gravada no
            // perfil aplicado precisa LIGAR ao catálogo de normas da
            // P1.34 — não basta copiar o JSON. Toda NR do mapa de adoção
            // resolve para um cert_standard.code (slug do JSON de normas)
            // por AnaNormCatalogLink, e cada NR com ato de adoção precisa
            // EXISTIR em platform.cert_standard (schema fixo da core
            // V9_8; precedente: CertificationService consulta o mesmo
            // catálogo por code). Código ausente do catálogo = aceite
            // recusado, nomeando o código: sem a carga da P1.34 não há
            // adoção ligada para prometer.
            AnaNormCatalogLink.validateAdoptionLinkage(profile);
            List<String> adoptedCodes = AnaNormCatalogLink.adoptedCatalogCodes(profile);
            if (!adoptedCodes.isEmpty()) {
                List<String> missingCodes = new ArrayList<>(adoptedCodes);
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT code FROM platform.cert_standard WHERE code = ?")) {
                    for (String code : adoptedCodes) {
                        ps.setString(1, code);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                missingCodes.remove(code);
                            }
                        }
                    }
                }
                if (!missingCodes.isEmpty()) {
                    throw conflict("Ligação DAD-28 não resolvida: os códigos "
                            + String.join(", ", missingCodes) + " das NR adotadas pelo perfil "
                            + profile.profileKey() + " não existem no catálogo de normas "
                            + "(platform.cert_standard, carga da P1.34).");
                }
            }

            List<UUID> versionIds = new ArrayList<>();
            List<String> versionHashes = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, content_hash FROM " + t("parameter_versions")
                            + " WHERE tenant_id = ? AND regulatory_activation_id = ?"
                            + " AND status = 'APPROVED' ORDER BY definition_key")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, activationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        versionIds.add(rs.getObject(1, UUID.class));
                        versionHashes.add(rs.getString(2));
                    }
                }
            }
            if (versionIds.isEmpty()) {
                throw conflict("A ativação não tem versões aprovadas para aceitar.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("parameter_acceptances")
                            + " (tenant_id, version_id, accepted_by, accepter_name, accepter_role,"
                            + " content_hash, norm_citation)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < versionIds.size(); i++) {
                    // O aceite registra o hash da propria versao (lido dela
                    // acima): e a conferencia que a V1_5 desenha -- aceite de
                    // outro conteudo nao existe porque o hash nao vem de fora.
                    ps.setObject(1, tenantId);
                    ps.setObject(2, versionIds.get(i));
                    ps.setObject(3, acceptedBy);
                    ps.setString(4, accepterName);
                    ps.setString(5, accepterRole);
                    ps.setString(6, versionHashes.get(i));
                    ps.setString(7, normCitation);
                    ps.executeUpdate();
                }
            }

            // Encerra o perfil anterior da mesma agencia e municipio que
            // sobreponha o novo (o EXCLUDE corrigido na V1_13 e por
            // municipio: perfis de outros municipios nao sao tocados).
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("regulatory_profiles")
                            + " SET valid_to = ?::date - 1, status = 'SUPERSEDED',"
                            + " updated_at = NOW()"
                            + " WHERE tenant_id = ? AND agency_code = ?"
                            + " AND COALESCE(municipality_ibge, '') = COALESCE(?, '')"
                            + " AND status = 'ACTIVE' AND valid_from < ?::date"
                            + " AND (valid_to IS NULL OR valid_to >= ?::date)")) {
                ps.setDate(1, Date.valueOf(current.validFrom()));
                ps.setObject(2, tenantId);
                ps.setString(3, current.agencyCode());
                ps.setString(4, current.municipalityIbge());
                ps.setDate(5, Date.valueOf(current.validFrom()));
                ps.setDate(6, Date.valueOf(current.validFrom()));
                ps.executeUpdate();
            }

            String adoptionJson = valueJsonOf(profile, "reg.ana_nr_adoption");
            String factsJson = valueJsonOf(profile, "reg.normative_facts");
            UUID profileId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("regulatory_profiles")
                            + " (tenant_id, agency_code, act_number, status, valid_from, valid_to,"
                            + " norm_source, municipality_ibge, municipality_name, profile_key,"
                            + " ana_nr_adoption, normative_facts)"
                            + " VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)"
                            + " RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, profile.agencyCode());
                ps.setString(3, profile.generalConditionsAct());
                ps.setDate(4, Date.valueOf(current.validFrom()));
                ps.setDate(5, current.validTo() == null ? null : Date.valueOf(current.validTo()));
                ps.setString(6, "Perfil de fábrica P2.31: " + profile.profileKey());
                ps.setString(7, current.municipalityIbge());
                ps.setString(8, current.municipalityName());
                ps.setString(9, profile.profileKey());
                ps.setString(10, adoptionJson);
                ps.setString(11, factsJson);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    profileId = rs.getObject(1, UUID.class);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("regulatory_profile_activations")
                            + " SET status = 'ACCEPTED', regulatory_profile_id = ?,"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, profileId);
                ps.setObject(2, tenantId);
                ps.setObject(3, activationId);
                ps.executeUpdate();
            }
            return new ActivationView(current.id(), current.profileKey(), current.agencyCode(),
                    current.municipalityIbge(), current.municipalityName(), current.validFrom(),
                    current.validTo(), "ACCEPTED", current.proposedBy(), current.approvedBy(),
                    profileId);
        });
    }

    /** Divergências do perfil, para exibição ao tenant antes e depois da ativação. */
    public List<RegulatoryProfileDef.ProfileDivergence> divergencesOf(String profileKey) {
        RegulatoryProfileDef profile = ScRegulatoryProfiles.byKey(profileKey);
        if (profile == null) {
            throw new ApiException.NotFound("Perfil regulatório de fábrica não existe: " + profileKey);
        }
        return profile.divergences();
    }

    private static String valueJsonOf(RegulatoryProfileDef profile, String definitionKey) {
        RegulatoryProfileDef.ProfileValue value = profile.value(definitionKey);
        return value == null ? null : value.valueJson();
    }

    private ActivationView load(Connection c, UUID activationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, profile_key, agency_code, municipality_ibge, municipality_name,"
                        + " valid_from, valid_to, status, proposed_by, approved_by,"
                        + " regulatory_profile_id FROM "
                        + t("regulatory_profile_activations")
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, activationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A ativação não existe para este tenant.");
                }
                Date from = rs.getDate(6);
                Date to = rs.getDate(7);
                return new ActivationView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5),
                        from == null ? null : from.toLocalDate(),
                        to == null ? null : to.toLocalDate(),
                        rs.getString(8), rs.getObject(9, UUID.class),
                        rs.getObject(10, UUID.class), rs.getObject(11, UUID.class));
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
