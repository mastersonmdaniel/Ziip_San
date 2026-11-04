package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.certification.CertificationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * Núcleo do módulo Certificações de qualidade (P1.32, ADR-0041): catálogo
 * de normas, ativação por tenant com escopo, edição e aceite, mapa de
 * cláusulas e vínculo muitos-para-muitos de evidência.
 *
 * <p>Permissões (catálogo P0.13, concessão direta — nenhum perfil de
 * fábrica as inclui até a P1.37): leitura com {@code cert:read}; propor
 * ativação, desligar e vincular evidência com {@code cert:write}; aprovar
 * e aceitar a ativação com {@code cert:approve}; a visão de auditoria do
 * vínculo, pelo lado da evidência, com {@code cert:audit}.
 *
 * <p>Sem DELETE em nenhuma rota: desligar uma norma congela o mapa e
 * mantém ativação, mapa e vínculos consultáveis (parâmetro travado
 * {@code cert.deactivation_keeps_records}). Toda resposta carrega o aviso
 * fixo de que o módulo não certifica — o mapa é autoavaliação do cliente,
 * nunca parecer do produto.
 *
 * <p>O catálogo nasce VAZIO e só a carga da P1.34 o popula, a partir do
 * catálogo consolidado em docs/normas (migration repetível
 * R__norm_catalog.sql; gerador em scripts/normas). A carga traz
 * metadados e resumos em palavras próprias — nunca texto de norma — e
 * NÃO traz as 33 normas ABNT da lista fechada da P1.28: a compra segue
 * PENDENTE, sem decisão do dono (docs/arquitetura/P1-28-LISTA-FECHADA-
 * NORMAS-ABNT.md), e as 36 linhas correspondentes do JSON ficam fora
 * do catálogo até a compra ser decidida. Ativar uma norma sem linha no
 * catálogo é 404 — nunca uma norma inventada pelo serviço.
 */
@RestController
@RequestMapping("/api/v1/certification")
@PreAuthorize("hasAuthority('cert:read')")
public class CertificationController {

    public record ActivateInput(String standardCode, String scopeStatement, String edition) {
    }

    public record DeactivateInput(String reason) {
    }

    public record LinkEvidenceInput(String evidenceKind, UUID evidenceId, String evidenceRef) {
    }

    public record StandardResponse(UUID id, String catalogKey, String code, String title, String summary,
                                   String standardClass, String area, String module, String agency,
                                   String verificationSeal, String status, String disclaimer) {
    }

    public record EditionResponse(UUID id, String edition, String disclaimer) {
    }

    public record ClauseResponse(UUID id, String clauseNumber, String title, String summary, String disclaimer) {
    }

    public record ActivationResponse(UUID id, String standardCode, String catalogKey, String standardClass,
                                     String edition, String scopeStatement, String status,
                                     UUID proposedBy, UUID approvedBy, UUID acceptedBy, String acceptanceHash,
                                     String disclaimer) {
    }

    public record ClauseMapResponse(UUID id, UUID activationId, UUID clauseId, String clauseNumber,
                                    String titleSnapshot, String mapStatus, String disclaimer) {
    }

    public record EvidenceResponse(UUID id, UUID clauseMapId, String evidenceKind, UUID evidenceId,
                                   String evidenceRef, boolean active, String disclaimer) {
    }

    public record DisclaimerResponse(String disclaimer) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;

    public CertificationController(DataSource dataSource, TenantCatalog catalog) {
        this.dataSource = dataSource;
        this.catalog = catalog;
    }

    private CertificationService service() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new CertificationService(dataSource, schema, tenantId);
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    private static StandardResponse toStandard(CertificationService.StandardView view) {
        return new StandardResponse(view.id(), view.catalogKey(), view.code(), view.title(), view.summary(),
                view.standardClass().name(), view.area(), view.module(), view.agency(),
                view.verificationSeal(), view.status(), CertificationService.DISCLAIMER);
    }

    private static ActivationResponse toActivation(CertificationService.ActivationView view) {
        return new ActivationResponse(view.id(), view.standardCode(), view.catalogKey(),
                view.standardClass().name(), view.edition(), view.scopeStatement(),
                view.status().name(), view.proposedBy(), view.approvedBy(), view.acceptedBy(),
                view.acceptanceHash(), CertificationService.DISCLAIMER);
    }

    @GetMapping("/disclaimer")
    public DisclaimerResponse disclaimer() {
        return new DisclaimerResponse(CertificationService.DISCLAIMER);
    }

    @GetMapping("/standards")
    public List<StandardResponse> listStandards(
            @RequestParam(required = false) String standardClass,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agency) {
        CertificationService service = service();
        boolean filtered = (standardClass != null && !standardClass.isBlank())
                || (area != null && !area.isBlank())
                || (module != null && !module.isBlank())
                || (status != null && !status.isBlank())
                || (agency != null && !agency.isBlank());
        List<CertificationService.StandardView> views = filtered
                ? service.listStandardsFiltered(new CertificationService.StandardFilter(
                        standardClass == null || standardClass.isBlank()
                                ? null : CertificationService.StandardClass.from(standardClass),
                        area, module, status, agency))
                : service.listStandards();
        return views.stream().map(CertificationController::toStandard).toList();
    }

    @GetMapping("/standards/{code}")
    public StandardResponse getStandard(@PathVariable String code) {
        return toStandard(service().getStandard(code));
    }

    @GetMapping("/standards/{code}/editions")
    public List<EditionResponse> listEditions(@PathVariable String code) {
        return service().listEditions(code).stream()
                .map(e -> new EditionResponse(e.id(), e.edition(), CertificationService.DISCLAIMER))
                .toList();
    }

    @GetMapping("/standards/{code}/editions/{edition}/clauses")
    public List<ClauseResponse> listClauses(@PathVariable String code, @PathVariable String edition) {
        return service().listClauses(code, edition).stream()
                .map(c -> new ClauseResponse(c.id(), c.clauseNumber(), c.title(), c.summary(),
                        CertificationService.DISCLAIMER))
                .toList();
    }

    @GetMapping("/activations")
    public List<ActivationResponse> listActivations() {
        return service().listActivations().stream()
                .map(CertificationController::toActivation).toList();
    }

    @GetMapping("/activations/{id}")
    public ActivationResponse getActivation(@PathVariable UUID id) {
        return toActivation(service().getActivation(id));
    }

    @GetMapping("/activations/{id}/clause-map")
    public List<ClauseMapResponse> listClauseMap(@PathVariable UUID id) {
        return service().listClauseMap(id).stream()
                .map(m -> new ClauseMapResponse(m.id(), m.activationId(), m.clauseId(), m.clauseNumber(),
                        m.titleSnapshot(), m.mapStatus(), CertificationService.DISCLAIMER))
                .toList();
    }

    @GetMapping("/activations/{id}/clause-map/{mapId}/evidence")
    public List<EvidenceResponse> listEvidence(@PathVariable UUID id, @PathVariable UUID mapId) {
        return service().listEvidence(mapId).stream()
                .map(e -> new EvidenceResponse(e.id(), e.clauseMapId(), e.evidenceKind().name(),
                        e.evidenceId(), e.evidenceRef(), e.active(), CertificationService.DISCLAIMER))
                .toList();
    }

    @GetMapping("/evidence/{evidenceId}/clauses")
    @PreAuthorize("hasAuthority('cert:audit')")
    public List<ClauseMapResponse> listEvidenceClauses(@PathVariable UUID evidenceId) {
        return service().listEvidenceClauses(evidenceId).stream()
                .map(m -> new ClauseMapResponse(m.id(), m.activationId(), m.clauseId(), m.clauseNumber(),
                        m.titleSnapshot(), m.mapStatus(), CertificationService.DISCLAIMER))
                .toList();
    }

    @PostMapping("/activations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public ActivationResponse activate(@RequestBody ActivateInput in, JwtAuthenticationToken auth) {
        return toActivation(service().activate(
                new CertificationService.ActivateInput(in.standardCode(), in.scopeStatement(), in.edition()),
                actorId(auth)));
    }

    @PostMapping("/activations/{id}/approve")
    @PreAuthorize("hasAuthority('cert:approve')")
    public ActivationResponse approve(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toActivation(service().approve(id, actorId(auth)));
    }

    @PostMapping("/activations/{id}/accept")
    @PreAuthorize("hasAuthority('cert:approve')")
    public ActivationResponse accept(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return toActivation(service().accept(id, actorId(auth)));
    }

    @PostMapping("/activations/{id}/deactivate")
    @PreAuthorize("hasAuthority('cert:write')")
    public ActivationResponse deactivate(@PathVariable UUID id, @RequestBody(required = false) DeactivateInput in,
                                         JwtAuthenticationToken auth) {
        return toActivation(service().deactivate(id, actorId(auth), in == null ? null : in.reason()));
    }

    @PostMapping("/clause-map/{mapId}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cert:write')")
    public EvidenceResponse linkEvidence(@PathVariable UUID mapId, @RequestBody LinkEvidenceInput in,
                                         JwtAuthenticationToken auth) {
        CertificationService.EvidenceLinkView view = service().linkEvidence(mapId,
                new CertificationService.EvidenceInput(
                        CertificationService.EvidenceKind.from(in.evidenceKind()), in.evidenceId(), in.evidenceRef()),
                actorId(auth));
        return new EvidenceResponse(view.id(), view.clauseMapId(), view.evidenceKind().name(),
                view.evidenceId(), view.evidenceRef(), view.active(), CertificationService.DISCLAIMER);
    }

    @PostMapping("/evidence/{linkId}/deactivate")
    @PreAuthorize("hasAuthority('cert:write')")
    public EvidenceResponse deactivateEvidenceLink(@PathVariable UUID linkId, JwtAuthenticationToken auth) {
        CertificationService.EvidenceLinkView view = service().deactivateEvidenceLink(linkId, actorId(auth));
        return new EvidenceResponse(view.id(), view.clauseMapId(), view.evidenceKind().name(),
                view.evidenceId(), view.evidenceRef(), view.active(), CertificationService.DISCLAIMER);
    }
}
