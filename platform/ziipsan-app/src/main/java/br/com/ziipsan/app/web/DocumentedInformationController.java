package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.certification.CertificationService;
import br.com.ziipsan.core.certification.DocumentedInformationService;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Informação documentada, ocorrência e ação (P2.25, ADR-0041): documento
 * controlado com ciclo de revisão e ciência, ocorrência única (não
 * conformidade, incidente, melhoria) com causa raiz, ação e verificação
 * de eficácia exigidas só quando a norma ativa pede, pontes com
 * protocolo, OS e incidente de segurança, vínculo com várias cláusulas
 * do mapa da P1.32 e fiscalização recebida como origem de ocorrência.
 *
 * <p>Permissões (catálogo P0.13, as mesmas da P1.32): leitura com
 * {@code cert:read}; criar, vincular e agir com {@code cert:write};
 * aprovar documento e verificar eficácia com {@code cert:approve}.
 * Sem DELETE em nenhuma rota: cancelar é estado, desligar norma
 * congela o mapa e nada some (parâmetro travado
 * {@code cert.deactivation_keeps_records}). Toda resposta carrega o
 * aviso fixo de que o módulo não certifica — o mapa é autoavaliação do
 * cliente, nunca parecer do produto.
 *
 * <p>Ação com responsável e prazo aparece em minhas tarefas pela P2.28:
 * esta API expõe o contrato em {@code GET /work-items} (origem,
 * responsável e vencimento), mas NÃO cria item de minhas tarefas — a
 * tabela {@code work_items} (V1_11) é da P2.28. O lembrete pela agenda
 * de obrigações é da ED-98, fora desta frente.
 */
@RestController
@RequestMapping("/api/v1/certification")
@PreAuthorize("hasAuthority('cert:read')")
public class DocumentedInformationController {

    public record CreateDocumentInput(String code, String title, String docType, String retentionClass,
                                      String confidentiality, String docVersion, String contentHash) {
    }

    public record ReviewInput(UUID reviewerId) {
    }

    public record PublishInput(LocalDate reviewDueDate) {
    }

    public record CreateOccurrenceInput(String kind, String title, String description, String origin) {
    }

    public record RootCauseInput(String rootCause) {
    }

    public record EffectivenessInput(String result) {
    }

    public record LinkClauseInput(UUID clauseMapId) {
    }

    public record LinkTargetInput(String linkKind, UUID protocolId, UUID serviceOrderId,
                                  UUID securityIncidentId, UUID documentId, String linkRef) {
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

    public record FindingEvidenceInput(String evidenceRef) {
    }

    public record ActionPlanInput(String actionPlan) {
    }

    public record AttendanceEvidenceInput(String evidenceKind, UUID evidenceId, String evidenceRef) {
    }

    public record DocumentResponse(UUID id, String code, String title, String docType, String retentionClass,
                                   String confidentiality, String docVersion, String contentHash, String status,
                                   UUID authorId, UUID reviewerId, UUID approvedBy, String disclaimer) {
    }

    public record AcknowledgementResponse(UUID id, UUID documentId, String docVersion, UUID userId,
                                          String contentHash, String disclaimer) {
    }

    public record OccurrenceResponse(UUID id, String kind, String title, String description, String origin,
                                     String status, String rootCause, UUID reportedBy, String disclaimer) {
    }

    public record ActionResponse(UUID id, UUID occurrenceId, String title, UUID responsibleId,
                                 LocalDate dueDate, String status, String disclaimer) {
    }

    public record InspectionResponse(UUID id, UUID occurrenceId, String authorityOrigin, String termNumber,
                                     LocalDate receivedDate, int responseDays, String countingRule,
                                     LocalDate responseDueDate, String actionPlan,
                                     String attendanceEvidenceKind, UUID attendanceEvidenceId,
                                     String attendanceEvidenceRef, String disclaimer) {
    }

    public record FindingResponse(UUID id, UUID inspectionId, String description, String status,
                                  String addressedEvidenceRef, String disclaimer) {
    }

    public record OccurrenceClauseResponse(UUID id, UUID occurrenceId, UUID clauseMapId, String disclaimer) {
    }

    public record OccurrenceLinkResponse(UUID id, UUID occurrenceId, String linkKind, UUID protocolId,
                                         UUID serviceOrderId, UUID securityIncidentId, UUID documentId,
                                         String linkRef, String disclaimer) {
    }

    public record WorkItemResponse(String sourceKind, UUID sourceId, UUID occurrenceId, String title,
                                   UUID responsibleId, LocalDate dueDate, String status, String disclaimer) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;

    public DocumentedInformationController(DataSource dataSource, TenantCatalog catalog) {
        this.dataSource = dataSource;
        this.catalog = catalog;
    }

    private DocumentedInformationService service() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new DocumentedInformationService(dataSource, schema, tenantId);
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    private static DocumentResponse toDocument(DocumentedInformationService.DocumentView view) {
        return new DocumentResponse(view.id(), view.code(), view.title(), view.docType(),
                view.retentionClass(), view.confidentiality().name(), view.docVersion(), view.contentHash(),
                view.status().name(), view.authorId(), view.reviewerId(), view.approvedBy(),
                CertificationService.DISCLAIMER);
    }

    private static OccurrenceResponse toOccurrence(DocumentedInformationService.OccurrenceView view) {
        return new OccurrenceResponse(view.id(), view.kind().name(), view.title(), view.description(),
                view.origin().name(), view.status().name(), view.rootCause(), view.reportedBy(),
                CertificationService.DISCLAIMER);
    }

    private static ActionResponse toAction(DocumentedInformationService.ActionView view) {
        return new ActionResponse(view.id(), view.occurrenceId(), view.title(), view.responsibleId(),
                view.dueDate(), view.status().name(), CertificationService.DISCLAIMER);
    }

    private static InspectionResponse toInspection(DocumentedInformationService.InspectionView view) {
        return new InspectionResponse(view.id(), view.occurrenceId(), view.authorityOrigin().name(),
                view.termNumber(), view.receivedDate(), view.responseDays(), view.countingRule(),
                view.responseDueDate(), view.actionPlan(), view.attendanceEvidenceKind(),
                view.attendanceEvidenceId(), view.attendanceEvidenceRef(), CertificationService.DISCLAIMER);
    }

    // ------------------------------------------------------------------
    // Controlled documents
    // ------------------------------------------------------------------

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public DocumentResponse createDocument(@RequestBody CreateDocumentInput in, JwtAuthenticationToken auth) {
        return toDocument(service().createDocument(
                new DocumentedInformationService.CreateDocumentInput(in.code(), in.title(), in.docType(),
                        in.retentionClass(), in.confidentiality(), in.docVersion(), in.contentHash()),
                actorId(auth)));
    }

    @GetMapping("/documents")
    public List<DocumentResponse> listDocuments() {
        return service().listDocuments().stream().map(DocumentedInformationController::toDocument).toList();
    }

    @GetMapping("/documents/{id}")
    public DocumentResponse getDocument(@PathVariable UUID id) {
        return toDocument(service().getDocument(id));
    }

    @PostMapping("/documents/{id}/submit-review")
    @PreAuthorize("hasAuthority('cert:write')")
    public DocumentResponse submitForReview(@PathVariable UUID id, @RequestBody ReviewInput in,
                                            JwtAuthenticationToken auth) {
        return toDocument(service().submitForReview(id,
                new DocumentedInformationService.ReviewInput(in.reviewerId()), actorId(auth)));
    }

    @PostMapping("/documents/{id}/approve")
    @PreAuthorize("hasAuthority('cert:approve')")
    public DocumentResponse approveDocument(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toDocument(service().approveDocument(id, actorId(auth)));
    }

    @PostMapping("/documents/{id}/publish")
    @PreAuthorize("hasAuthority('cert:approve')")
    public DocumentResponse publishDocument(@PathVariable UUID id, @RequestBody PublishInput in,
                                            JwtAuthenticationToken auth) {
        return toDocument(service().publishDocument(id,
                new DocumentedInformationService.PublishInput(in.reviewDueDate()), actorId(auth)));
    }

    @PostMapping("/documents/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public DocumentResponse newVersion(@PathVariable UUID id, @RequestBody CreateDocumentInput in,
                                       JwtAuthenticationToken auth) {
        return toDocument(service().newVersion(id,
                new DocumentedInformationService.CreateDocumentInput(in.code(), in.title(), in.docType(),
                        in.retentionClass(), in.confidentiality(), in.docVersion(), in.contentHash()),
                actorId(auth)));
    }

    @PostMapping("/documents/{id}/acknowledge")
    @PreAuthorize("hasAuthority('cert:write')")
    public AcknowledgementResponse acknowledgeDocument(@PathVariable UUID id, JwtAuthenticationToken auth) {
        DocumentedInformationService.AcknowledgementView view =
                service().acknowledgeDocument(id, actorId(auth));
        return new AcknowledgementResponse(view.id(), view.documentId(), view.docVersion(), view.userId(),
                view.contentHash(), CertificationService.DISCLAIMER);
    }

    @GetMapping("/documents/{id}/acknowledgements")
    public List<AcknowledgementResponse> listAcknowledgements(@PathVariable UUID id) {
        return service().listAcknowledgements(id).stream()
                .map(v -> new AcknowledgementResponse(v.id(), v.documentId(), v.docVersion(), v.userId(),
                        v.contentHash(), CertificationService.DISCLAIMER))
                .toList();
    }

    // ------------------------------------------------------------------
    // Occurrences
    // ------------------------------------------------------------------

    @PostMapping("/occurrences")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceResponse createOccurrence(@RequestBody CreateOccurrenceInput in,
                                               JwtAuthenticationToken auth) {
        return toOccurrence(service().createOccurrence(
                new DocumentedInformationService.CreateOccurrenceInput(in.kind(), in.title(), in.description(),
                        in.origin()),
                actorId(auth)));
    }

    @GetMapping("/occurrences")
    public List<OccurrenceResponse> listOccurrences() {
        return service().listOccurrences().stream().map(DocumentedInformationController::toOccurrence).toList();
    }

    @GetMapping("/occurrences/{id}")
    public OccurrenceResponse getOccurrence(@PathVariable UUID id) {
        return toOccurrence(service().getOccurrence(id));
    }

    @PostMapping("/occurrences/{id}/start-analysis")
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceResponse startAnalysis(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toOccurrence(service().startAnalysis(id, actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/root-cause")
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceResponse setRootCause(@PathVariable UUID id, @RequestBody RootCauseInput in,
                                           JwtAuthenticationToken auth) {
        return toOccurrence(service().setRootCause(id, in.rootCause(), actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/action-plan-state")
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceResponse setActionPlanState(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toOccurrence(service().setActionPlanState(id, actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/verify-effectiveness")
    @PreAuthorize("hasAuthority('cert:approve')")
    public OccurrenceResponse verifyEffectiveness(@PathVariable UUID id, @RequestBody EffectivenessInput in,
                                                  JwtAuthenticationToken auth) {
        return toOccurrence(service().verifyEffectiveness(id, in.result(), actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/close")
    @PreAuthorize("hasAuthority('cert:approve')")
    public OccurrenceResponse closeOccurrence(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toOccurrence(service().closeOccurrence(id, actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/cancel")
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceResponse cancelOccurrence(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toOccurrence(service().cancelOccurrence(id, actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/clauses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceClauseResponse linkClause(@PathVariable UUID id, @RequestBody LinkClauseInput in,
                                               JwtAuthenticationToken auth) {
        DocumentedInformationService.OccurrenceClauseView view =
                service().linkClause(id, in.clauseMapId(), actorId(auth));
        return new OccurrenceClauseResponse(view.id(), view.occurrenceId(), view.clauseMapId(),
                CertificationService.DISCLAIMER);
    }

    @GetMapping("/occurrences/{id}/clauses")
    public List<OccurrenceClauseResponse> listOccurrenceClauses(@PathVariable UUID id) {
        return service().listOccurrenceClauses(id).stream()
                .map(v -> new OccurrenceClauseResponse(v.id(), v.occurrenceId(), v.clauseMapId(),
                        CertificationService.DISCLAIMER))
                .toList();
    }

    @PostMapping("/occurrences/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public OccurrenceLinkResponse linkTarget(@PathVariable UUID id, @RequestBody LinkTargetInput in,
                                             JwtAuthenticationToken auth) {
        DocumentedInformationService.OccurrenceLinkView view = service().linkTarget(id,
                DocumentedInformationService.LinkKind.from(in.linkKind()), in.protocolId(), in.serviceOrderId(),
                in.securityIncidentId(), in.documentId(), in.linkRef(), actorId(auth));
        return new OccurrenceLinkResponse(view.id(), view.occurrenceId(), view.linkKind().name(),
                view.protocolId(), view.serviceOrderId(), view.securityIncidentId(), view.documentId(),
                view.linkRef(), CertificationService.DISCLAIMER);
    }

    @GetMapping("/occurrences/{id}/links")
    public List<OccurrenceLinkResponse> listOccurrenceLinks(@PathVariable UUID id) {
        return service().listOccurrenceLinks(id).stream()
                .map(v -> new OccurrenceLinkResponse(v.id(), v.occurrenceId(), v.linkKind().name(),
                        v.protocolId(), v.serviceOrderId(), v.securityIncidentId(), v.documentId(),
                        v.linkRef(), CertificationService.DISCLAIMER))
                .toList();
    }

    @PostMapping("/occurrences/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public ActionResponse addAction(@PathVariable UUID id, @RequestBody CreateActionInput in,
                                    JwtAuthenticationToken auth) {
        return toAction(service().addAction(id,
                new DocumentedInformationService.CreateActionInput(in.title(), in.responsibleId(), in.dueDate()),
                actorId(auth)));
    }

    @GetMapping("/occurrences/{id}/actions")
    public List<ActionResponse> listActions(@PathVariable UUID id) {
        return service().listActions(id).stream().map(DocumentedInformationController::toAction).toList();
    }

    @PostMapping("/occurrences/{id}/actions/{actionId}/start")
    @PreAuthorize("hasAuthority('cert:write')")
    public ActionResponse startAction(@PathVariable UUID id, @PathVariable UUID actionId,
                                      JwtAuthenticationToken auth) {
        return toAction(service().startAction(actionId, actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/actions/{actionId}/done")
    @PreAuthorize("hasAuthority('cert:write')")
    public ActionResponse markActionDone(@PathVariable UUID id, @PathVariable UUID actionId,
                                         JwtAuthenticationToken auth) {
        return toAction(service().markActionDone(actionId, actorId(auth)));
    }

    @PostMapping("/occurrences/{id}/actions/{actionId}/verify-effectiveness")
    @PreAuthorize("hasAuthority('cert:approve')")
    public ActionResponse verifyActionEffectiveness(@PathVariable UUID id, @PathVariable UUID actionId,
                                                    @RequestBody EffectivenessInput in,
                                                    JwtAuthenticationToken auth) {
        return toAction(service().verifyActionEffectiveness(actionId, in.result(), actorId(auth)));
    }

    // ------------------------------------------------------------------
    // Inspections
    // ------------------------------------------------------------------

    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public InspectionResponse registerInspection(@RequestBody RegisterInspectionInput in,
                                                 JwtAuthenticationToken auth) {
        return toInspection(service().registerInspection(
                new DocumentedInformationService.RegisterInspectionInput(in.kind(), in.title(), in.description(),
                        in.authorityOrigin(), in.termNumber(), in.receivedDate(), in.responseDays(),
                        in.countingRule(), in.holidayScope(), in.stateCode(), in.municipalityIbge(),
                        in.findings()),
                actorId(auth)));
    }

    @GetMapping("/occurrences/{id}/inspection")
    public InspectionResponse getInspectionByOccurrence(@PathVariable UUID id) {
        return toInspection(service().getInspectionByOccurrence(id));
    }

    @GetMapping("/inspections/{inspectionId}/findings")
    public List<FindingResponse> listFindings(@PathVariable UUID inspectionId) {
        return service().listFindings(inspectionId).stream()
                .map(v -> new FindingResponse(v.id(), v.inspectionId(), v.description(), v.status().name(),
                        v.addressedEvidenceRef(), CertificationService.DISCLAIMER))
                .toList();
    }

    @PostMapping("/inspections/{inspectionId}/findings/{findingId}/address")
    @PreAuthorize("hasAuthority('cert:write')")
    public FindingResponse addressFinding(@PathVariable UUID inspectionId, @PathVariable UUID findingId,
                                          @RequestBody FindingEvidenceInput in, JwtAuthenticationToken auth) {
        DocumentedInformationService.FindingView view =
                service().addressFinding(findingId, in.evidenceRef(), actorId(auth));
        return new FindingResponse(view.id(), view.inspectionId(), view.description(), view.status().name(),
                view.addressedEvidenceRef(), CertificationService.DISCLAIMER);
    }

    @PostMapping("/inspections/{inspectionId}/action-plan")
    @PreAuthorize("hasAuthority('cert:write')")
    public InspectionResponse setInspectionActionPlan(@PathVariable UUID inspectionId,
                                                      @RequestBody ActionPlanInput in,
                                                      JwtAuthenticationToken auth) {
        return toInspection(service().setInspectionActionPlan(inspectionId, in.actionPlan(), actorId(auth)));
    }

    @PostMapping("/inspections/{inspectionId}/attendance-evidence")
    @PreAuthorize("hasAuthority('cert:write')")
    public InspectionResponse setInspectionAttendanceEvidence(@PathVariable UUID inspectionId,
                                                              @RequestBody AttendanceEvidenceInput in,
                                                              JwtAuthenticationToken auth) {
        return toInspection(service().setInspectionAttendanceEvidence(inspectionId, in.evidenceKind(),
                in.evidenceId(), in.evidenceRef(), actorId(auth)));
    }

    // ------------------------------------------------------------------
    // Work-item contract for P2.28 (read-only here)
    // ------------------------------------------------------------------

    @GetMapping("/work-items")
    public List<WorkItemResponse> listOpenWorkItems() {
        return service().listOpenWorkItems().stream()
                .map(v -> new WorkItemResponse(v.sourceKind(), v.sourceId(), v.occurrenceId(), v.title(),
                        v.responsibleId(), v.dueDate(), v.status(), CertificationService.DISCLAIMER))
                .toList();
    }
}
