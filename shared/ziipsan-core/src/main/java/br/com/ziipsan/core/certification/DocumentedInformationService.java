package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.HolidayScope;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled documents, occurrences and actions (P2.25; ADR-0041).
 *
 * <p>What this engine is: the single writer of the tenant-side
 * documented-information state — controlled documents with a review and
 * acknowledgement cycle, the single occurrence record (nonconformity,
 * incident, improvement), its clause links, its bridges to protocol,
 * service order, security incident and document, its actions, and the
 * received inspection that originates an occurrence — for one tenant
 * schema, over the clause map of the P1.32 core. Every method runs in
 * one transaction, with the tenant context set on the connection first,
 * exactly like the certification core (P1.32) and the work-order engine
 * (P1.31).
 *
 * <p>What it is not: it holds no text of any standard and no sector
 * content, and it creates no "my tasks" item. The work-item contract
 * ({@link #listOpenWorkItems()}) is the read surface the P2.28 front
 * consumes when it creates {@code work_items} (platform V1_11); the
 * obligation-agenda reminder belongs to ED-98, not here.
 *
 * <p>Document cycle (on the P1.24 document infrastructure: type by
 * catalog, retention class, immutable content per version):
 * {@code DRAFT -> UNDER_REVIEW -> APPROVED -> PUBLISHED}, with a new
 * version (same code, new doc_version) superseding the published one.
 * Review is by another person (reviewer differs from author, as in the
 * P0.19 propose/approve envelope) and acknowledgement ("ciencia") is
 * recorded only for the published version, with the hash of the exact
 * content acknowledged, in the same pattern as the P1.24 acceptance.
 * Content is never rewritten: a correction is a new version.
 *
 * <p>Occurrence cycle: {@code OPEN -> IN_ANALYSIS -> ACTION_PLAN ->
 * EFFECTIVENESS_CHECK -> CLOSED} (or {@code CANCELLED}). Root cause,
 * action and effectiveness verification are required at closure only
 * when an active standard asks for them (ADR-0041 item 3): in practice,
 * when the occurrence is linked to at least one clause whose map row is
 * ACTIVE and whose activation is ACTIVE. The check is computed at
 * closure time, never stored, so deactivating a standard relaxes the
 * requirement without rewriting the occurrence. Without an active
 * link, the occurrence is a free record and closes without those
 * requirements. An inspection occurrence additionally requires, at
 * closure, its action plan, its attendance evidence and every finding
 * addressed.
 *
 * <p>Inspection response deadline: counted on the tenant calendar —
 * the tenant {@code holidays} table (V1_5) filtered by the scope the
 * caller passes, through the P1.36 {@link DeadlineCalculator} — with
 * the counting rule recorded on the inspection itself (CALENDAR or
 * BUSINESS). No holiday is ever embedded here: a holiday missing from
 * the tenant register is a business day.
 *
 * <p>Security-incident bridge: there is no security-incident table yet
 * (P3.10 creates it and resolves this bridge). Until then the link
 * carries the incident id and a reference text, and the service refuses
 * to invent an incident row — it only records the pointer.
 *
 * <p>Boundary: this package depends on nothing outside the core. In
 * particular it names no sector catalog, no sector entity and no sector
 * term — the sector content belongs to the vertical package, never to
 * this engine.
 */
public class DocumentedInformationService {

    /** Controlled-document states; see the class documentation for the cycle. */
    public enum DocumentStatus {
        DRAFT, UNDER_REVIEW, APPROVED, PUBLISHED, SUPERSEDED
    }

    /** Document confidentiality classes (management-system classification). */
    public enum Confidentiality {
        PUBLIC, INTERNAL, RESTRICTED, CONFIDENTIAL;

        /** Parses a class, refusing anything outside the four classes. */
        public static Confidentiality from(String value) {
            if (value == null || value.isBlank()) {
                throw validation("confidentiality", "required", "A classificação de sigilo é obrigatória.");
            }
            try {
                return Confidentiality.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("confidentiality", "invalid",
                        "Classificação de sigilo desconhecida: " + value + ".");
            }
        }
    }

    /** The single occurrence kinds (ADR-0041 item 4: one record, no duplication per standard). */
    public enum OccurrenceKind {
        NONCONFORMITY, INCIDENT, IMPROVEMENT;

        /** Parses a kind, refusing anything outside the three kinds. */
        public static OccurrenceKind from(String value) {
            if (value == null || value.isBlank()) {
                throw validation("kind", "required", "O tipo da ocorrência é obrigatório.");
            }
            try {
                return OccurrenceKind.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("kind", "invalid", "Tipo de ocorrência desconhecido: " + value + ".");
            }
        }
    }

    /** Where an occurrence came from; INSPECTION is registered only with its inspection record. */
    public enum OccurrenceOrigin {
        INTERNAL, AUDIT, CUSTOMER, INSPECTION, OTHER;

        /** Parses an origin, refusing anything outside the catalog. */
        public static OccurrenceOrigin from(String value) {
            if (value == null || value.isBlank()) {
                return INTERNAL;
            }
            try {
                return OccurrenceOrigin.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("origin", "invalid", "Origem de ocorrência desconhecida: " + value + ".");
            }
        }
    }

    /** Occurrence states; see the class documentation for the cycle. */
    public enum OccurrenceStatus {
        OPEN, IN_ANALYSIS, ACTION_PLAN, EFFECTIVENESS_CHECK, CLOSED, CANCELLED
    }

    /** The three inspection authorities (JOR-24: one test per origin). */
    public enum InspectionAuthority {
        REGULATORY_AGENCY, AUDIT_COURT, ENVIRONMENTAL_AGENCY;

        /** Parses an authority, refusing anything outside the three origins. */
        public static InspectionAuthority from(String value) {
            if (value == null || value.isBlank()) {
                throw validation("authorityOrigin", "required", "A origem da fiscalização é obrigatória.");
            }
            try {
                return InspectionAuthority.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("authorityOrigin", "invalid",
                        "Origem de fiscalização desconhecida: " + value + ".");
            }
        }
    }

    /** Bridge kinds of an occurrence link; exactly one target per link row. */
    public enum LinkKind {
        PROTOCOL, SERVICE_ORDER, SECURITY_INCIDENT, DOCUMENT;

        /** Parses a kind, refusing anything outside the four bridges. */
        public static LinkKind from(String value) {
            if (value == null || value.isBlank()) {
                throw validation("linkKind", "required", "O tipo do vínculo é obrigatório.");
            }
            try {
                return LinkKind.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("linkKind", "invalid", "Tipo de vínculo desconhecido: " + value + ".");
            }
        }
    }

    /** Action states. */
    public enum ActionStatus {
        OPEN, IN_PROGRESS, DONE, VERIFIED
    }

    /** Finding states (one finding per inspection-term observation). */
    public enum FindingStatus {
        OPEN, ADDRESSED
    }

    /** Attendance-evidence kinds of an inspection (DOCUMENT points at a controlled document). */
    public enum AttendanceEvidenceKind {
        DOCUMENT, RECORD, OTHER;

        /** Parses a kind, refusing anything outside the three kinds. */
        public static AttendanceEvidenceKind from(String value) {
            if (value == null || value.isBlank()) {
                throw validation("attendanceEvidenceKind", "required",
                        "O tipo da evidência de atendimento é obrigatório.");
            }
            try {
                return AttendanceEvidenceKind.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("attendanceEvidenceKind", "invalid",
                        "Tipo de evidência de atendimento desconhecido: " + value + ".");
            }
        }
    }

    public record DocumentView(UUID id, String code, String title, String docType, String retentionClass,
                               Confidentiality confidentiality, String docVersion, String contentHash,
                               DocumentStatus status, UUID authorId, UUID reviewerId, UUID approvedBy,
                               OffsetDateTime approvedAt, OffsetDateTime publishedAt, LocalDate reviewDueDate,
                               UUID supersedesId) {
    }

    public record AcknowledgementView(UUID id, UUID documentId, String docVersion, UUID userId,
                                      String contentHash, OffsetDateTime acknowledgedAt) {
    }

    public record OccurrenceView(UUID id, OccurrenceKind kind, String title, String description,
                                 OccurrenceOrigin origin, OccurrenceStatus status, String rootCause,
                                 UUID reportedBy, OffsetDateTime reportedAt,
                                 OffsetDateTime closedAt, UUID closedBy,
                                 OffsetDateTime effectivenessVerifiedAt, UUID effectivenessVerifiedBy,
                                 String effectivenessResult) {
    }

    public record ActionView(UUID id, UUID occurrenceId, String title, UUID responsibleId, LocalDate dueDate,
                             ActionStatus status, OffsetDateTime doneAt,
                             OffsetDateTime effectivenessVerifiedAt, UUID effectivenessVerifiedBy,
                             String effectivenessResult) {
    }

    public record InspectionView(UUID id, UUID occurrenceId, InspectionAuthority authorityOrigin,
                                 String termNumber, LocalDate receivedDate, int responseDays,
                                 String countingRule, LocalDate responseDueDate, String actionPlan,
                                 String attendanceEvidenceKind, UUID attendanceEvidenceId,
                                 String attendanceEvidenceRef) {
    }

    public record FindingView(UUID id, UUID inspectionId, String description, FindingStatus status,
                              String addressedEvidenceRef, OffsetDateTime addressedAt) {
    }

    public record OccurrenceClauseView(UUID id, UUID occurrenceId, UUID clauseMapId, UUID linkedBy,
                                       OffsetDateTime linkedAt) {
    }

    public record OccurrenceLinkView(UUID id, UUID occurrenceId, LinkKind linkKind, UUID protocolId,
                                     UUID serviceOrderId, UUID securityIncidentId, UUID documentId,
                                     String linkRef, UUID linkedBy, OffsetDateTime linkedAt) {
    }

    /**
     * Work-item contract consumed by P2.28 (platform V1_11
     * {@code work_items}, NOT created here). One row per open action
     * (responsible + due date) and per document review pending with a
     * reviewer: a document UNDER_REVIEW, or a PUBLISHED document whose
     * review due date has passed. {@code sourceKind} is
     * {@code CERT_ACTION} or {@code CERT_DOCUMENT_REVIEW};
     * {@code sourceId} is the action or document id; P2.28 stores the
     * row as its own item and closes it when the action is DONE or the
     * document leaves the pending state — this service never writes
     * {@code work_items} itself.
     */
    public record WorkItemView(String sourceKind, UUID sourceId, UUID occurrenceId, String title,
                               UUID responsibleId, LocalDate dueDate, String status) {
    }

    public record CreateDocumentInput(String code, String title, String docType, String retentionClass,
                                      String confidentiality, String docVersion, String contentHash) {
    }

    public record ReviewInput(UUID reviewerId) {
    }

    public record PublishInput(LocalDate reviewDueDate) {
    }

    public record CreateOccurrenceInput(String kind, String title, String description, String origin) {
    }

    public record CreateActionInput(String title, UUID responsibleId, LocalDate dueDate) {
    }

    public record RegisterInspectionInput(String kind, String title, String description,
                                          String authorityOrigin, String termNumber,
                                          LocalDate receivedDate, int responseDays,
                                          String countingRule, String holidayScope,
                                          String stateCode, String municipalityIbge,
                                          List<String> findings) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public DocumentedInformationService(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema and tenantId are required");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    // ------------------------------------------------------------------
    // Controlled documents
    // ------------------------------------------------------------------

    /** Creates a controlled document in DRAFT, with its first version. */
    public DocumentView createDocument(CreateDocumentInput input, UUID authorId) {
        if (input == null) {
            throw validation("document", "required", "O documento é obrigatório.");
        }
        String code = requireText(input.code(), "code", "O código do documento é obrigatório.");
        String title = requireText(input.title(), "title", "O título do documento é obrigatório.");
        String docType = requireText(input.docType(), "docType", "O tipo do documento é obrigatório.");
        String retentionClass = requireText(input.retentionClass(), "retentionClass",
                "A classe de retenção do documento é obrigatória.");
        Confidentiality confidentiality = Confidentiality.from(input.confidentiality());
        String docVersion = requireText(input.docVersion(), "docVersion", "A versão do documento é obrigatória.");
        String contentHash = requireHash(input.contentHash());
        if (authorId == null) {
            throw validation("authorId", "required", "O autor do documento é obrigatório.");
        }
        return inTx(c -> {
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_document")
                            + " (tenant_id, code, title, doc_type, retention_class, confidentiality,"
                            + " doc_version, content_hash, status, author_id, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, code);
                ps.setString(3, title);
                ps.setString(4, docType);
                ps.setString(5, retentionClass);
                ps.setString(6, confidentiality.name());
                ps.setString(7, docVersion);
                ps.setString(8, contentHash);
                ps.setObject(9, authorId);
                ps.setObject(10, authorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Document insert returned no id");
                    }
                    id = (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new ApiException.Conflict("document-state",
                            "O documento " + code + " já tem a versão " + docVersion + " para este tenant.");
                }
                throw e;
            }
            return loadDocument(c, id);
        });
    }

    /** Lists the tenant's controlled documents, in code and version order. */
    public List<DocumentView> listDocuments() {
        return inTx(c -> {
            List<DocumentView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, code, title, doc_type, retention_class, confidentiality, doc_version,"
                            + " content_hash, status, author_id, reviewer_id, approved_by, approved_at,"
                            + " published_at, review_due_date, supersedes_id"
                            + " FROM " + t("cert_document") + " WHERE tenant_id = ? ORDER BY code, doc_version")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readDocument(rs));
                    }
                }
            }
            return out;
        });
    }

    /** One controlled document, or 404 for another tenant's id. */
    public DocumentView getDocument(UUID documentId) {
        if (documentId == null) {
            throw validation("documentId", "required", "O documento é obrigatório.");
        }
        return inTx(c -> loadDocument(c, documentId));
    }

    /** Sends a DRAFT document to review, naming a reviewer other than the author. */
    public DocumentView submitForReview(UUID documentId, ReviewInput input, UUID actorId) {
        if (input == null || input.reviewerId() == null) {
            throw validation("reviewerId", "required", "O revisor do documento é obrigatório.");
        }
        return inTx(c -> {
            DocumentView current = loadDocument(c, documentId);
            if (current.status() != DocumentStatus.DRAFT) {
                throw new ApiException.Conflict("document-state",
                        "Só um documento em rascunho vai para revisão (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            if (input.reviewerId().equals(current.authorId())) {
                throw validation("reviewerId", "invalid", "O revisor não pode ser o autor do documento.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_document")
                            + " SET status = 'UNDER_REVIEW', reviewer_id = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, input.reviewerId());
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, documentId);
                ps.executeUpdate();
            }
            return loadDocument(c, documentId);
        });
    }

    /** Approves a document under review; only its reviewer approves, never the author. */
    public DocumentView approveDocument(UUID documentId, UUID actorId) {
        return inTx(c -> {
            DocumentView current = loadDocument(c, documentId);
            if (current.status() != DocumentStatus.UNDER_REVIEW) {
                throw new ApiException.Conflict("document-state",
                        "Só um documento em revisão pode ser aprovado (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            if (actorId == null || !actorId.equals(current.reviewerId())) {
                throw new ApiException.Forbidden("Só o revisor do documento pode aprová-lo.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_document")
                            + " SET status = 'APPROVED', approved_by = ?, approved_at = NOW(),"
                            + " updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actorId);
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, documentId);
                ps.executeUpdate();
            }
            return loadDocument(c, documentId);
        });
    }

    /** Publishes an APPROVED document, setting its next review due date (the review cycle). */
    public DocumentView publishDocument(UUID documentId, PublishInput input, UUID actorId) {
        if (input == null || input.reviewDueDate() == null) {
            throw validation("reviewDueDate", "required",
                    "A data da próxima revisão é obrigatória na publicação.");
        }
        return inTx(c -> {
            DocumentView current = loadDocument(c, documentId);
            if (current.status() != DocumentStatus.APPROVED) {
                throw new ApiException.Conflict("document-state",
                        "Só um documento aprovado pode ser publicado (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_document")
                            + " SET status = 'PUBLISHED', published_at = NOW(), review_due_date = ?,"
                            + " updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setDate(1, Date.valueOf(input.reviewDueDate()));
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, documentId);
                ps.executeUpdate();
            }
            return loadDocument(c, documentId);
        });
    }

    /**
     * Creates the next version of a PUBLISHED document: a new row with
     * the same code, the new version and the new content hash, which
     * supersedes the published row. The published row's content is
     * never rewritten — only its status moves to SUPERSEDED.
     */
    public DocumentView newVersion(UUID documentId, CreateDocumentInput input, UUID authorId) {
        if (input == null) {
            throw validation("document", "required", "O documento é obrigatório.");
        }
        String docVersion = requireText(input.docVersion(), "docVersion", "A versão do documento é obrigatória.");
        String contentHash = requireHash(input.contentHash());
        if (authorId == null) {
            throw validation("authorId", "required", "O autor do documento é obrigatório.");
        }
        return inTx(c -> {
            DocumentView current = loadDocument(c, documentId);
            if (current.status() != DocumentStatus.PUBLISHED) {
                throw new ApiException.Conflict("document-state",
                        "Só um documento publicado ganha nova versão (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            String title = input.title() == null || input.title().isBlank() ? current.title() : input.title().trim();
            String docType = input.docType() == null || input.docType().isBlank()
                    ? current.docType() : input.docType().trim();
            String retentionClass = input.retentionClass() == null || input.retentionClass().isBlank()
                    ? current.retentionClass() : input.retentionClass().trim();
            Confidentiality confidentiality = input.confidentiality() == null || input.confidentiality().isBlank()
                    ? current.confidentiality() : Confidentiality.from(input.confidentiality());
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_document")
                            + " (tenant_id, code, title, doc_type, retention_class, confidentiality,"
                            + " doc_version, content_hash, status, author_id, supersedes_id, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, current.code());
                ps.setString(3, title);
                ps.setString(4, docType);
                ps.setString(5, retentionClass);
                ps.setString(6, confidentiality.name());
                ps.setString(7, docVersion);
                ps.setString(8, contentHash);
                ps.setObject(9, authorId);
                ps.setObject(10, documentId);
                ps.setObject(11, authorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Document version insert returned no id");
                    }
                    id = (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new ApiException.Conflict("document-state",
                            "O documento " + current.code() + " já tem a versão " + docVersion
                                    + " para este tenant.");
                }
                throw e;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_document")
                            + " SET status = 'SUPERSEDED', updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, authorId);
                ps.setObject(2, tenantId);
                ps.setObject(3, documentId);
                ps.executeUpdate();
            }
            return loadDocument(c, id);
        });
    }

    /**
     * Records a user's acknowledgement ("ciencia") of a PUBLISHED
     * document version. The hash must equal the version's content hash
     * — the acknowledgement points at the exact content read — and
     * re-acknowledging the same version is idempotent, returning the
     * original record, as in the P1.24 acceptance.
     */
    public AcknowledgementView acknowledgeDocument(UUID documentId, UUID userId) {
        if (userId == null) {
            throw validation("userId", "required", "O usuário da ciência é obrigatório.");
        }
        return inTx(c -> {
            DocumentView current = loadDocument(c, documentId);
            if (current.status() != DocumentStatus.PUBLISHED) {
                throw new ApiException.Conflict("document-state",
                        "Só se registra ciência de um documento publicado (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_document_acknowledgement")
                            + " (tenant_id, document_id, doc_version, user_id, content_hash, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?)"
                            + " ON CONFLICT (tenant_id, document_id, doc_version, user_id) DO NOTHING"
                            + " RETURNING id, acknowledged_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, documentId);
                ps.setString(3, current.docVersion());
                ps.setObject(4, userId);
                ps.setString(5, current.contentHash());
                ps.setObject(6, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new AcknowledgementView((UUID) rs.getObject(1), documentId,
                                current.docVersion(), userId, current.contentHash(),
                                timestamp(rs, "acknowledged_at"));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, content_hash, acknowledged_at FROM " + t("cert_document_acknowledgement")
                            + " WHERE tenant_id = ? AND document_id = ? AND doc_version = ? AND user_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, documentId);
                ps.setString(3, current.docVersion());
                ps.setObject(4, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Acknowledgement insert returned no row");
                    }
                    return new AcknowledgementView((UUID) rs.getObject(1), documentId,
                            current.docVersion(), userId, rs.getString("content_hash"),
                            timestamp(rs, "acknowledged_at"));
                }
            }
        });
    }

    /** Acknowledgements of one document version, in acknowledgement order. */
    public List<AcknowledgementView> listAcknowledgements(UUID documentId) {
        return inTx(c -> {
            loadDocument(c, documentId);
            List<AcknowledgementView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, document_id, doc_version, user_id, content_hash, acknowledged_at"
                            + " FROM " + t("cert_document_acknowledgement")
                            + " WHERE tenant_id = ? AND document_id = ? ORDER BY acknowledged_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, documentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new AcknowledgementView((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                                rs.getString("doc_version"), (UUID) rs.getObject(4),
                                rs.getString("content_hash"), timestamp(rs, "acknowledged_at")));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Occurrences
    // ------------------------------------------------------------------

    /**
     * Creates an occurrence in OPEN. An INSPECTION origin is refused
     * here: it is registered only through {@link #registerInspection},
     * which carries the term, the findings and the response deadline.
     */
    public OccurrenceView createOccurrence(CreateOccurrenceInput input, UUID reportedBy) {
        if (input == null) {
            throw validation("occurrence", "required", "A ocorrência é obrigatória.");
        }
        OccurrenceKind kind = OccurrenceKind.from(input.kind());
        String title = requireText(input.title(), "title", "O título da ocorrência é obrigatório.");
        String description = requireText(input.description(), "description",
                "A descrição da ocorrência é obrigatória.");
        OccurrenceOrigin origin = OccurrenceOrigin.from(input.origin());
        if (origin == OccurrenceOrigin.INSPECTION) {
            throw validation("origin", "invalid",
                    "Fiscalização se registra com termo e constatações, pela rota de fiscalizações.");
        }
        if (reportedBy == null) {
            throw validation("reportedBy", "required", "Quem registra a ocorrência é obrigatório.");
        }
        return inTx(c -> {
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_occurrence")
                            + " (tenant_id, kind, title, description, origin, status, reported_by, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, kind.name());
                ps.setString(3, title);
                ps.setString(4, description);
                ps.setString(5, origin.name());
                ps.setObject(6, reportedBy);
                ps.setObject(7, reportedBy);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Occurrence insert returned no id");
                    }
                    id = (UUID) rs.getObject(1);
                }
            }
            return loadOccurrence(c, id);
        });
    }

    /** Lists the tenant's occurrences, most recent first. */
    public List<OccurrenceView> listOccurrences() {
        return inTx(c -> {
            List<OccurrenceView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, kind, title, description, origin, status, root_cause, reported_by,"
                            + " reported_at, closed_at, closed_by, effectiveness_verified_at,"
                            + " effectiveness_verified_by, effectiveness_result"
                            + " FROM " + t("cert_occurrence")
                            + " WHERE tenant_id = ? ORDER BY reported_at DESC, id")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readOccurrence(rs));
                    }
                }
            }
            return out;
        });
    }

    /** One occurrence, or 404 for another tenant's id. */
    public OccurrenceView getOccurrence(UUID occurrenceId) {
        if (occurrenceId == null) {
            throw validation("occurrenceId", "required", "A ocorrência é obrigatória.");
        }
        return inTx(c -> loadOccurrence(c, occurrenceId));
    }

    /** Moves an OPEN occurrence to IN_ANALYSIS. */
    public OccurrenceView startAnalysis(UUID occurrenceId, UUID actorId) {
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            requireOpenCycle(current, OccurrenceStatus.OPEN, "iniciar a análise");
            updateOccurrenceStatus(c, occurrenceId, "IN_ANALYSIS", actorId);
            return loadOccurrence(c, occurrenceId);
        });
    }

    /** Records the root cause of an occurrence still in its open cycle. */
    public OccurrenceView setRootCause(UUID occurrenceId, String rootCause, UUID actorId) {
        String cause = requireText(rootCause, "rootCause", "A causa raiz é obrigatória.");
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            requireOpenCycle(current, null, "registrar a causa raiz");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_occurrence")
                            + " SET root_cause = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, cause);
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, occurrenceId);
                ps.executeUpdate();
            }
            return loadOccurrence(c, occurrenceId);
        });
    }

    /** Moves an occurrence to ACTION_PLAN (its actions are then tracked). */
    public OccurrenceView setActionPlanState(UUID occurrenceId, UUID actorId) {
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            requireOpenCycle(current, null, "levar ao plano de ação");
            updateOccurrenceStatus(c, occurrenceId, "ACTION_PLAN", actorId);
            return loadOccurrence(c, occurrenceId);
        });
    }

    /**
     * Records the effectiveness verification of an occurrence, moving
     * it to EFFECTIVENESS_CHECK. Only the verification record is set
     * here; closure itself is {@link #closeOccurrence}, which re-checks
     * every requirement.
     */
    public OccurrenceView verifyEffectiveness(UUID occurrenceId, String result, UUID actorId) {
        String outcome = requireText(result, "effectivenessResult",
                "O resultado da verificação de eficácia é obrigatório.");
        if (actorId == null) {
            throw validation("effectivenessVerifiedBy", "required",
                    "Quem verifica a eficácia é obrigatório.");
        }
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            requireOpenCycle(current, null, "verificar a eficácia");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_occurrence")
                            + " SET status = 'EFFECTIVENESS_CHECK', effectiveness_verified_at = NOW(),"
                            + " effectiveness_verified_by = ?, effectiveness_result = ?,"
                            + " updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actorId);
                ps.setString(2, outcome);
                ps.setObject(3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, occurrenceId);
                ps.executeUpdate();
            }
            return loadOccurrence(c, occurrenceId);
        });
    }

    /**
     * Pure closure rule, unit-tested without a database: the blockers
     * of closing an occurrence. With an active clause link, root cause,
     * at least one done action and the occurrence's effectiveness
     * verification are all required; an inspection occurrence
     * additionally requires its action plan, its attendance evidence
     * and zero open findings. Without either, nothing blocks closure.
     */
    public static List<String> closeBlockers(boolean hasActiveClauseLink, boolean isInspection,
                                             boolean hasRootCause, int doneActions,
                                             boolean effectivenessVerified,
                                             boolean hasActionPlan, boolean hasAttendanceEvidence,
                                             int openFindings) {
        List<String> blockers = new ArrayList<>();
        if (hasActiveClauseLink) {
            if (!hasRootCause) {
                blockers.add("rootCause");
            }
            if (doneActions < 1) {
                blockers.add("action");
            }
            if (!effectivenessVerified) {
                blockers.add("effectiveness");
            }
        }
        if (isInspection) {
            if (!hasActionPlan) {
                blockers.add("inspectionActionPlan");
            }
            if (!hasAttendanceEvidence) {
                blockers.add("inspectionAttendanceEvidence");
            }
            if (openFindings > 0) {
                blockers.add("inspectionOpenFindings");
            }
        }
        return blockers;
    }

    /** Closes an occurrence, enforcing {@link #closeBlockers} for its actual state. */
    public OccurrenceView closeOccurrence(UUID occurrenceId, UUID actorId) {
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            if (current.status() == OccurrenceStatus.CLOSED) {
                throw new ApiException.Conflict("occurrence-state", "A ocorrência já está encerrada.");
            }
            if (current.status() == OccurrenceStatus.CANCELLED) {
                throw new ApiException.Conflict("occurrence-state", "A ocorrência cancelada não pode ser encerrada.");
            }
            boolean hasActiveClauseLink = hasActiveClauseLink(c, occurrenceId);
            InspectionView inspection = loadInspectionByOccurrenceOrNull(c, occurrenceId);
            int doneActions = countActionsByStatus(c, occurrenceId, "DONE", "VERIFIED");
            int openFindings = inspection == null ? 0 : countOpenFindings(c, inspection.id());
            List<String> blockers = closeBlockers(hasActiveClauseLink, inspection != null,
                    current.rootCause() != null && !current.rootCause().isBlank(), doneActions,
                    current.effectivenessVerifiedAt() != null,
                    inspection == null || (inspection.actionPlan() != null && !inspection.actionPlan().isBlank()),
                    inspection == null || inspection.attendanceEvidenceKind() != null,
                    openFindings);
            if (!blockers.isEmpty()) {
                List<ApiException.FieldError> errors = new ArrayList<>();
                for (String blocker : blockers) {
                    errors.add(switch (blocker) {
                        case "rootCause" -> new ApiException.FieldError("rootCause", "required",
                                "A norma ativa exige a causa raiz para encerrar a ocorrência.");
                        case "action" -> new ApiException.FieldError("action", "required",
                                "A norma ativa exige ao menos uma ação concluída para encerrar a ocorrência.");
                        case "effectiveness" -> new ApiException.FieldError("effectiveness", "required",
                                "A norma ativa exige a verificação de eficácia para encerrar a ocorrência.");
                        case "inspectionActionPlan" -> new ApiException.FieldError("actionPlan", "required",
                                "A fiscalização exige o plano de ação para encerrar a ocorrência.");
                        case "inspectionAttendanceEvidence" -> new ApiException.FieldError(
                                "attendanceEvidence", "required",
                                "A fiscalização exige a evidência de atendimento para encerrar a ocorrência.");
                        default -> new ApiException.FieldError("findings", "required",
                                "A fiscalização exige todas as constatações atendidas para encerrar a ocorrência.");
                    });
                }
                throw new ApiException.Validation("A ocorrência não pode ser encerrada.", errors, List.of());
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_occurrence")
                            + " SET status = 'CLOSED', closed_at = NOW(), closed_by = ?,"
                            + " updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actorId);
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, occurrenceId);
                ps.executeUpdate();
            }
            return loadOccurrence(c, occurrenceId);
        });
    }

    /** Cancels an occurrence still in its open cycle; nothing is deleted. */
    public OccurrenceView cancelOccurrence(UUID occurrenceId, UUID actorId) {
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            requireOpenCycle(current, null, "cancelar");
            updateOccurrenceStatus(c, occurrenceId, "CANCELLED", actorId);
            return loadOccurrence(c, occurrenceId);
        });
    }

    /** Links an occurrence to a clause-map row (one record, several clauses). */
    public OccurrenceClauseView linkClause(UUID occurrenceId, UUID clauseMapId, UUID actorId) {
        if (clauseMapId == null) {
            throw validation("clauseMapId", "required", "A linha do mapa de cláusulas é obrigatória.");
        }
        return inTx(c -> {
            loadOccurrence(c, occurrenceId);
            loadClauseMapRow(c, clauseMapId);
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_occurrence_clause")
                            + " (tenant_id, occurrence_id, clause_map_id, linked_by, created_by)"
                            + " VALUES (?, ?, ?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                ps.setObject(3, clauseMapId);
                ps.setObject(4, actorId);
                ps.setObject(5, actorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Occurrence clause insert returned no id");
                    }
                    id = (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new ApiException.Conflict("occurrence-state",
                            "A ocorrência já está vinculada a esta linha do mapa.");
                }
                throw e;
            }
            return loadOccurrenceClause(c, id);
        });
    }

    /** Clause links of one occurrence. */
    public List<OccurrenceClauseView> listOccurrenceClauses(UUID occurrenceId) {
        return inTx(c -> {
            loadOccurrence(c, occurrenceId);
            List<OccurrenceClauseView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, occurrence_id, clause_map_id, linked_by, linked_at"
                            + " FROM " + t("cert_occurrence_clause")
                            + " WHERE tenant_id = ? AND occurrence_id = ? ORDER BY linked_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new OccurrenceClauseView((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                                (UUID) rs.getObject(3), (UUID) rs.getObject(4),
                                timestamp(rs, "linked_at")));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Links an occurrence to a protocol, a service order, a security
     * incident or a controlled document. Protocol and service-order
     * targets must exist for this tenant (foreign keys enforce it too);
     * a security-incident target is only a pointer until P3.10, so it
     * is checked for presence, not for existence.
     */
    public OccurrenceLinkView linkTarget(UUID occurrenceId, LinkKind kind, UUID protocolId,
                                         UUID serviceOrderId, UUID securityIncidentId, UUID documentId,
                                         String linkRef, UUID actorId) {
        if (kind == null) {
            throw validation("linkKind", "required", "O tipo do vínculo é obrigatório.");
        }
        return inTx(c -> {
            loadOccurrence(c, occurrenceId);
            switch (kind) {
                case PROTOCOL -> {
                    if (protocolId == null) {
                        throw validation("protocolId", "required", "O protocolo é obrigatório.");
                    }
                    requireProtocol(c, protocolId);
                }
                case SERVICE_ORDER -> {
                    if (serviceOrderId == null) {
                        throw validation("serviceOrderId", "required", "A ordem de serviço é obrigatória.");
                    }
                    requireServiceOrder(c, serviceOrderId);
                }
                case SECURITY_INCIDENT -> {
                    if (securityIncidentId == null) {
                        throw validation("securityIncidentId", "required",
                                "O identificador do incidente de segurança é obrigatório.");
                    }
                }
                case DOCUMENT -> {
                    if (documentId == null) {
                        throw validation("documentId", "required", "O documento é obrigatório.");
                    }
                    loadDocument(c, documentId);
                }
            }
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_occurrence_link")
                            + " (tenant_id, occurrence_id, link_kind, protocol_id, service_order_id,"
                            + " security_incident_id, document_id, link_ref, linked_by, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                ps.setString(3, kind.name());
                setUuidOrNull(ps, 4, protocolId);
                setUuidOrNull(ps, 5, serviceOrderId);
                setUuidOrNull(ps, 6, securityIncidentId);
                setUuidOrNull(ps, 7, documentId);
                ps.setString(8, linkRef == null || linkRef.isBlank() ? null : linkRef.trim());
                ps.setObject(9, actorId);
                ps.setObject(10, actorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Occurrence link insert returned no id");
                    }
                    id = (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new ApiException.Conflict("occurrence-state",
                            "A ocorrência já está vinculada a este alvo.");
                }
                throw e;
            }
            return loadOccurrenceLink(c, id);
        });
    }

    /** Bridges of one occurrence. */
    public List<OccurrenceLinkView> listOccurrenceLinks(UUID occurrenceId) {
        return inTx(c -> {
            loadOccurrence(c, occurrenceId);
            List<OccurrenceLinkView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, occurrence_id, link_kind, protocol_id, service_order_id,"
                            + " security_incident_id, document_id, link_ref, linked_by, linked_at"
                            + " FROM " + t("cert_occurrence_link")
                            + " WHERE tenant_id = ? AND occurrence_id = ? ORDER BY linked_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readOccurrenceLink(rs));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Adds an action (responsible + due date) to an occurrence still in its open cycle. */
    public ActionView addAction(UUID occurrenceId, CreateActionInput input, UUID actorId) {
        if (input == null) {
            throw validation("action", "required", "A ação é obrigatória.");
        }
        String title = requireText(input.title(), "title", "O título da ação é obrigatório.");
        if (input.responsibleId() == null) {
            throw validation("responsibleId", "required", "O responsável pela ação é obrigatório.");
        }
        if (input.dueDate() == null) {
            throw validation("dueDate", "required", "O prazo da ação é obrigatório.");
        }
        return inTx(c -> {
            OccurrenceView current = loadOccurrence(c, occurrenceId);
            requireOpenCycle(current, null, "incluir ação");
            UUID id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_action")
                            + " (tenant_id, occurrence_id, title, responsible_id, due_date, status, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, 'OPEN', ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                ps.setString(3, title);
                ps.setObject(4, input.responsibleId());
                ps.setDate(5, Date.valueOf(input.dueDate()));
                ps.setObject(6, actorId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Action insert returned no id");
                    }
                    id = (UUID) rs.getObject(1);
                }
            }
            return loadAction(c, id);
        });
    }

    /** Actions of one occurrence, in due-date order. */
    public List<ActionView> listActions(UUID occurrenceId) {
        return inTx(c -> {
            loadOccurrence(c, occurrenceId);
            List<ActionView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, occurrence_id, title, responsible_id, due_date, status, done_at,"
                            + " effectiveness_verified_at, effectiveness_verified_by, effectiveness_result"
                            + " FROM " + t("cert_action")
                            + " WHERE tenant_id = ? AND occurrence_id = ? ORDER BY due_date, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readAction(rs));
                    }
                }
            }
            return out;
        });
    }

    /** Marks an action DONE (OPEN or IN_PROGRESS only). */
    public ActionView markActionDone(UUID actionId, UUID actorId) {
        return inTx(c -> {
            ActionView current = loadAction(c, actionId);
            if (current.status() != ActionStatus.OPEN && current.status() != ActionStatus.IN_PROGRESS) {
                throw new ApiException.Conflict("occurrence-state",
                        "Só uma ação aberta ou em andamento pode ser concluída (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_action")
                            + " SET status = 'DONE', done_at = NOW(), updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actorId);
                ps.setObject(2, tenantId);
                ps.setObject(3, actionId);
                ps.executeUpdate();
            }
            return loadAction(c, actionId);
        });
    }

    /** Moves an OPEN action to IN_PROGRESS. */
    public ActionView startAction(UUID actionId, UUID actorId) {
        return inTx(c -> {
            ActionView current = loadAction(c, actionId);
            if (current.status() != ActionStatus.OPEN) {
                throw new ApiException.Conflict("occurrence-state",
                        "Só uma ação aberta pode ser iniciada (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_action")
                            + " SET status = 'IN_PROGRESS', updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actorId);
                ps.setObject(2, tenantId);
                ps.setObject(3, actionId);
                ps.executeUpdate();
            }
            return loadAction(c, actionId);
        });
    }

    /** Records the effectiveness verification of one DONE action (it becomes VERIFIED). */
    public ActionView verifyActionEffectiveness(UUID actionId, String result, UUID actorId) {
        String outcome = requireText(result, "effectivenessResult",
                "O resultado da verificação de eficácia é obrigatório.");
        if (actorId == null) {
            throw validation("effectivenessVerifiedBy", "required",
                    "Quem verifica a eficácia é obrigatório.");
        }
        return inTx(c -> {
            ActionView current = loadAction(c, actionId);
            if (current.status() != ActionStatus.DONE) {
                throw new ApiException.Conflict("occurrence-state",
                        "Só uma ação concluída tem a eficácia verificada (estado atual: "
                                + current.status().name().toLowerCase() + ").");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_action")
                            + " SET status = 'VERIFIED', effectiveness_verified_at = NOW(),"
                            + " effectiveness_verified_by = ?, effectiveness_result = ?,"
                            + " updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, actorId);
                ps.setString(2, outcome);
                ps.setObject(3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, actionId);
                ps.executeUpdate();
            }
            return loadAction(c, actionId);
        });
    }

    // ------------------------------------------------------------------
    // Work-item contract for P2.28 (read-only here)
    // ------------------------------------------------------------------

    /**
     * Open work items of this tenant, in the contract P2.28 consumes:
     * open or in-progress actions (responsible + due date) and
     * document reviews pending with a reviewer (UNDER_REVIEW documents
     * and PUBLISHED documents past their review due date). This method
     * reads only — P2.28 owns the {@code work_items} rows.
     */
    public List<WorkItemView> listOpenWorkItems() {
        return inTx(c -> {
            List<WorkItemView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, occurrence_id, title, responsible_id, due_date, status"
                            + " FROM " + t("cert_action")
                            + " WHERE tenant_id = ? AND status IN ('OPEN', 'IN_PROGRESS')"
                            + " ORDER BY due_date, id")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new WorkItemView("CERT_ACTION", (UUID) rs.getObject(1),
                                (UUID) rs.getObject(2), rs.getString("title"),
                                (UUID) rs.getObject(4), date(rs, "due_date"), rs.getString("status")));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, code, title, reviewer_id, review_due_date, status"
                            + " FROM " + t("cert_document")
                            + " WHERE tenant_id = ?"
                            + " AND ((status = 'UNDER_REVIEW')"
                            + " OR (status = 'PUBLISHED' AND review_due_date IS NOT NULL"
                            + " AND review_due_date <= CURRENT_DATE))"
                            + " ORDER BY review_due_date NULLS FIRST, id")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new WorkItemView("CERT_DOCUMENT_REVIEW", (UUID) rs.getObject(1), null,
                                rs.getString("code") + " — " + rs.getString("title"),
                                (UUID) rs.getObject(4), date(rs, "review_due_date"),
                                rs.getString("status")));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Inspections (received oversight, origin of an occurrence — JOR-24)
    // ------------------------------------------------------------------

    /**
     * Registers a received inspection as the origin of a new
     * occurrence: the occurrence is created with origin INSPECTION, the
     * inspection row carries the authority, the term and the findings,
     * and the response deadline is counted on the tenant calendar
     * (tenant {@code holidays} rows, filtered by the given scope)
     * through the P1.36 deadline engine, under the recorded rule.
     */
    public InspectionView registerInspection(RegisterInspectionInput input, UUID reportedBy) {
        if (input == null) {
            throw validation("inspection", "required", "A fiscalização é obrigatória.");
        }
        OccurrenceKind kind = OccurrenceKind.from(input.kind());
        String title = requireText(input.title(), "title", "O título da ocorrência é obrigatório.");
        String description = requireText(input.description(), "description",
                "A descrição da ocorrência é obrigatória.");
        InspectionAuthority authority = InspectionAuthority.from(input.authorityOrigin());
        String termNumber = requireText(input.termNumber(), "termNumber", "O termo da fiscalização é obrigatório.");
        if (input.receivedDate() == null) {
            throw validation("receivedDate", "required", "A data de recebimento da fiscalização é obrigatória.");
        }
        if (input.responseDays() <= 0) {
            throw validation("responseDays", "invalid", "O prazo de resposta deve ser maior que zero.");
        }
        DeadlineCountingRule rule;
        try {
            rule = input.countingRule() == null || input.countingRule().isBlank()
                    ? DeadlineCountingRule.CALENDAR
                    : DeadlineCountingRule.valueOf(input.countingRule().trim());
        } catch (IllegalArgumentException e) {
            throw validation("countingRule", "invalid",
                    "Regra de contagem desconhecida: " + input.countingRule() + ".");
        }
        HolidayScope scope;
        try {
            scope = input.holidayScope() == null || input.holidayScope().isBlank()
                    ? HolidayScope.NATIONAL
                    : HolidayScope.valueOf(input.holidayScope().trim());
        } catch (IllegalArgumentException e) {
            throw validation("holidayScope", "invalid",
                    "Escopo de feriado desconhecido: " + input.holidayScope() + ".");
        }
        if (reportedBy == null) {
            throw validation("reportedBy", "required", "Quem registra a fiscalização é obrigatório.");
        }
        return inTx(c -> {
            Set<LocalDate> holidays = loadTenantHolidayDates(c, scope, input.stateCode(), input.municipalityIbge());
            LocalDate dueDate = DeadlineCalculator.computeDueDate(
                    input.receivedDate(), input.responseDays(), rule, holidays);
            UUID occurrenceId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_occurrence")
                            + " (tenant_id, kind, title, description, origin, status, reported_by, created_by)"
                            + " VALUES (?, ?, ?, ?, 'INSPECTION', 'OPEN', ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, kind.name());
                ps.setString(3, title);
                ps.setString(4, description);
                ps.setObject(5, reportedBy);
                ps.setObject(6, reportedBy);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Inspection occurrence insert returned no id");
                    }
                    occurrenceId = (UUID) rs.getObject(1);
                }
            }
            UUID inspectionId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("cert_inspection")
                            + " (tenant_id, occurrence_id, authority_origin, term_number, received_date,"
                            + " response_days, counting_rule, response_due_date, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, occurrenceId);
                ps.setString(3, authority.name());
                ps.setString(4, termNumber);
                ps.setDate(5, Date.valueOf(input.receivedDate()));
                ps.setInt(6, input.responseDays());
                ps.setString(7, rule.name());
                ps.setDate(8, Date.valueOf(dueDate));
                ps.setObject(9, reportedBy);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Inspection insert returned no id");
                    }
                    inspectionId = (UUID) rs.getObject(1);
                }
            }
            if (input.findings() != null) {
                for (String finding : input.findings()) {
                    String text = finding == null ? null : finding.trim();
                    if (text == null || text.isEmpty()) {
                        throw validation("findings", "required", "Cada constatação precisa de descrição.");
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO " + t("cert_inspection_finding")
                                    + " (tenant_id, inspection_id, description, status, created_by)"
                                    + " VALUES (?, ?, ?, 'OPEN', ?)")) {
                        ps.setObject(1, tenantId);
                        ps.setObject(2, inspectionId);
                        ps.setString(3, text);
                        ps.setObject(4, reportedBy);
                        ps.executeUpdate();
                    }
                }
            }
            return loadInspection(c, inspectionId);
        });
    }

    /** The inspection of one occurrence, or 404 when the occurrence has none. */
    public InspectionView getInspectionByOccurrence(UUID occurrenceId) {
        return inTx(c -> {
            loadOccurrence(c, occurrenceId);
            InspectionView inspection = loadInspectionByOccurrenceOrNull(c, occurrenceId);
            if (inspection == null) {
                throw new ApiException.NotFound("A ocorrência não tem fiscalização registrada.");
            }
            return inspection;
        });
    }

    /** Findings of one inspection, in registration order. */
    public List<FindingView> listFindings(UUID inspectionId) {
        return inTx(c -> {
            loadInspection(c, inspectionId);
            List<FindingView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, inspection_id, description, status, addressed_evidence_ref, addressed_at"
                            + " FROM " + t("cert_inspection_finding")
                            + " WHERE tenant_id = ? AND inspection_id = ? ORDER BY created_at, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, inspectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readFinding(rs));
                    }
                }
            }
            return out;
        });
    }

    /** Marks one finding ADDRESSED, with its attendance-evidence reference. */
    public FindingView addressFinding(UUID findingId, String evidenceRef, UUID actorId) {
        String ref = requireText(evidenceRef, "addressedEvidenceRef",
                "A referência da evidência de atendimento é obrigatória.");
        return inTx(c -> {
            FindingView current = loadFinding(c, findingId);
            if (current.status() != FindingStatus.OPEN) {
                throw new ApiException.Conflict("occurrence-state", "A constatação já foi atendida.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_inspection_finding")
                            + " SET status = 'ADDRESSED', addressed_evidence_ref = ?, addressed_at = NOW(),"
                            + " updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, ref);
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, findingId);
                ps.executeUpdate();
            }
            return loadFinding(c, findingId);
        });
    }

    /** Records the inspection's action plan (required at closure of an inspection occurrence). */
    public InspectionView setInspectionActionPlan(UUID inspectionId, String actionPlan, UUID actorId) {
        String plan = requireText(actionPlan, "actionPlan", "O plano de ação é obrigatório.");
        return inTx(c -> {
            loadInspection(c, inspectionId);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_inspection")
                            + " SET action_plan = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, plan);
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, inspectionId);
                ps.executeUpdate();
            }
            return loadInspection(c, inspectionId);
        });
    }

    /** Records the inspection's attendance evidence (required at closure of an inspection occurrence). */
    public InspectionView setInspectionAttendanceEvidence(UUID inspectionId, String evidenceKind,
                                                          UUID evidenceId, String evidenceRef, UUID actorId) {
        AttendanceEvidenceKind kind = AttendanceEvidenceKind.from(evidenceKind);
        return inTx(c -> {
            InspectionView current = loadInspection(c, inspectionId);
            if (kind == AttendanceEvidenceKind.DOCUMENT) {
                if (evidenceId == null) {
                    throw validation("attendanceEvidenceId", "required",
                            "A evidência de atendimento do tipo documento exige o documento.");
                }
                loadDocument(c, evidenceId);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("cert_inspection")
                            + " SET attendance_evidence_kind = ?, attendance_evidence_id = ?,"
                            + " attendance_evidence_ref = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, kind.name());
                setUuidOrNull(ps, 2, evidenceId);
                ps.setString(3, evidenceRef == null || evidenceRef.isBlank() ? null : evidenceRef.trim());
                ps.setObject(4, actorId);
                ps.setObject(5, tenantId);
                ps.setObject(6, inspectionId);
                ps.executeUpdate();
            }
            return loadInspection(c, inspectionId);
        });
    }

    // ------------------------------------------------------------------
    // Loaders and readers
    // ------------------------------------------------------------------

    private DocumentView loadDocument(Connection c, UUID documentId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, code, title, doc_type, retention_class, confidentiality, doc_version,"
                        + " content_hash, status, author_id, reviewer_id, approved_by, approved_at,"
                        + " published_at, review_due_date, supersedes_id"
                        + " FROM " + t("cert_document") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O documento não existe para este tenant.");
                }
                return readDocument(rs);
            }
        }
    }

    private static DocumentView readDocument(ResultSet rs) throws SQLException {
        return new DocumentView((UUID) rs.getObject(1), rs.getString("code"), rs.getString("title"),
                rs.getString("doc_type"), rs.getString("retention_class"),
                Confidentiality.valueOf(rs.getString("confidentiality")), rs.getString("doc_version"),
                rs.getString("content_hash"), DocumentStatus.valueOf(rs.getString("status")),
                (UUID) rs.getObject("author_id"), uuidOrNull(rs, "reviewer_id"),
                uuidOrNull(rs, "approved_by"), timestamp(rs, "approved_at"),
                timestamp(rs, "published_at"), date(rs, "review_due_date"),
                uuidOrNull(rs, "supersedes_id"));
    }

    private OccurrenceView loadOccurrence(Connection c, UUID occurrenceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, kind, title, description, origin, status, root_cause, reported_by,"
                        + " reported_at, closed_at, closed_by, effectiveness_verified_at,"
                        + " effectiveness_verified_by, effectiveness_result"
                        + " FROM " + t("cert_occurrence") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, occurrenceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A ocorrência não existe para este tenant.");
                }
                return readOccurrence(rs);
            }
        }
    }

    private static OccurrenceView readOccurrence(ResultSet rs) throws SQLException {
        return new OccurrenceView((UUID) rs.getObject(1), OccurrenceKind.valueOf(rs.getString("kind")),
                rs.getString("title"), rs.getString("description"),
                OccurrenceOrigin.valueOf(rs.getString("origin")),
                OccurrenceStatus.valueOf(rs.getString("status")), rs.getString("root_cause"),
                (UUID) rs.getObject("reported_by"), timestamp(rs, "reported_at"),
                timestamp(rs, "closed_at"), uuidOrNull(rs, "closed_by"),
                timestamp(rs, "effectiveness_verified_at"), uuidOrNull(rs, "effectiveness_verified_by"),
                rs.getString("effectiveness_result"));
    }

    private void updateOccurrenceStatus(Connection c, UUID occurrenceId, String status, UUID actorId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + t("cert_occurrence")
                        + " SET status = ?, updated_by = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, actorId);
            ps.setObject(3, tenantId);
            ps.setObject(4, occurrenceId);
            ps.executeUpdate();
        }
    }

    private void requireOpenCycle(OccurrenceView current, OccurrenceStatus expected, String action)
            throws SQLException {
        if (current.status() == OccurrenceStatus.CLOSED || current.status() == OccurrenceStatus.CANCELLED) {
            throw new ApiException.Conflict("occurrence-state",
                    "A ocorrência " + current.status().name().toLowerCase() + " não aceita " + action + ".");
        }
        if (expected != null && current.status() != expected) {
            throw new ApiException.Conflict("occurrence-state",
                    "A ocorrência precisa estar " + expected.name().toLowerCase() + " para " + action
                            + " (estado atual: " + current.status().name().toLowerCase() + ").");
        }
    }

    private boolean hasActiveClauseLink(Connection c, UUID occurrenceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM " + t("cert_occurrence_clause") + " oc"
                        + " JOIN " + t("cert_clause_map") + " cm ON cm.tenant_id = oc.tenant_id"
                        + " AND cm.id = oc.clause_map_id"
                        + " JOIN " + t("cert_standard_activation") + " a ON a.tenant_id = cm.tenant_id"
                        + " AND a.id = cm.activation_id"
                        + " WHERE oc.tenant_id = ? AND oc.occurrence_id = ?"
                        + " AND cm.map_status = 'ACTIVE' AND a.status = 'ACTIVE' LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, occurrenceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
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

    private OccurrenceClauseView loadOccurrenceClause(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, occurrence_id, clause_map_id, linked_by, linked_at"
                        + " FROM " + t("cert_occurrence_clause") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O vínculo de cláusula não existe para este tenant.");
                }
                return new OccurrenceClauseView((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                        (UUID) rs.getObject(3), (UUID) rs.getObject(4), timestamp(rs, "linked_at"));
            }
        }
    }

    private void requireProtocol(Connection c, UUID protocolId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM " + t("service_protocols") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, protocolId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O protocolo não existe para este tenant.");
                }
            }
        }
    }

    private void requireServiceOrder(Connection c, UUID serviceOrderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM " + t("service_orders") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, serviceOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A ordem de serviço não existe para este tenant.");
                }
            }
        }
    }

    private OccurrenceLinkView loadOccurrenceLink(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, occurrence_id, link_kind, protocol_id, service_order_id,"
                        + " security_incident_id, document_id, link_ref, linked_by, linked_at"
                        + " FROM " + t("cert_occurrence_link") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O vínculo não existe para este tenant.");
                }
                return readOccurrenceLink(rs);
            }
        }
    }

    private static OccurrenceLinkView readOccurrenceLink(ResultSet rs) throws SQLException {
        return new OccurrenceLinkView((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                LinkKind.valueOf(rs.getString("link_kind")), uuidOrNull(rs, "protocol_id"),
                uuidOrNull(rs, "service_order_id"), uuidOrNull(rs, "security_incident_id"),
                uuidOrNull(rs, "document_id"), rs.getString("link_ref"),
                (UUID) rs.getObject("linked_by"), timestamp(rs, "linked_at"));
    }

    private ActionView loadAction(Connection c, UUID actionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, occurrence_id, title, responsible_id, due_date, status, done_at,"
                        + " effectiveness_verified_at, effectiveness_verified_by, effectiveness_result"
                        + " FROM " + t("cert_action") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, actionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A ação não existe para este tenant.");
                }
                return readAction(rs);
            }
        }
    }

    private static ActionView readAction(ResultSet rs) throws SQLException {
        return new ActionView((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString("title"),
                (UUID) rs.getObject(4), date(rs, "due_date"), ActionStatus.valueOf(rs.getString("status")),
                timestamp(rs, "done_at"), timestamp(rs, "effectiveness_verified_at"),
                uuidOrNull(rs, "effectiveness_verified_by"), rs.getString("effectiveness_result"));
    }

    private int countActionsByStatus(Connection c, UUID occurrenceId, String... statuses) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM " + t("cert_action") + " WHERE tenant_id = ? AND occurrence_id = ? AND status IN (");
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setObject(1, tenantId);
            ps.setObject(2, occurrenceId);
            for (int i = 0; i < statuses.length; i++) {
                ps.setString(3 + i, statuses[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private InspectionView loadInspection(Connection c, UUID inspectionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, occurrence_id, authority_origin, term_number, received_date, response_days,"
                        + " counting_rule, response_due_date, action_plan, attendance_evidence_kind,"
                        + " attendance_evidence_id, attendance_evidence_ref"
                        + " FROM " + t("cert_inspection") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, inspectionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A fiscalização não existe para este tenant.");
                }
                return readInspection(rs);
            }
        }
    }

    private InspectionView loadInspectionByOccurrenceOrNull(Connection c, UUID occurrenceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, occurrence_id, authority_origin, term_number, received_date, response_days,"
                        + " counting_rule, response_due_date, action_plan, attendance_evidence_kind,"
                        + " attendance_evidence_id, attendance_evidence_ref"
                        + " FROM " + t("cert_inspection") + " WHERE tenant_id = ? AND occurrence_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, occurrenceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readInspection(rs);
            }
        }
    }

    private static InspectionView readInspection(ResultSet rs) throws SQLException {
        return new InspectionView((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                InspectionAuthority.valueOf(rs.getString("authority_origin")),
                rs.getString("term_number"), date(rs, "received_date"), rs.getInt("response_days"),
                rs.getString("counting_rule"), date(rs, "response_due_date"), rs.getString("action_plan"),
                rs.getString("attendance_evidence_kind"), uuidOrNull(rs, "attendance_evidence_id"),
                rs.getString("attendance_evidence_ref"));
    }

    private FindingView loadFinding(Connection c, UUID findingId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, inspection_id, description, status, addressed_evidence_ref, addressed_at"
                        + " FROM " + t("cert_inspection_finding") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, findingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A constatação não existe para este tenant.");
                }
                return readFinding(rs);
            }
        }
    }

    private static FindingView readFinding(ResultSet rs) throws SQLException {
        return new FindingView((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                rs.getString("description"), FindingStatus.valueOf(rs.getString("status")),
                rs.getString("addressed_evidence_ref"), timestamp(rs, "addressed_at"));
    }

    private int countOpenFindings(Connection c, UUID inspectionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM " + t("cert_inspection_finding")
                        + " WHERE tenant_id = ? AND inspection_id = ? AND status = 'OPEN'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, inspectionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Tenant calendar rows for the deadline engine: the {@code holidays}
     * table of the tenant schema (V1_5), filtered by scope and locality
     * through {@link DeadlineCalculator#filterHolidayDates}. Optional
     * points do not count, as in the P1.36 protocol deadline.
     */
    private Set<LocalDate> loadTenantHolidayDates(Connection c, HolidayScope scope,
                                                  String stateCode, String municipalityIbge) throws SQLException {
        List<DeadlineCalculator.HolidayEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT holiday_date, scope, state_code, municipality_ibge, kind"
                        + " FROM " + t("holidays") + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new DeadlineCalculator.HolidayEntry(
                            date(rs, "holiday_date"),
                            DeadlineCalculator.HolidaySphere.valueOf(rs.getString("scope")),
                            rs.getString("state_code"), rs.getString("municipality_ibge"),
                            "OPTIONAL_POINT".equals(rs.getString("kind"))));
                }
            }
        }
        return DeadlineCalculator.filterHolidayDates(entries, scope, stateCode, municipalityIbge);
    }

    // ------------------------------------------------------------------
    // Transaction plumbing (same shape as the certification core)
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
                throw new IllegalStateException("Documented information operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Documented information operation failed: " + e.getMessage(), e);
        }
    }

    private String t(String table) {
        return schema + "." + table;
    }

    private static ApiException.Validation validation(String field, String code, String message) {
        return new ApiException.Validation(message,
                List.of(new ApiException.FieldError(field, code, message)), List.of());
    }

    private static String requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw validation(field, "required", message);
        }
        return value.trim();
    }

    private static String requireHash(String value) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw validation("contentHash", "invalid",
                    "O hash do conteúdo deve ser um SHA-256 hexadecimal de 64 caracteres.");
        }
        return value;
    }

    private static void setUuidOrNull(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : (UUID) value;
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static OffsetDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}
