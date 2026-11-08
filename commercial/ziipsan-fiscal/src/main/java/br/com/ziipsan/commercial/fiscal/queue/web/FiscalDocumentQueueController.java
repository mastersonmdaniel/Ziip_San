package br.com.ziipsan.commercial.fiscal.queue.web;

import br.com.ziipsan.commercial.fiscal.queue.FiscalOperatorAction;
import br.com.ziipsan.commercial.fiscal.queue.FiscalOperatorPermissions;
import br.com.ziipsan.commercial.fiscal.queue.FiscalOperatorService;
import br.com.ziipsan.commercial.fiscal.queue.FiscalQueueItem;
import br.com.ziipsan.commercial.fiscal.queue.FiscalQueueQuery;
import br.com.ziipsan.commercial.fiscal.queue.FiscalQueueService;
import br.com.ziipsan.commercial.fiscal.queue.FiscalQueueState;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API da tela "Documentos fiscais" do tenant (item de menu, P2.33): a fila com
 * filtros por estado, competência e grupo, e as três ações do operador — cada uma
 * com a sua permissão própria conferida no endpoint E no serviço. O tenant vem do
 * contexto de segurança/RLS na integração (o serviço recebe o tenantId resolvido
 * pelo chamador da camada web do tenant, como nos demais controllers do produto);
 * aqui o {@code subject} é o autor gravado na trilha. A permissão de cada ação é
 * imposta pelo {@code @PreAuthorize} do endpoint; ao serviço, o controller passa o
 * conjunto com a permissão da própria ação (a que o endpoint acabou de exigir) —
 * chamadas programáticas ao serviço continuam conferidas por ele mesmo.
 *
 * <p>FRONTEIRA: a visão agregada da Ziip na Central é da P2.11 — esta API é só a
 * fila operada pelo tenant.
 */
@RestController
@RequestMapping("/api/v1/fiscal/documents")
@PreAuthorize("isAuthenticated()")
public class FiscalDocumentQueueController {

    public record ActionRequest(String reason, UUID contractResponsibleId) {
    }

    public record VoidingRequest(UUID issuerId, int serie, long numberFrom, long numberTo,
                                 String reason, int tpAmb) {
    }

    private final FiscalQueueService queueService;
    private final FiscalOperatorService operatorService;
    private final UUID tenantId;

    public FiscalDocumentQueueController(FiscalQueueService queueService,
                                         FiscalOperatorService operatorService, UUID tenantId) {
        this.queueService = queueService;
        this.operatorService = operatorService;
        this.tenantId = tenantId;
    }

    @GetMapping
    @PreAuthorize(FiscalDocumentQueueRoles.READ)
    public List<FiscalQueueItem> queue(@RequestParam(required = false) FiscalQueueState state,
                                       @RequestParam(required = false) YearMonth competence,
                                       @RequestParam(required = false) UUID billingGroupId) {
        return queueService.queue(tenantId, new FiscalQueueQuery(state, competence, billingGroupId));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize(FiscalDocumentQueueRoles.RESEND)
    public FiscalOperatorAction resend(@AuthenticationPrincipal(expression = "subject") String subject,
                                       @PathVariable UUID id, @RequestBody ActionRequest request) {
        return operatorService.resend(tenantId, id, UUID.fromString(subject),
                Set.of(FiscalOperatorPermissions.RESEND), request.reason());
    }

    @PostMapping("/voidings")
    @PreAuthorize(FiscalDocumentQueueRoles.VOID_NUMBER)
    public FiscalOperatorAction voidNumbers(@AuthenticationPrincipal(expression = "subject") String subject,
                                            @RequestBody VoidingRequest request) {
        return operatorService.voidNumbers(tenantId, request.issuerId(), request.serie(),
                request.numberFrom(), request.numberTo(), UUID.fromString(subject),
                Set.of(FiscalOperatorPermissions.VOID_NUMBER), request.reason(), request.tpAmb());
    }

    @PostMapping("/{id}/cancellation-requests")
    @PreAuthorize(FiscalDocumentQueueRoles.REQUEST_CANCELLATION)
    public FiscalOperatorAction requestCancellation(
            @AuthenticationPrincipal(expression = "subject") String subject,
            @PathVariable UUID id, @RequestBody ActionRequest request) {
        return operatorService.requestCancellation(tenantId, id, UUID.fromString(subject),
                Set.of(FiscalOperatorPermissions.REQUEST_CANCELLATION), request.reason(),
                request.contractResponsibleId());
    }
}
