package br.com.ziipsan.platform.orgscope.web;

import br.com.ziipsan.platform.orgscope.OrgAreaType;
import br.com.ziipsan.platform.orgscope.OrgScopeService;
import br.com.ziipsan.platform.orgscope.OrgUnit;
import br.com.ziipsan.platform.orgscope.UserScopeGrant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API de unidades organizacionais e escopo por área (P3.30). Toda rota
 * declara a sua permissão do catálogo da P0.13 (o RoutePermissionTest
 * recusa rota sem declaração):
 * <ul>
 *   <li>gestão de unidades, áreas e concessões — gestão de usuários do
 *       tenant: {@code user:read} para ler a estrutura e {@code user:write}
 *       para alterá-la (a régua fina de quem concede o quê é a GrantRule
 *       no serviço, não a rota);</li>
 *   <li>trâmite de protocolo — {@code protocol:write}: a permissão fina
 *       {@code protocol:transfer-any-unit} NÃO é exigida na rota porque ela
 *       é o desvio, não o caminho comum; quem a tem ou não é decidido pela
 *       ProtocolTransferRule no serviço;</li>
 *   <li>atribuição INICIAL de unidade ao protocolo (correção D2 da revisão
 *       independente) — também {@code protocol:write}, no mesmo padrão do
 *       trâmite: sem esta rota, um protocolo nunca receberia a primeira
 *       unidade pela API e o trâmite responderia protocol-without-unit
 *       para todos. A régua fina é a do serviço
 *       ({@code OrgScopeService.assignProtocolUnit}).</li>
 * </ul>
 * O ator de toda operação é o {@code sub} do token: concedente, autor do
 * trâmite e dono do escopo conferido são sempre ele, nunca um id do corpo.
 */
@RestController
@RequestMapping("/api/v1")
public class OrgScopeController {

    public record CreateUnitRequest(UUID parentId, String code, String name) {
    }

    public record LinkAreaRequest(OrgAreaType areaType, UUID areaId) {
    }

    public record GrantOrgUnitRequest(UUID userId, UUID orgUnitId) {
    }

    public record GrantAreaRequest(UUID userId, OrgAreaType areaType, UUID areaId) {
    }

    public record GrantUnrestrictedRequest(UUID userId) {
    }

    public record AssignUnitRequest(UUID orgUnitId) {
    }

    public record TransferRequest(UUID toOrgUnitId, String reason) {
    }

    public record TransferResult(UUID protocolId, UUID toOrgUnitId, List<UUID> movedOrderIds) {
    }

    private final OrgScopeService service;

    public OrgScopeController(OrgScopeService service) {
        this.service = service;
    }

    @GetMapping("/org-units")
    @PreAuthorize("hasAuthority('user:read')")
    public List<OrgUnit> listUnits() {
        return service.listUnits();
    }

    @PostMapping("/org-units")
    @PreAuthorize("hasAuthority('user:write')")
    public OrgUnit createUnit(@AuthenticationPrincipal(expression = "subject") String subject,
                              @RequestBody CreateUnitRequest request) {
        return service.createUnit(UUID.fromString(subject), request.parentId(), request.code(), request.name());
    }

    @PostMapping("/org-units/{id}/areas")
    @PreAuthorize("hasAuthority('user:write')")
    public void linkArea(@AuthenticationPrincipal(expression = "subject") String subject,
                         @PathVariable UUID id, @RequestBody LinkAreaRequest request) {
        service.linkArea(UUID.fromString(subject), id, request.areaType(), request.areaId());
    }

    @PostMapping("/org-scopes/org-unit")
    @PreAuthorize("hasAuthority('user:write')")
    public UserScopeGrant grantOrgUnit(@AuthenticationPrincipal(expression = "subject") String subject,
                                       @RequestBody GrantOrgUnitRequest request) {
        return service.grantOrgUnit(UUID.fromString(subject), request.userId(), request.orgUnitId());
    }

    @PostMapping("/org-scopes/area")
    @PreAuthorize("hasAuthority('user:write')")
    public UserScopeGrant grantArea(@AuthenticationPrincipal(expression = "subject") String subject,
                                    @RequestBody GrantAreaRequest request) {
        return service.grantArea(UUID.fromString(subject), request.userId(), request.areaType(), request.areaId());
    }

    @PostMapping("/org-scopes/unrestricted")
    @PreAuthorize("hasAuthority('user:write')")
    public UserScopeGrant grantUnrestricted(@AuthenticationPrincipal(expression = "subject") String subject,
                                            @RequestBody GrantUnrestrictedRequest request) {
        return service.grantUnrestricted(UUID.fromString(subject), request.userId());
    }

    @DeleteMapping("/org-scopes/{grantId}")
    @PreAuthorize("hasAuthority('user:write')")
    public void revoke(@AuthenticationPrincipal(expression = "subject") String subject,
                       @PathVariable UUID grantId) {
        service.revokeGrant(UUID.fromString(subject), grantId);
    }

    @PostMapping("/org-units/{id}/deactivate")
    @PreAuthorize("hasAuthority('user:write')")
    public OrgUnit deactivateUnit(@AuthenticationPrincipal(expression = "subject") String subject,
                                  @PathVariable UUID id) {
        return service.deactivateUnit(UUID.fromString(subject), id);
    }

    /** Atribuição INICIAL de unidade ao protocolo (correção D2 — não é trâmite: não há origem). */
    @PostMapping("/protocols/{id}/org-unit")
    @PreAuthorize("hasAuthority('protocol:write')")
    public void assignUnit(@AuthenticationPrincipal(expression = "subject") String subject,
                           @PathVariable UUID id, @RequestBody AssignUnitRequest request) {
        service.assignProtocolUnit(UUID.fromString(subject), id, request.orgUnitId());
    }

    @PostMapping("/protocols/{id}/transfer")
    @PreAuthorize("hasAuthority('protocol:write')")
    public TransferResult transfer(@AuthenticationPrincipal(expression = "subject") String subject,
                                   @PathVariable UUID id, @RequestBody TransferRequest request) {
        List<UUID> moved = service.transferProtocol(
                UUID.fromString(subject), id, request.toOrgUnitId(), request.reason());
        return new TransferResult(id, request.toOrgUnitId(), moved);
    }
}
