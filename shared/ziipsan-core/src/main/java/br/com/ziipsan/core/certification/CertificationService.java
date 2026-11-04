package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Quality-certification core (P1.32; ADR-0041).
 *
 * <p>What this engine is: the single writer of the tenant-side certification
 * state — one activation row per standard, its clause map and its evidence
 * links — for one tenant schema, over the global standard catalog that lives
 * in the {@code platform} schema (core V9_8). Every method runs in one
 * transaction, with the tenant context set on the connection first, exactly
 * like the work-order engine (P1.31).
 *
 * <p>What it is not: it holds no text of any standard and no sector content.
 * The catalog it reads starts EMPTY and is populated only by the P1.34 load
 * from the consolidated catalog; until then the only rows it can see are the
 * synthetic rows a test inserts itself. Clause titles and summaries in the
 * catalog are written in the curator's own words (ADR-0041 item 11); this
 * service copies them verbatim into the tenant's clause map at acceptance
 * time and never invents a clause, an edition or a standard.
 *
 * <p>Activation cycle (mirrors the parameter propose/approve/accept of
 * P0.19, with the same proposer-approver separation):
 * <ul>
 *   <li>{@code PROPOSED} — created by {@link #activate}, with a declared
 *       scope statement and an edition that exists in the catalog;</li>
 *   <li>{@code APPROVED} — approved by someone other than the proposer;</li>
 *   <li>{@code ACTIVE} — formally accepted; only then is the clause map
 *       copied from the catalog edition, so a never-accepted activation
 *       has no map to display;</li>
 *   <li>{@code INACTIVE} — deactivated. Nothing is deleted: the activation
 *       row, the frozen clause map and every evidence link stay queryable
 *       for the retention period (parameter
 *       {@code cert.deactivation_keeps_records}, class a, locked true).
 *       Reactivation reuses the same row through a new propose/approve/
 *       accept cycle and unfreezes the same map rows — no duplicates.</li>
 * </ul>
 *
 * <p>Two rules are enforced here and again by the database (V21_1):
 * a {@code REGULATORY} standard can never be deactivated (ADR-0041: it is
 * mandatory by applicability), and an {@code ACTIVE} activation always
 * carries its acceptance (accepted_at + hash). The fixed notice below is
 * returned with every view the controller serves, so no screen that shows
 * a clause map can be read as a certification opinion.
 *
 * <p>Boundary: this package depends on nothing outside the core. In
 * particular it names no sector catalog, no sector entity and no sector
 * term — the sector content of each standard belongs to the vertical
 * package (P1.34 load, ED-69 onwards), never to this engine.
 */
public class CertificationService {

    /** Fixed notice shown with every clause map (ADR-0041 item 8). */
    public static final String DISCLAIMER =
            "Este módulo não certifica. A certificação é concedida por organismo "
                    + "de certificação acreditado, após auditoria no cliente. O mapa de "
                    + "cláusulas é autoavaliação do cliente, nunca parecer do produto: "
                    + "o sistema produz, organiza e preserva evidência e aponta lacuna "
                    + "de cláusula.";

    /** Standard classes of the single catalog (ADR-0041 item 2). */
    public enum StandardClass {
        REGULATORY, VOLUNTARY, REFERENCE, EXPORT_PROFILE;

        /** Parses a class, refusing anything outside the four catalog classes. */
        public static StandardClass from(String value) {
            if (value == null || value.isBlank()) {
                throw new ApiException.Validation("A classe da norma é obrigatória.",
                        List.of(new ApiException.FieldError("standardClass", "required",
                                "A classe da norma é obrigatória.")), List.of());
            }
            try {
                return StandardClass.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw new ApiException.Validation("Classe de norma desconhecida: " + value + ".",
                        List.of(new ApiException.FieldError("standardClass", "invalid",
                                "Classe de norma desconhecida: " + value + ".")), List.of());
            }
        }
    }

    /** Generic evidence kinds a clause map row can point at (P1.32; P2.25 extends the set). */
    public enum EvidenceKind {
        DOCUMENT, SERVICE_ORDER, RECORD, OTHER;

        /** Parses a kind, refusing anything outside the catalog of kinds. */
        public static EvidenceKind from(String value) {
            if (value == null || value.isBlank()) {
                throw new ApiException.Validation("O tipo da evidência é obrigatório.",
                        List.of(new ApiException.FieldError("evidenceKind", "required",
                                "O tipo da evidência é obrigatório.")), List.of());
            }
            try {
                return EvidenceKind.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw new ApiException.Validation("Tipo de evidência desconhecido: " + value + ".",
                        List.of(new ApiException.FieldError("evidenceKind", "invalid",
                                "Tipo de evidência desconhecido: " + value + ".")), List.of());
            }
        }
    }

    /** Activation states; see the class documentation for the cycle. */
    public enum ActivationStatus {
        PROPOSED, APPROVED, ACTIVE, INACTIVE
    }

    public record StandardView(UUID id, String catalogKey, String code, String title, String summary,
                               StandardClass standardClass, String area, String module, String agency,
                               String verificationSeal, String status,
                               LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    public record EditionView(UUID id, UUID standardId, String edition,
                              LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    public record ClauseView(UUID id, UUID editionId, String clauseNumber, String title, String summary) {
    }

    public record ActivationView(UUID id, String standardCode, String catalogKey,
                                 StandardClass standardClass, String edition, String scopeStatement,
                                 ActivationStatus status, UUID proposedBy, UUID approvedBy,
                                 UUID acceptedBy, String acceptanceHash,
                                 OffsetDateTime activatedAt, OffsetDateTime deactivatedAt,
                                 UUID deactivatedBy, String deactivationReason) {
    }

    public record ClauseMapView(UUID id, UUID activationId, UUID clauseId, String clauseNumber,
                                String titleSnapshot, String mapStatus, OffsetDateTime frozenAt) {
    }

    public record EvidenceLinkView(UUID id, UUID clauseMapId, EvidenceKind evidenceKind,
                                   UUID evidenceId, String evidenceRef, UUID linkedBy,
                                   OffsetDateTime linkedAt, boolean active) {
    }

    public record ActivateInput(String standardCode, String scopeStatement, String edition) {
    }

    public record EvidenceInput(EvidenceKind evidenceKind, UUID evidenceId, String evidenceRef) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final String platformSchema;
    private final UUID tenantId;

    public CertificationService(DataSource dataSource, String schema, UUID tenantId) {
        this(dataSource, schema, "platform", tenantId);
    }

    public CertificationService(DataSource dataSource, String schema, String platformSchema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank()
                || platformSchema == null || platformSchema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema, platformSchema and tenantId are required");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.platformSchema = platformSchema;
        this.tenantId = tenantId;
    }

    // ------------------------------------------------------------------
    // Catalog reads (global; empty until the P1.34 load)
    // ------------------------------------------------------------------

    /** Every standard in the global catalog, in catalog-key order. */
    public List<StandardView> listStandards() {
        return inTx(c -> {
            List<StandardView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, catalog_key, code, title, summary, standard_class, area, module, agency,"
                            + " verification_seal, status, effective_from, effective_to"
                            + " FROM " + platformSchema + ".cert_standard ORDER BY catalog_key")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readStandard(rs));
                    }
                }
            }
            return out;
        });
    }

    /** Standards of one class only (the P1.34 query shape: by class). */
    public List<StandardView> listStandardsByClass(StandardClass standardClass) {
        if (standardClass == null) {
            throw new ApiException.Validation("A classe da norma é obrigatória.",
                    List.of(new ApiException.FieldError("standardClass", "required",
                            "A classe da norma é obrigatória.")), List.of());
        }
        return inTx(c -> {
            List<StandardView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, catalog_key, code, title, summary, standard_class, area, module, agency,"
                            + " verification_seal, status, effective_from, effective_to"
                            + " FROM " + platformSchema + ".cert_standard"
                            + " WHERE standard_class = ? ORDER BY catalog_key")) {
                ps.setString(1, standardClass.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readStandard(rs));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Filtro da consulta do catalogo (P1.34: por classe, area, modulo,
     * situacao e agencia). Todo campo nulo ou em branco e "sem filtro".
     * O modulo casa por pertinencia: o catalogo guarda os modulos
     * afetados unidos por virgula (carga R__norm_catalog), e a consulta
     * casa o modulo inteiro entre virgulas, nunca por substring — um
     * filtro "lgpd" nao casa "lgpd_extra". A situacao aceita so os 4
     * valores do CHECK da V9_8; qualquer outro e erro de validacao,
     * nunca lista vazia silenciosa.
     */
    public record StandardFilter(StandardClass standardClass, String area, String module,
                                 String status, String agency) {
    }

    /** Catalogo filtrado (P1.34); sem filtro, equivale a {@link #listStandards()}. */
    public List<StandardView> listStandardsFiltered(StandardFilter filter) {
        return inTx(c -> {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, catalog_key, code, title, summary, standard_class, area, module, agency,"
                            + " verification_seal, status, effective_from, effective_to"
                            + " FROM " + platformSchema + ".cert_standard WHERE 1 = 1");
            List<String> params = new ArrayList<>();
            if (filter != null && filter.standardClass() != null) {
                sql.append(" AND standard_class = ?");
                params.add(filter.standardClass().name());
            }
            if (filter != null && filter.area() != null && !filter.area().isBlank()) {
                sql.append(" AND area = ?");
                params.add(filter.area().trim());
            }
            if (filter != null && filter.module() != null && !filter.module().isBlank()) {
                sql.append(" AND (',' || module || ',' LIKE '%,' || REPLACE(REPLACE(REPLACE(?, '\\', '\\\\'),"
                        + " '%', '\\%'), '_', '\\_') || ',%' ESCAPE '\\')");
                params.add(filter.module().trim());
            }
            if (filter != null && filter.status() != null && !filter.status().isBlank()) {
                String status = filter.status().trim();
                if (!List.of("VIGENTE", "VIGENCIA_FUTURA", "REVOGADA", "SUBSTITUIDA").contains(status)) {
                    throw new ApiException.Validation("Situação de norma desconhecida: " + status + ".",
                            List.of(new ApiException.FieldError("status", "invalid",
                                    "Situação de norma desconhecida: " + status + ".")), List.of());
                }
                sql.append(" AND status = ?");
                params.add(status);
            }
            if (filter != null && filter.agency() != null && !filter.agency().isBlank()) {
                sql.append(" AND agency = ?");
                params.add(filter.agency().trim());
            }
            sql.append(" ORDER BY catalog_key");
            List<StandardView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setString(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readStandard(rs));
                    }
                }
            }
            return out;
        });
    }

    /** One standard by code; a code the catalog does not hold is a 404, never an empty shell. */
    public StandardView getStandard(String code) {
        requireCode(code);
        return inTx(c -> loadStandard(c, code.trim()));
    }

    /** Editions of a standard, in edition order. */
    public List<EditionView> listEditions(String standardCode) {
        requireCode(standardCode);
        return inTx(c -> {
            StandardView standard = loadStandard(c, standardCode.trim());
            List<EditionView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, standard_id, edition, effective_from, effective_to"
                            + " FROM " + platformSchema + ".cert_standard_edition"
                            + " WHERE standard_id = ? ORDER BY edition")) {
                ps.setObject(1, standard.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new EditionView(uuid(rs, "id"), uuid(rs, "standard_id"),
                                rs.getString("edition"), date(rs, "effective_from"), date(rs, "effective_to")));
                    }
                }
            }
            return out;
        });
    }

    /** Clauses of one edition, in map order (sort_order, then clause number). */
    public List<ClauseView> listClauses(String standardCode, String edition) {
        requireCode(standardCode);
        if (edition == null || edition.isBlank()) {
            throw new ApiException.Validation("A edição é obrigatória.",
                    List.of(new ApiException.FieldError("edition", "required",
                            "A edição é obrigatória.")), List.of());
        }
        return inTx(c -> {
            StandardView standard = loadStandard(c, standardCode.trim());
            UUID editionId = loadEditionId(c, standard.id(), edition.trim());
            List<ClauseView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, edition_id, clause_number, title, summary"
                            + " FROM " + platformSchema + ".cert_clause"
                            + " WHERE edition_id = ? ORDER BY sort_order, clause_number")) {
                ps.setObject(1, editionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ClauseView(uuid(rs, "id"), uuid(rs, "edition_id"),
                                rs.getString("clause_number"), rs.getString("title"), rs.getString("summary")));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Activation cycle
    // ------------------------------------------------------------------

    /**
     * Proposes an activation (new row), or re-proposes a deactivated one
     * (same row, new cycle). A proposed, approved or active activation for
     * the same standard is a conflict: there is exactly one activation row
     * per standard per tenant, so no second row can shadow the first.
     */
    public ActivationView activate(ActivateInput input, UUID proposedBy) {
        if (input == null || input.standardCode() == null || input.standardCode().isBlank()) {
            throw new ApiException.Validation("O código da norma é obrigatório.",
                    List.of(new ApiException.FieldError("standardCode", "required",
                            "O código da norma é obrigatório.")), List.of());
        }
        requireScope(input.scopeStatement());
        if (input.edition() == null || input.edition().isBlank()) {
            throw new ApiException.Validation("A edição é obrigatória.",
                    List.of(new ApiException.FieldError("edition", "required",
                            "A edição é obrigatória.")), List.of());
        }
        if (proposedBy == null) {
            throw new ApiException.Validation("O proponente é obrigatório.",
                    List.of(new ApiException.FieldError("proposedBy", "required",
                            "O proponente é obrigatório.")), List.of());
        }
        String code = input.standardCode().trim();
        String scope = input.scopeStatement().trim();
        String edition = input.edition().trim();
        return inTx(c -> {
            StandardView standard = loadStandard(c, code);
            // The edition must exist in the catalog for this standard: an
            // activation may never name an edition the catalog never held.
            loadEditionId(c, standard.id(), edition);
            ActivationView existing = loadActivationByCode(c, code);
            if (existing != null && existing.status() != ActivationStatus.INACTIVE) {
                throw new ApiException.Conflict("activation-exists",
                        "A norma " + code + " já tem uma ativação " + existing.status().name().toLowerCase()
                                + " para este tenant; desative-a antes de propor outra.");
            }
            UUID id;
            if (existing == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + t("cert_standard_activation")
                                + " (tenant_id, standard_code, catalog_key, standard_class, edition,"
                                + " scope_statement, status, proposed_by, created_by)"
                                + " VALUES (?, ?, ?, ?, ?, ?, 'PROPOSED', ?, ?) RETURNING id")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, code);
                    ps.setString(3, standard.catalogKey());
                    ps.setString(4, standard.standardClass().name());
                    ps.setString(5, edition);
                    ps.setString(6, scope);
                    ps.setObject(7, proposedBy);
                    ps.setObject(8, proposedBy);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getObject(1, UUID.class);
                    }
                }
            } else {
                // Reactivation: same row, fresh cycle; the frozen map rows
                // stay frozen until the new acceptance unfreezes them.
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + t("cert_standard_activation")
                                + " SET edition = ?, scope_statement = ?, status = 'PROPOSED',"
                                + " proposed_by = ?, approved_by = NULL, approved_at = NULL,"
                                + " accepted_by = NULL, accepted_at = NULL, acceptance_hash = NULL,"
                                + " activated_at = NULL, deactivated_at = NULL, deactivated_by = NULL,"
                                + " deactivation_reason = NULL, updated_at = NOW(), updated_by = ?"
                                + " WHERE tenant_id = ? AND id = ?")) {
                    ps.setString(1, edition);
                    ps.setString(2, scope);
                    ps.setObject(3, proposedBy);
                    ps.setObject(4, proposedBy);
                    ps.setObject(5, tenantId);
                    ps.setObject(6, existing.id());
                    if (ps.executeUpdate() == 0) {
                        throw new ApiException.NotFound("A ativação não existe para este tenant.");
                    }
                }
                id = existing.id();
            }
            return loadActivation(c, id);
        });
    }

    /** Approves a proposed activation. Who proposed does not approve. */
    public ActivationView approve(UUID activationId, UUID approvedBy) {
        if (activationId == null || approvedBy == null) {
            throw new ApiException.Validation("A ativação e o aprovador são obrigatórios.",
                    List.of(new ApiException.FieldError("activationId", "required",
                            "A ativação e o aprovador são obrigatórios.")), List.of());
        }
        return inTx(c -> {
            ActivationView current = loadActivation(c, activationId);
            if (current.status() != ActivationStatus.PROPOSED) {
                throw new ApiException.Conflict("activation-state",
                        "Só uma ativação proposta pode ser aprovada (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            if (approvedBy.equals(current.proposedBy())) {
                throw new ApiException.Forbidden("Quem propôs não aprova a própria ativação.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_standard_activation")
                            + " SET status = 'APPROVED', approved_by = ?, approved_at = NOW(),"
                            + " updated_at = NOW(), updated_by = ? WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, approvedBy);
                ps.setObject(2, approvedBy);
                ps.setObject(3, tenantId);
                ps.setObject(4, activationId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("A ativação não existe para este tenant.");
                }
            }
            return loadActivation(c, activationId);
        });
    }

    /**
     * Formally accepts an approved activation: it becomes ACTIVE and the
     * clause map is copied from the catalog edition at this moment. The
     * acceptance hash covers tenant, activation, standard, edition, scope
     * and accepter, so a later change of any of them no longer matches the
     * accepted content (the P0.19 acceptance pattern).
     */
    public ActivationView accept(UUID activationId, UUID acceptedBy) {
        if (activationId == null || acceptedBy == null) {
            throw new ApiException.Validation("A ativação e o aceitante são obrigatórios.",
                    List.of(new ApiException.FieldError("activationId", "required",
                            "A ativação e o aceitante são obrigatórios.")), List.of());
        }
        return inTx(c -> {
            ActivationView current = loadActivation(c, activationId);
            if (current.status() != ActivationStatus.APPROVED) {
                throw new ApiException.Conflict("activation-state",
                        "Só uma ativação aprovada pode receber aceite formal (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            String hash = acceptanceHash(tenantId, activationId, current.standardCode(),
                    current.edition(), current.scopeStatement(), acceptedBy);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_standard_activation")
                            + " SET status = 'ACTIVE', accepted_by = ?, accepted_at = NOW(),"
                            + " acceptance_hash = ?, activated_at = NOW(),"
                            + " updated_at = NOW(), updated_by = ? WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, acceptedBy);
                ps.setString(2, hash);
                ps.setObject(3, acceptedBy);
                ps.setObject(4, tenantId);
                ps.setObject(5, activationId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("A ativação não existe para este tenant.");
                }
            }
            // Copy the edition's clauses into the tenant map; reactivations
            // reuse the rows that already exist (no duplicates).
            StandardView standard = loadStandard(c, current.standardCode());
            UUID editionId = loadEditionId(c, standard.id(), current.edition());
            List<ClauseView> clauses = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, edition_id, clause_number, title, summary"
                            + " FROM " + platformSchema + ".cert_clause"
                            + " WHERE edition_id = ? ORDER BY sort_order, clause_number")) {
                ps.setObject(1, editionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        clauses.add(new ClauseView(uuid(rs, "id"), uuid(rs, "edition_id"),
                                rs.getString("clause_number"), rs.getString("title"), rs.getString("summary")));
                    }
                }
            }
            for (ClauseView clause : clauses) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + t("cert_clause_map")
                                + " (tenant_id, activation_id, clause_id, clause_number, title_snapshot,"
                                + " map_status, frozen_at, created_by)"
                                + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', NULL, ?)"
                                + " ON CONFLICT (tenant_id, activation_id, clause_id) DO UPDATE"
                                + " SET map_status = 'ACTIVE', frozen_at = NULL, updated_at = NOW(), updated_by = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, activationId);
                    ps.setObject(3, clause.id());
                    ps.setString(4, clause.clauseNumber());
                    ps.setString(5, clause.title());
                    ps.setObject(6, acceptedBy);
                    ps.setObject(7, acceptedBy);
                    ps.executeUpdate();
                }
            }
            return loadActivation(c, activationId);
        });
    }

    /**
     * Deactivates an active activation. Nothing is deleted: the row, the
     * clause map (now FROZEN) and every evidence link stay queryable. A
     * REGULATORY standard is refused here and by the V21_1 CHECK.
     */
    public ActivationView deactivate(UUID activationId, UUID actor, String reason) {
        if (activationId == null || actor == null) {
            throw new ApiException.Validation("A ativação e o responsável pelo desligamento são obrigatórios.",
                    List.of(new ApiException.FieldError("activationId", "required",
                            "A ativação e o responsável pelo desligamento são obrigatórios.")), List.of());
        }
        return inTx(c -> {
            ActivationView current = loadActivation(c, activationId);
            if (current.status() != ActivationStatus.ACTIVE) {
                throw new ApiException.Conflict("activation-state",
                        "Só uma ativação ativa pode ser desligada (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            if (current.standardClass() == StandardClass.REGULATORY) {
                throw new ApiException.Forbidden(
                        "A norma " + current.standardCode() + " é obrigatória por aplicabilidade e não pode ser desligada.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_standard_activation")
                            + " SET status = 'INACTIVE', deactivated_at = NOW(), deactivated_by = ?,"
                            + " deactivation_reason = ?, updated_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actor);
                if (reason == null || reason.isBlank()) {
                    ps.setNull(2, Types.VARCHAR);
                } else {
                    ps.setString(2, reason.trim());
                }
                ps.setObject(3, actor);
                ps.setObject(4, tenantId);
                ps.setObject(5, activationId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException.NotFound("A ativação não existe para este tenant.");
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_clause_map")
                            + " SET map_status = 'FROZEN', frozen_at = NOW(), updated_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND activation_id = ? AND map_status = 'ACTIVE'")) {
                ps.setObject(1, actor);
                ps.setObject(2, tenantId);
                ps.setObject(3, activationId);
                ps.executeUpdate();
            }
            return loadActivation(c, activationId);
        });
    }

    // ------------------------------------------------------------------
    // Activation and map reads; the gate P2.25 will use
    // ------------------------------------------------------------------

    /** Every activation of this tenant, most recently updated first. */
    public List<ActivationView> listActivations() {
        return inTx(c -> {
            List<ActivationView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, standard_code, catalog_key, standard_class, edition, scope_statement, status,"
                            + " proposed_by, approved_by, accepted_by, acceptance_hash, activated_at,"
                            + " deactivated_at, deactivated_by, deactivation_reason"
                            + " FROM " + t("cert_standard_activation")
                            + " WHERE tenant_id = ? ORDER BY updated_at DESC")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readActivation(rs));
                    }
                }
            }
            return out;
        });
    }

    public ActivationView getActivation(UUID activationId) {
        if (activationId == null) {
            throw new ApiException.NotFound("A ativação não existe para este tenant.");
        }
        return inTx(c -> loadActivation(c, activationId));
    }

    /** The clause map of an activation — frozen rows included, never hidden. */
    public List<ClauseMapView> listClauseMap(UUID activationId) {
        if (activationId == null) {
            throw new ApiException.NotFound("A ativação não existe para este tenant.");
        }
        return inTx(c -> {
            loadActivation(c, activationId);
            List<ClauseMapView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, activation_id, clause_id, clause_number, title_snapshot, map_status, frozen_at"
                            + " FROM " + t("cert_clause_map")
                            + " WHERE tenant_id = ? AND activation_id = ? ORDER BY clause_number")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, activationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ClauseMapView(uuid(rs, "id"), uuid(rs, "activation_id"),
                                uuid(rs, "clause_id"), rs.getString("clause_number"),
                                rs.getString("title_snapshot"), rs.getString("map_status"),
                                timestamp(rs, "frozen_at")));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Whether a standard is ACTIVE for this tenant right now. This is the
     * gate the later fronts use to decide whether a standard's fields and
     * cycles are required on a record (P2.25: required only when the
     * standard is active) — a never-activated or deactivated standard
     * leaves the record free.
     */
    public boolean isStandardActive(String standardCode) {
        requireCode(standardCode);
        return inTx(c -> {
            ActivationView activation = loadActivationByCode(c, standardCode.trim());
            return activation != null && activation.status() == ActivationStatus.ACTIVE;
        });
    }

    /** Codes of the standards ACTIVE for this tenant right now. */
    public List<String> activeStandards() {
        return inTx(c -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT standard_code FROM " + t("cert_standard_activation")
                            + " WHERE tenant_id = ? AND status = 'ACTIVE' ORDER BY standard_code")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Evidence links (many-to-many)
    // ------------------------------------------------------------------

    /**
     * Links one evidence to one clause-map row. The map row must be ACTIVE:
     * a frozen map (deactivated standard) stays consultable but takes no
     * new evidence. The same evidence linked to two map rows is the
     * many-to-many case, not a duplicate — the duplicate is the same
     * evidence on the same map row, refused as a conflict.
     */
    public EvidenceLinkView linkEvidence(UUID clauseMapId, EvidenceInput input, UUID linkedBy) {
        if (clauseMapId == null) {
            throw new ApiException.Validation("A linha do mapa é obrigatória.",
                    List.of(new ApiException.FieldError("clauseMapId", "required",
                            "A linha do mapa é obrigatória.")), List.of());
        }
        if (input == null || input.evidenceKind() == null || input.evidenceId() == null) {
            throw new ApiException.Validation("O tipo e o id da evidência são obrigatórios.",
                    List.of(new ApiException.FieldError("evidenceId", "required",
                            "O tipo e o id da evidência são obrigatórios.")), List.of());
        }
        if (linkedBy == null) {
            throw new ApiException.Validation("Quem vincula é obrigatório.",
                    List.of(new ApiException.FieldError("linkedBy", "required",
                            "Quem vincula é obrigatório.")), List.of());
        }
        return inTx(c -> {
            String mapStatus;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT map_status FROM " + t("cert_clause_map")
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, clauseMapId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("A linha do mapa não existe para este tenant.");
                    }
                    mapStatus = rs.getString(1);
                }
            }
            if (!"ACTIVE".equals(mapStatus)) {
                throw new ApiException.Conflict("clause-map-frozen",
                        "O mapa está congelado (norma desligada): ele continua consultável, mas não recebe nova evidência.");
            }
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_evidence_link")
                            + " (tenant_id, clause_map_id, evidence_kind, evidence_id, evidence_ref, linked_by, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, clauseMapId);
                ps.setString(3, input.evidenceKind().name());
                ps.setObject(4, input.evidenceId());
                if (input.evidenceRef() == null || input.evidenceRef().isBlank()) {
                    ps.setNull(5, Types.VARCHAR);
                } else {
                    ps.setString(5, input.evidenceRef().trim());
                }
                ps.setObject(6, linkedBy);
                ps.setObject(7, linkedBy);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    id = rs.getObject(1, UUID.class);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new ApiException.Conflict("evidence-link-exists",
                            "Esta evidência já está vinculada a esta cláusula do mapa.");
                }
                throw new IllegalStateException("Failed to link evidence: " + e.getMessage(), e);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, clause_map_id, evidence_kind, evidence_id, evidence_ref, linked_by, linked_at, active"
                            + " FROM " + t("cert_evidence_link") + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new EvidenceLinkView(uuid(rs, "id"), uuid(rs, "clause_map_id"),
                            EvidenceKind.from(rs.getString("evidence_kind")), uuid(rs, "evidence_id"),
                            rs.getString("evidence_ref"), uuidOrNull(rs, "linked_by"),
                            timestamp(rs, "linked_at"), rs.getBoolean("active"));
                }
            }
        });
    }

    /** Evidence links of one clause-map row, in link order — deactivated links included, never hidden. */
    public List<EvidenceLinkView> listEvidence(UUID clauseMapId) {
        if (clauseMapId == null) {
            throw new ApiException.NotFound("A linha do mapa não existe para este tenant.");
        }
        return inTx(c -> {
            loadClauseMapRow(c, clauseMapId);
            List<EvidenceLinkView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, clause_map_id, evidence_kind, evidence_id, evidence_ref, linked_by, linked_at, active"
                            + " FROM " + t("cert_evidence_link")
                            + " WHERE tenant_id = ? AND clause_map_id = ? ORDER BY linked_at, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, clauseMapId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new EvidenceLinkView(uuid(rs, "id"), uuid(rs, "clause_map_id"),
                                EvidenceKind.from(rs.getString("evidence_kind")), uuid(rs, "evidence_id"),
                                rs.getString("evidence_ref"), uuidOrNull(rs, "linked_by"),
                                timestamp(rs, "linked_at"), rs.getBoolean("active")));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Corrects a wrong evidence link with a logical deactivation (P2.25,
     * delivering the V21_1 promise): the row is never deleted, it only
     * stops being active, and it stays queryable through
     * {@link #listEvidence} with {@code active = false}. A link that is
     * already inactive is a conflict, not a silent no-op.
     */
    public EvidenceLinkView deactivateEvidenceLink(UUID linkId, UUID actor) {
        if (linkId == null || actor == null) {
            throw new ApiException.Validation("O vínculo e quem o desativa são obrigatórios.",
                    List.of(new ApiException.FieldError("linkId", "required",
                            "O vínculo e quem o desativa são obrigatórios.")), List.of());
        }
        return inTx(c -> {
            boolean active;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT active FROM " + t("cert_evidence_link") + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, linkId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("O vínculo de evidência não existe para este tenant.");
                    }
                    active = rs.getBoolean(1);
                }
            }
            if (!active) {
                throw new ApiException.Conflict("evidence-link-exists",
                        "O vínculo de evidência já está desativado.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_evidence_link")
                            + " SET active = FALSE, deactivated_at = NOW(), deactivated_by = ?,"
                            + " updated_at = NOW(), updated_by = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actor);
                ps.setObject(2, actor);
                ps.setObject(3, tenantId);
                ps.setObject(4, linkId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, clause_map_id, evidence_kind, evidence_id, evidence_ref, linked_by, linked_at, active"
                            + " FROM " + t("cert_evidence_link") + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, linkId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new EvidenceLinkView(uuid(rs, "id"), uuid(rs, "clause_map_id"),
                            EvidenceKind.from(rs.getString("evidence_kind")), uuid(rs, "evidence_id"),
                            rs.getString("evidence_ref"), uuidOrNull(rs, "linked_by"),
                            timestamp(rs, "linked_at"), rs.getBoolean("active"));
                }
            }
        });
    }

    /** The other side of the many-to-many: every clause-map row one evidence satisfies. */
    public List<ClauseMapView> listEvidenceClauses(UUID evidenceId) {
        if (evidenceId == null) {
            throw new ApiException.NotFound("A evidência não existe para este tenant.");
        }
        return inTx(c -> {
            List<ClauseMapView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT m.id, m.activation_id, m.clause_id, m.clause_number, m.title_snapshot,"
                            + " m.map_status, m.frozen_at FROM " + t("cert_clause_map") + " m"
                            + " JOIN " + t("cert_evidence_link") + " l"
                            + " ON l.tenant_id = m.tenant_id AND l.clause_map_id = m.id"
                            + " WHERE m.tenant_id = ? AND l.evidence_id = ? AND l.active = TRUE"
                            + " ORDER BY m.clause_number")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, evidenceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ClauseMapView(uuid(rs, "id"), uuid(rs, "activation_id"),
                                uuid(rs, "clause_id"), rs.getString("clause_number"),
                                rs.getString("title_snapshot"), rs.getString("map_status"),
                                timestamp(rs, "frozen_at")));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Acceptance hash (public so a later front can re-verify an acceptance)
    // ------------------------------------------------------------------

    /** SHA-256 over the accepted content, hex-encoded, deterministic. */
    public static String acceptanceHash(UUID tenantId, UUID activationId, String standardCode,
                                        String edition, String scopeStatement, UUID acceptedBy) {
        String canonical = tenantId + "|" + activationId + "|" + standardCode + "|"
                + edition + "|" + scopeStatement + "|" + acceptedBy;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ------------------------------------------------------------------
    // Transaction plumbing (same shape as the work-order engine)
    // ------------------------------------------------------------------

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
                throw new IllegalStateException("Certification operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Certification operation failed: " + e.getMessage(), e);
        }
    }

    private String t(String table) {
        return schema + "." + table;
    }

    private void requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException.Validation("O código da norma é obrigatório.",
                    List.of(new ApiException.FieldError("standardCode", "required",
                            "O código da norma é obrigatório.")), List.of());
        }
    }

    private void requireScope(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new ApiException.Validation("O escopo declarado é obrigatório.",
                    List.of(new ApiException.FieldError("scopeStatement", "required",
                            "O escopo declarado é obrigatório: informe o escopo e o local da norma ligada.")), List.of());
        }
    }

    private StandardView loadStandard(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, catalog_key, code, title, summary, standard_class, area, module, agency,"
                        + " verification_seal, status, effective_from, effective_to"
                        + " FROM " + platformSchema + ".cert_standard WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A norma " + code + " não existe no catálogo.");
                }
                return readStandard(rs);
            }
        }
    }

    private UUID loadEditionId(Connection c, UUID standardId, String edition) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM " + platformSchema + ".cert_standard_edition"
                        + " WHERE standard_id = ? AND edition = ?")) {
            ps.setObject(1, standardId);
            ps.setString(2, edition);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.Validation("A edição " + edition + " não existe no catálogo para esta norma.",
                            List.of(new ApiException.FieldError("edition", "invalid",
                                    "A edição " + edition + " não existe no catálogo para esta norma.")), List.of());
                }
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private ActivationView loadActivation(Connection c, UUID activationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, standard_code, catalog_key, standard_class, edition, scope_statement, status,"
                        + " proposed_by, approved_by, accepted_by, acceptance_hash, activated_at,"
                        + " deactivated_at, deactivated_by, deactivation_reason"
                        + " FROM " + t("cert_standard_activation") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, activationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A ativação não existe para este tenant.");
                }
                return readActivation(rs);
            }
        }
    }

    private ActivationView loadActivationByCode(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, standard_code, catalog_key, standard_class, edition, scope_statement, status,"
                        + " proposed_by, approved_by, accepted_by, acceptance_hash, activated_at,"
                        + " deactivated_at, deactivated_by, deactivation_reason"
                        + " FROM " + t("cert_standard_activation") + " WHERE tenant_id = ? AND standard_code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readActivation(rs);
            }
        }
    }

    private void loadClauseMapRow(Connection c, UUID clauseMapId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM " + t("cert_clause_map") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, clauseMapId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A linha do mapa não existe para este tenant.");
                }
            }
        }
    }

    private static StandardView readStandard(ResultSet rs) throws SQLException {
        return new StandardView(uuid(rs, "id"), rs.getString("catalog_key"), rs.getString("code"),
                rs.getString("title"), rs.getString("summary"),
                StandardClass.from(rs.getString("standard_class")),
                rs.getString("area"), rs.getString("module"), rs.getString("agency"),
                rs.getString("verification_seal"), rs.getString("status"),
                date(rs, "effective_from"), date(rs, "effective_to"));
    }

    private static ActivationView readActivation(ResultSet rs) throws SQLException {
        return new ActivationView(uuid(rs, "id"), rs.getString("standard_code"), rs.getString("catalog_key"),
                StandardClass.from(rs.getString("standard_class")), rs.getString("edition"),
                rs.getString("scope_statement"),
                ActivationStatus.valueOf(rs.getString("status")),
                uuid(rs, "proposed_by"), uuidOrNull(rs, "approved_by"), uuidOrNull(rs, "accepted_by"),
                rs.getString("acceptance_hash"), timestamp(rs, "activated_at"),
                timestamp(rs, "deactivated_at"), uuidOrNull(rs, "deactivated_by"),
                rs.getString("deactivation_reason"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return (UUID) rs.getObject(column);
    }

    private static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : (UUID) value;
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}
