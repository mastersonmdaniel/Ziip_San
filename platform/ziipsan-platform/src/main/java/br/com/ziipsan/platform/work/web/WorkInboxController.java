package br.com.ziipsan.platform.work.web;

import br.com.ziipsan.platform.work.ApprovalTask;
import br.com.ziipsan.platform.work.DueFilter;
import br.com.ziipsan.platform.work.WorkDelegation;
import br.com.ziipsan.platform.work.WorkInboxService;
import br.com.ziipsan.platform.work.WorkItem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API da caixa "Minhas tarefas" (P2.28), no mesmo espírito do {@code /api/v1/me/...} da
 * central (P3.36): o usuário SÓ enxerga e SÓ age sobre a SUA caixa — o {@code sub} do
 * token é o dono de toda leitura/escrita, e o serviço ainda filtra por ele (defesa em
 * profundidade com a RLS). O item de menu "Minhas tarefas" do frontend aponta para a
 * página que consome esta API.
 *
 * <p>Decidir aprovação por aqui é a mesma porta do serviço ({@code decideApproval}):
 * não há endpoint, parâmetro ou papel que pule a segregação solicitante != aprovador.
 */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class WorkInboxController {

    public record DecisionRequest(boolean approved, String reason) {
    }

    public record DelegationRequest(UUID delegateId, Instant startsAt, Instant endsAt,
                                    Set<String> grantedPermissions, String reason) {
    }

    public record InboxCount(long count) {
    }

    private final WorkInboxService service;

    public WorkInboxController(WorkInboxService service) {
        this.service = service;
    }

    @GetMapping("/work-items")
    public List<WorkItem> inbox(@AuthenticationPrincipal(expression = "subject") String subject,
                                @RequestParam(defaultValue = "ALL") DueFilter filter,
                                @RequestParam(defaultValue = "50") int limit) {
        return service.listInbox(UUID.fromString(subject), filter, limit);
    }

    @GetMapping("/work-items/count")
    public InboxCount count(@AuthenticationPrincipal(expression = "subject") String subject,
                            @RequestParam(defaultValue = "ALL") DueFilter filter) {
        return new InboxCount(service.countInbox(UUID.fromString(subject), filter));
    }

    @PostMapping("/work-items/{id}/start")
    public WorkItem start(@AuthenticationPrincipal(expression = "subject") String subject,
                          @PathVariable UUID id) {
        return service.startItem(id, UUID.fromString(subject));
    }

    @PostMapping("/work-items/{id}/decision")
    public ApprovalTask decide(@AuthenticationPrincipal(expression = "subject") String subject,
                               @PathVariable UUID id, @RequestBody DecisionRequest request) {
        return service.decideApproval(id, UUID.fromString(subject), request.approved(), request.reason());
    }

    @GetMapping("/work-delegations/received")
    public List<WorkDelegation> delegationsReceived(@AuthenticationPrincipal(expression = "subject") String subject) {
        return service.delegationsReceivedBy(UUID.fromString(subject));
    }

    @GetMapping("/work-delegations/sent")
    public List<WorkDelegation> delegationsSent(@AuthenticationPrincipal(expression = "subject") String subject) {
        return service.delegationsSentBy(UUID.fromString(subject));
    }

    @PostMapping("/work-delegations")
    public WorkDelegation delegate(@AuthenticationPrincipal(expression = "subject") String subject,
                                   @RequestBody DelegationRequest request) {
        return service.delegate(UUID.fromString(subject), request.delegateId(), request.startsAt(),
                request.endsAt(), request.grantedPermissions(), request.reason());
    }

    @PostMapping("/work-delegations/{id}/revoke")
    public WorkDelegation revoke(@AuthenticationPrincipal(expression = "subject") String subject,
                                 @PathVariable UUID id) {
        return service.revokeDelegation(id, UUID.fromString(subject));
    }
}
