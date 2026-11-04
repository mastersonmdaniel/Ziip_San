package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.certification.CertificationService;
import br.com.ziipsan.core.certification.DocumentedInformationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.25 (tenant V21_2) on the real PostgreSQL, over the provisioned
 * tenant.
 *
 * <p>Live proof: the controlled-document cycle (draft, review by
 * another person, approval by the reviewer only, publication with a
 * review due date, acknowledgement of the published version only and
 * idempotent, new version superseding the published one without
 * rewriting its content); the single occurrence closing freely without
 * an active link and blocked — root cause, done action, effectiveness
 * verification — with an active clause link, with one record linked to
 * two clause-map rows; the four bridges (protocol, service order,
 * security-incident pointer, document) with duplicate refused and
 * unknown protocol as 404; one received inspection per authority
 * origin (regulatory agency, audit court, environmental agency) with
 * the response deadline counted on the tenant calendar (calendar days
 * and business days skipping a registered holiday, other-state
 * holidays ignored under the tenant scope), and inspection closure
 * blocked until plan, attendance evidence and addressed findings;
 * the P2.28 work-item contract (open action and pending document
 * review listed with responsible and due date, done action gone);
 * and the logical deactivation of an evidence link promised by V21_1
 * (row kept, active false, audit view no longer counting it).
 *
 * <p>Synthetic rows only (codes with a SYN- prefix and a random
 * suffix, own-words titles, no regulatory content); platform catalog
 * rows and tenant holidays inserted by a test are deleted at its end.
 */
class DocumentedInformationIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    private static DocumentedInformationService service;
    private static CertificationService certService;
    private static final AtomicInteger PROTOCOL_NUMBER = new AtomicInteger(900000);

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
        service = new DocumentedInformationService(ProvisionedTenant.appDs, SCHEMA, TENANT);
        certService = new CertificationService(ProvisionedTenant.appDs, SCHEMA, TENANT);
    }

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String hash(char fill) {
        return String.valueOf(fill).repeat(64);
    }

    private static Connection tenantConnection() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
            ps.setString(1, TENANT.toString());
            ps.executeQuery().close();
        }
        return c;
    }

    private static void insertStandard(String code) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_standard (catalog_key, code, title, standard_class,"
                            + " verification_seal, status) VALUES (?, ?, ?, 'VOLUNTARY', 'NAO_CONFIRMADA', 'VIGENTE')")) {
                ps.setString(1, "syn-" + code.toLowerCase());
                ps.setString(2, code);
                ps.setString(3, "Norma sintética de teste — sistema de gestão genérico (" + code + ")");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_standard_edition (standard_id, edition)"
                            + " SELECT id, 'ED-TESTE' FROM platform.cert_standard WHERE code = ?")) {
                ps.setString(1, code);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_clause (edition_id, clause_number, title, summary, sort_order)"
                            + " SELECT e.id, ?, ?, ?, ? FROM platform.cert_standard_edition e"
                            + " JOIN platform.cert_standard s ON s.id = e.standard_id WHERE s.code = ?")) {
                ps.setString(1, "4.1");
                ps.setString(2, "Contexto em palavras próprias de teste");
                ps.setString(3, "Resumo próprio de teste para a cláusula de contexto.");
                ps.setInt(4, 1);
                ps.setString(5, code);
                ps.executeUpdate();
                ps.setString(1, "5.1");
                ps.setString(2, "Liderança em palavras próprias de teste");
                ps.setString(3, "Resumo próprio de teste para a cláusula de liderança.");
                ps.setInt(4, 2);
                ps.setString(5, code);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    private static void deleteStandard(String code) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("DELETE FROM platform.cert_standard WHERE code = '" + code + "'");
            c.commit();
        }
    }

    private static CertificationService.ActivationView activateCycle(String code) {
        UUID proposer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        UUID accepter = UUID.randomUUID();
        CertificationService.ActivationView proposed = certService.activate(
                new CertificationService.ActivateInput(code, "Escopo de teste — unidade sintética", "ED-TESTE"),
                proposer);
        CertificationService.ActivationView approved = certService.approve(proposed.id(), approver);
        return certService.accept(approved.id(), accepter);
    }

    private static void insertHoliday(LocalDate date, String scope, String stateCode, String description)
            throws SQLException {
        try (Connection c = tenantConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + SCHEMA + ".holidays (tenant_id, holiday_date, scope, state_code,"
                             + " municipality_ibge, description, source, kind)"
                             + " VALUES (?, ?, ?, ?, NULL, ?, 'Calendário sintético de teste', 'HOLIDAY')")) {
            ps.setObject(1, TENANT);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, scope);
            ps.setString(4, stateCode);
            ps.setString(5, description);
            ps.executeUpdate();
            c.commit();
        }
    }

    private static void deleteHolidays(String descriptionPrefix) throws SQLException {
        try (Connection c = tenantConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM " + SCHEMA + ".holidays WHERE tenant_id = ? AND description LIKE ?")) {
            ps.setObject(1, TENANT);
            ps.setString(2, descriptionPrefix + "%");
            ps.executeUpdate();
            c.commit();
        }
    }

    private static UUID insertProtocol() throws SQLException {
        UUID specId;
        UUID protocolId;
        try (Connection c = tenantConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".service_protocol_specs (tenant_id, code, description, deadline_days)"
                            + " VALUES (?, ?, ?, 30) RETURNING id")) {
                ps.setObject(1, TENANT);
                ps.setString(2, uniq("SYN-SPEC"));
                ps.setString(3, "Especificação sintética de teste");
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    specId = (UUID) rs.getObject(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".service_protocols (tenant_id, protocol_year, protocol_number,"
                            + " channel, spec_id, location_text, requester_role, requester_name,"
                            + " requester_contact, opened_date,"
                            + " original_due_date, current_due_date, created_by)"
                            + " VALUES (?, 2027, ?, 'COUNTER', ?, 'Local sintético de teste', 'INTERNAL',"
                            + " 'Solicitante sintético de teste', 'teste@sintetico.local',"
                            + " DATE '2027-02-01', DATE '2027-03-03', DATE '2027-03-03', ?) RETURNING id")) {
                ps.setObject(1, TENANT);
                ps.setInt(2, PROTOCOL_NUMBER.incrementAndGet());
                ps.setObject(3, specId);
                ps.setObject(4, UUID.randomUUID());
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    protocolId = (UUID) rs.getObject(1);
                }
            }
            c.commit();
        }
        return protocolId;
    }

    private static UUID insertServiceOrder() throws SQLException {
        UUID typeId;
        UUID orderId;
        try (Connection c = tenantConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".service_types (tenant_id, code, name, operation,"
                            + " effect_code, indicator, target_classes)"
                            + " VALUES (?, ?, 'Tipo sintético de teste', 'SYNTHETIC_OPERATION',"
                            + " 'SYNTHETIC_EFFECT', 'INTERNAL', ARRAY['LOCATION_ONLY']) RETURNING id")) {
                ps.setObject(1, TENANT);
                ps.setString(2, uniq("SYN-TYPE"));
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    typeId = (UUID) rs.getObject(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".service_orders (tenant_id, service_type_id, origin,"
                            + " execution_mode, status, created_by)"
                            + " VALUES (?, ?, 'INTERNAL', 'INTERNAL', 'DRAFT', ?) RETURNING id")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, typeId);
                ps.setObject(3, UUID.randomUUID());
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    orderId = (UUID) rs.getObject(1);
                }
            }
            c.commit();
        }
        return orderId;
    }

    private static DocumentedInformationService.DocumentView publishedDocument(String code) {
        UUID author = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        DocumentedInformationService.DocumentView doc = service.createDocument(
                new DocumentedInformationService.CreateDocumentInput(code, "Documento sintético de teste",
                        "PROCEDIMENTO", "GUARDA_5_ANOS", "INTERNAL", "1", hash('a')), author);
        service.submitForReview(doc.id(), new DocumentedInformationService.ReviewInput(reviewer), author);
        service.approveDocument(doc.id(), reviewer);
        return service.publishDocument(doc.id(),
                new DocumentedInformationService.PublishInput(LocalDate.now().plusYears(1)), reviewer);
    }

    @Test
    @DisplayName("documento: rascunho, revisão por outra pessoa, aprovação só do revisor, publicação, ciência idempotente, nova versão sem reescrever")
    void documentCycle() {
        UUID author = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String code = uniq("SYN-DOC");
        DocumentedInformationService.DocumentView doc = service.createDocument(
                new DocumentedInformationService.CreateDocumentInput(code, "Procedimento sintético",
                        "PROCEDIMENTO", "GUARDA_5_ANOS", "INTERNAL", "1", hash('a')), author);
        assertThat(doc.status()).isEqualTo(DocumentedInformationService.DocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.submitForReview(doc.id(),
                new DocumentedInformationService.ReviewInput(author), author))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.acknowledgeDocument(doc.id(), stranger))
                .isInstanceOf(ApiException.Conflict.class);
        DocumentedInformationService.DocumentView underReview = service.submitForReview(doc.id(),
                new DocumentedInformationService.ReviewInput(reviewer), author);
        assertThat(underReview.status()).isEqualTo(DocumentedInformationService.DocumentStatus.UNDER_REVIEW);
        assertThatThrownBy(() -> service.approveDocument(doc.id(), stranger))
                .isInstanceOf(ApiException.Forbidden.class);
        DocumentedInformationService.DocumentView approved = service.approveDocument(doc.id(), reviewer);
        assertThat(approved.status()).isEqualTo(DocumentedInformationService.DocumentStatus.APPROVED);
        DocumentedInformationService.DocumentView published = service.publishDocument(doc.id(),
                new DocumentedInformationService.PublishInput(LocalDate.of(2028, 2, 1)), reviewer);
        assertThat(published.status()).isEqualTo(DocumentedInformationService.DocumentStatus.PUBLISHED);
        assertThat(published.reviewDueDate()).isEqualTo(LocalDate.of(2028, 2, 1));
        DocumentedInformationService.AcknowledgementView ack1 = service.acknowledgeDocument(doc.id(), stranger);
        DocumentedInformationService.AcknowledgementView ack2 = service.acknowledgeDocument(doc.id(), stranger);
        assertThat(ack2.id()).isEqualTo(ack1.id());
        assertThat(ack1.contentHash()).isEqualTo(hash('a'));
        assertThat(service.listAcknowledgements(doc.id())).hasSize(1);
        DocumentedInformationService.DocumentView v2 = service.newVersion(doc.id(),
                new DocumentedInformationService.CreateDocumentInput(null, null, null, null, null, "2", hash('b')),
                author);
        assertThat(v2.docVersion()).isEqualTo("2");
        assertThat(v2.supersedesId()).isEqualTo(doc.id());
        DocumentedInformationService.DocumentView old = service.getDocument(doc.id());
        assertThat(old.status()).isEqualTo(DocumentedInformationService.DocumentStatus.SUPERSEDED);
        assertThat(old.contentHash()).isEqualTo(hash('a'));
        assertThatThrownBy(() -> service.newVersion(doc.id(),
                new DocumentedInformationService.CreateDocumentInput(null, null, null, null, null, "3", hash('c')),
                author))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("ocorrência livre fecha sem exigências; com vínculo ativo exige causa, ação concluída e eficácia")
    void occurrenceClosureRules() throws SQLException {
        DocumentedInformationService.OccurrenceView free = service.createOccurrence(
                new DocumentedInformationService.CreateOccurrenceInput("IMPROVEMENT",
                        "Melhoria sintética livre", "Descrição sintética livre", "INTERNAL"),
                UUID.randomUUID());
        assertThat(service.closeOccurrence(free.id(), UUID.randomUUID()).status())
                .isEqualTo(DocumentedInformationService.OccurrenceStatus.CLOSED);

        String code = uniq("SYN-VOL");
        try {
            insertStandard(code);
            CertificationService.ActivationView activation = activateCycle(code);
            List<CertificationService.ClauseMapView> map = certService.listClauseMap(activation.id());
            assertThat(map).hasSize(2);
            DocumentedInformationService.OccurrenceView occurrence = service.createOccurrence(
                    new DocumentedInformationService.CreateOccurrenceInput("NONCONFORMITY",
                            "Não conformidade sintética", "Descrição sintética", "AUDIT"),
                    UUID.randomUUID());
            service.linkClause(occurrence.id(), map.get(0).id(), UUID.randomUUID());
            service.linkClause(occurrence.id(), map.get(1).id(), UUID.randomUUID());
            assertThat(service.listOccurrenceClauses(occurrence.id())).hasSize(2);
            assertThatThrownBy(() -> service.closeOccurrence(occurrence.id(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.Validation.class)
                    .satisfies(e -> assertThat(((ApiException.Validation) e).errors())
                            .extracting(ApiException.FieldError::field)
                            .containsExactlyInAnyOrder("rootCause", "action", "effectiveness"));
            service.setRootCause(occurrence.id(), "Causa sintética apurada", UUID.randomUUID());
            UUID responsible = UUID.randomUUID();
            DocumentedInformationService.ActionView action = service.addAction(occurrence.id(),
                    new DocumentedInformationService.CreateActionInput("Ação sintética",
                            responsible, LocalDate.of(2027, 3, 1)), UUID.randomUUID());
            service.markActionDone(action.id(), responsible);
            assertThatThrownBy(() -> service.closeOccurrence(occurrence.id(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.Validation.class)
                    .satisfies(e -> assertThat(((ApiException.Validation) e).errors())
                            .extracting(ApiException.FieldError::field)
                            .containsExactly("effectiveness"));
            service.verifyEffectiveness(occurrence.id(), "Eficaz na verificação sintética", UUID.randomUUID());
            assertThat(service.closeOccurrence(occurrence.id(), UUID.randomUUID()).status())
                    .isEqualTo(DocumentedInformationService.OccurrenceStatus.CLOSED);
        } finally {
            deleteStandard(code);
        }
    }

    @Test
    @DisplayName("pontes: protocolo, OS, incidente de segurança e documento; duplicata recusada, protocolo desconhecido é 404")
    void occurrenceLinks() throws SQLException {
        UUID protocolId = insertProtocol();
        UUID orderId = insertServiceOrder();
        UUID incidentId = UUID.randomUUID();
        DocumentedInformationService.DocumentView doc = publishedDocument(uniq("SYN-LINK"));
        DocumentedInformationService.OccurrenceView occurrence = service.createOccurrence(
                new DocumentedInformationService.CreateOccurrenceInput("INCIDENT",
                        "Incidente sintético", "Descrição sintética", "INTERNAL"), UUID.randomUUID());
        service.linkTarget(occurrence.id(), DocumentedInformationService.LinkKind.PROTOCOL,
                protocolId, null, null, null, null, UUID.randomUUID());
        service.linkTarget(occurrence.id(), DocumentedInformationService.LinkKind.SERVICE_ORDER,
                null, orderId, null, null, null, UUID.randomUUID());
        service.linkTarget(occurrence.id(), DocumentedInformationService.LinkKind.SECURITY_INCIDENT,
                null, null, incidentId, null, "INC-SEG-SINTETICO-001", UUID.randomUUID());
        service.linkTarget(occurrence.id(), DocumentedInformationService.LinkKind.DOCUMENT,
                null, null, null, doc.id(), null, UUID.randomUUID());
        assertThat(service.listOccurrenceLinks(occurrence.id())).hasSize(4);
        assertThatThrownBy(() -> service.linkTarget(occurrence.id(),
                DocumentedInformationService.LinkKind.PROTOCOL, protocolId, null, null, null, null,
                UUID.randomUUID()))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> service.linkTarget(occurrence.id(),
                DocumentedInformationService.LinkKind.PROTOCOL, UUID.randomUUID(), null, null, null, null,
                UUID.randomUUID()))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    @DisplayName("fiscalização de cada origem: termo, prazo pelo calendário do tenant, plano, evidência e constatações até o fechamento")
    void inspectionsOfEachOrigin() throws SQLException {
        String holidayPrefix = "SYN-FERIADO-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            insertHoliday(LocalDate.of(2027, 2, 3), "NATIONAL", null, holidayPrefix + "-nacional");
            for (String authority : List.of("REGULATORY_AGENCY", "AUDIT_COURT", "ENVIRONMENTAL_AGENCY")) {
                DocumentedInformationService.InspectionView inspection = service.registerInspection(
                        new DocumentedInformationService.RegisterInspectionInput("NONCONFORMITY",
                                "Ocorrência de fiscalização sintética", "Descrição sintética",
                                authority, "TERMO-SIN-" + authority, LocalDate.of(2027, 2, 1), 3,
                                "BUSINESS", "NATIONAL", null, null,
                                List.of("Constatação sintética 1", "Constatação sintética 2")),
                        UUID.randomUUID());
                assertThat(inspection.authorityOrigin().name()).isEqualTo(authority);
                assertThat(inspection.responseDueDate()).isEqualTo(LocalDate.of(2027, 2, 5));
                assertThat(inspection.responseDays()).isEqualTo(3);
                DocumentedInformationService.OccurrenceView occurrence =
                        service.getOccurrence(inspection.occurrenceId());
                assertThat(occurrence.origin())
                        .isEqualTo(DocumentedInformationService.OccurrenceOrigin.INSPECTION);
                assertThat(service.listFindings(inspection.id())).hasSize(2);
            }
            DocumentedInformationService.InspectionView calendar = service.registerInspection(
                    new DocumentedInformationService.RegisterInspectionInput("NONCONFORMITY",
                            "Ocorrência de fiscalização em dias corridos", "Descrição sintética",
                            "REGULATORY_AGENCY", "TERMO-SIN-CAL", LocalDate.of(2027, 2, 1), 3,
                            "CALENDAR", "NATIONAL", null, null, List.of("Constatação sintética")),
                    UUID.randomUUID());
            assertThat(calendar.responseDueDate()).isEqualTo(LocalDate.of(2027, 2, 4));

            DocumentedInformationService.InspectionView inspection = service.registerInspection(
                    new DocumentedInformationService.RegisterInspectionInput("NONCONFORMITY",
                            "Ocorrência de fiscalização até o fechamento", "Descrição sintética",
                            "REGULATORY_AGENCY", "TERMO-SIN-FECHA", LocalDate.of(2027, 2, 1), 10,
                            "CALENDAR", "NATIONAL", null, null, List.of("Constatação sintética")),
                    UUID.randomUUID());
            assertThatThrownBy(() -> service.closeOccurrence(inspection.occurrenceId(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.Validation.class)
                    .satisfies(e -> assertThat(((ApiException.Validation) e).errors())
                            .extracting(ApiException.FieldError::field)
                            .containsExactlyInAnyOrder("actionPlan", "attendanceEvidence", "findings"));
            DocumentedInformationService.FindingView finding =
                    service.listFindings(inspection.id()).get(0);
            service.addressFinding(finding.id(), "Evidência sintética de atendimento", UUID.randomUUID());
            service.setInspectionActionPlan(inspection.id(), "Plano sintético de ação", UUID.randomUUID());
            service.setInspectionAttendanceEvidence(inspection.id(), "RECORD", null,
                    "Protocolo sintético de atendimento", UUID.randomUUID());
            assertThat(service.closeOccurrence(inspection.occurrenceId(), UUID.randomUUID()).status())
                    .isEqualTo(DocumentedInformationService.OccurrenceStatus.CLOSED);
        } finally {
            deleteHolidays(holidayPrefix);
        }
    }

    @Test
    @DisplayName("prazo em dias úteis ignora feriado estadual de outra UF no escopo do tenant")
    void businessDeadlineIgnoresOtherStateHoliday() throws SQLException {
        String holidayPrefix = "SYN-FERIADO-UF-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            insertHoliday(LocalDate.of(2027, 2, 9), "STATE", "RJ", holidayPrefix + "-rj");
            DocumentedInformationService.InspectionView inspection = service.registerInspection(
                    new DocumentedInformationService.RegisterInspectionInput("NONCONFORMITY",
                            "Ocorrência com escopo estadual sintético", "Descrição sintética",
                            "AUDIT_COURT", "TERMO-SIN-UF", LocalDate.of(2027, 2, 8), 3,
                            "BUSINESS", "NATIONAL_STATE", "SP", null, List.of("Constatação sintética")),
                    UUID.randomUUID());
            assertThat(inspection.responseDueDate()).isEqualTo(LocalDate.of(2027, 2, 11));
        } finally {
            deleteHolidays(holidayPrefix);
        }
    }

    @Test
    @DisplayName("contrato P2.28: ação aberta e revisão pendente aparecem com responsável e prazo; ação concluída some")
    void workItemContract() {
        UUID responsible = UUID.randomUUID();
        DocumentedInformationService.OccurrenceView occurrence = service.createOccurrence(
                new DocumentedInformationService.CreateOccurrenceInput("NONCONFORMITY",
                        "Ocorrência sintética de tarefas", "Descrição sintética", "INTERNAL"),
                UUID.randomUUID());
        DocumentedInformationService.ActionView action = service.addAction(occurrence.id(),
                new DocumentedInformationService.CreateActionInput("Ação sintética de tarefa",
                        responsible, LocalDate.of(2027, 4, 1)), UUID.randomUUID());
        List<DocumentedInformationService.WorkItemView> items = service.listOpenWorkItems();
        assertThat(items).filteredOn(i -> i.sourceKind().equals("CERT_ACTION") && i.sourceId().equals(action.id()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.responsibleId()).isEqualTo(responsible);
                    assertThat(i.dueDate()).isEqualTo(LocalDate.of(2027, 4, 1));
                    assertThat(i.occurrenceId()).isEqualTo(occurrence.id());
                });
        service.markActionDone(action.id(), responsible);
        assertThat(service.listOpenWorkItems())
                .filteredOn(i -> i.sourceKind().equals("CERT_ACTION") && i.sourceId().equals(action.id()))
                .isEmpty();

        UUID author = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        DocumentedInformationService.DocumentView doc = service.createDocument(
                new DocumentedInformationService.CreateDocumentInput(uniq("SYN-WI"),
                        "Documento sintético de revisão", "PROCEDIMENTO", "GUARDA_5_ANOS",
                        "INTERNAL", "1", hash('d')), author);
        service.submitForReview(doc.id(), new DocumentedInformationService.ReviewInput(reviewer), author);
        assertThat(service.listOpenWorkItems())
                .filteredOn(i -> i.sourceKind().equals("CERT_DOCUMENT_REVIEW") && i.sourceId().equals(doc.id()))
                .singleElement()
                .satisfies(i -> assertThat(i.responsibleId()).isEqualTo(reviewer));
    }

    @Test
    @DisplayName("baixa lógica do vínculo de evidência (V21_1): desativa sem apagar e some da visão de auditoria")
    void evidenceLinkDeactivation() throws SQLException {
        String code = uniq("SYN-EVI");
        try {
            insertStandard(code);
            CertificationService.ActivationView activation = activateCycle(code);
            CertificationService.ClauseMapView mapRow = certService.listClauseMap(activation.id()).get(0);
            UUID evidenceId = UUID.randomUUID();
            CertificationService.EvidenceLinkView link = certService.linkEvidence(mapRow.id(),
                    new CertificationService.EvidenceInput(CertificationService.EvidenceKind.OTHER,
                            evidenceId, "Referência sintética"), UUID.randomUUID());
            assertThat(link.active()).isTrue();
            assertThat(certService.listEvidenceClauses(evidenceId)).hasSize(1);
            CertificationService.EvidenceLinkView deactivated =
                    certService.deactivateEvidenceLink(link.id(), UUID.randomUUID());
            assertThat(deactivated.active()).isFalse();
            assertThat(certService.listEvidence(mapRow.id()))
                    .filteredOn(l -> l.id().equals(link.id()))
                    .singleElement()
                    .satisfies(l -> assertThat(l.active()).isFalse());
            assertThat(certService.listEvidenceClauses(evidenceId)).isEmpty();
            assertThatThrownBy(() -> certService.deactivateEvidenceLink(link.id(), UUID.randomUUID()))
                    .isInstanceOf(ApiException.Conflict.class);
        } finally {
            deleteStandard(code);
        }
    }
}
