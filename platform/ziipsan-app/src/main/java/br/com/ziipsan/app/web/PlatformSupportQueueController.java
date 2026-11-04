package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.support.TicketService;
import br.com.ziipsan.platform.documents.TicketAttachmentService;
import br.com.ziipsan.platform.infra.events.BusinessEventPublisher;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fila de chamados na Central Ziip, lado do operador (P1.23; AJUDA §6, lado
 * do operador; CENTRAL-ZIIP §7).
 *
 * <p>Esta é a ALIMENTAÇÃO da fila, não a Central: a Central (P1.17, frente
 * paralela) é dona das abas, dos painéis e da agregação entre tenants — nada
 * disso é duplicado aqui. O que esta classe entrega, por tenant explícito:
 * a fila filtrada, o detalhe com notas internas, a resposta do agente, a
 * mudança de estado/severidade/nível/responsável e a varredura do anexo.
 * A agregação entre tenants (a Central chamar esta fila para cada tenant do
 * seu painel) é da integração com a P1.17, que reconcilia as duas frentes.
 *
 * <p>Acesso: token de plataforma com {@code support:queue} (OWNER, OPS e
 * SUPPORT; FINANCE, não — ver PlatformRole). O filtro de plataforma já
 * recusou token com tenant aqui. A Central NÃO lê o schema do tenant fora
 * desta API (ADR-0020, item da emergência à parte, que é P3.17).
 */
@RestController
@RequestMapping("/api/v1/platform/support")
@PreAuthorize("hasAuthority('support:queue')")
public class PlatformSupportQueueController {

    public record AgentUpdateInput(String status, String severity, String level, UUID assigneeId) {
    }

    public record AgentMessageInput(String body, boolean internal) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;
    private final TicketAttachmentService attachments;
    private final FileScanner scanner;
    private final BusinessEventPublisher events;

    public PlatformSupportQueueController(DataSource dataSource, TenantCatalog catalog,
                                          TicketAttachmentService attachments, FileScanner scanner,
                                          BusinessEventPublisher events) {
        this.dataSource = dataSource;
        this.catalog = catalog;
        this.attachments = attachments;
        this.scanner = scanner;
        this.events = events;
    }

    private TicketService service(UUID tenantId) {
        catalog.find(tenantId)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new TicketService(dataSource, tenantId, Clock.systemUTC());
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o agente.");
        }
    }

    @GetMapping("/queue")
    public List<TicketService.Ticket> listQueue(@RequestParam UUID tenantId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String severity) {
        return service(tenantId).queue(status, severity);
    }

    @GetMapping("/tickets/{number}")
    public TicketService.TicketDetail getQueuedTicket(@RequestParam UUID tenantId, @PathVariable String number) {
        return service(tenantId).queueGet(number);
    }

    @PatchMapping("/tickets/{number}")
    public TicketService.Ticket updateQueuedTicket(@RequestParam UUID tenantId, @PathVariable String number,
                                       @RequestBody AgentUpdateInput in, JwtAuthenticationToken auth) {
        TicketService service = service(tenantId);
        TicketService.Ticket before = service.queueGet(number).ticket();
        TicketService.Ticket updated = service.agentUpdate(number, actorId(auth),
                in.status(), in.severity(), in.level(), in.assigneeId());
        if (in.status() != null && !in.status().equals(before.status())) {
            events.publish(tenantId, "support.ticket.status", 1, Json.write(Map.of(
                    "ticketId", before.id().toString(), "ticketNumber", before.number(),
                    "tenantId", tenantId.toString(), "fromStatus", before.status(),
                    "toStatus", updated.status())));
        }
        return updated;
    }

    @PostMapping("/tickets/{number}/messages")
    public TicketService.TicketMessage postQueuedMessage(@RequestParam UUID tenantId, @PathVariable String number,
                                               @RequestBody AgentMessageInput in, JwtAuthenticationToken auth) {
        TicketService service = service(tenantId);
        TicketService.Ticket ticket = service.queueGet(number).ticket();
        TicketService.TicketMessage message = service.addAgentMessage(number, actorId(auth),
                in.body(), in.internal());
        events.publish(tenantId, "support.ticket.message", 1, Json.write(Map.of(
                "ticketId", ticket.id().toString(), "ticketNumber", ticket.number(),
                "tenantId", tenantId.toString(), "messageId", message.id().toString(),
                "authorType", "AGENT", "visibility", message.visibility())));
        return message;
    }

    /** Varredura de um anexo em quarentena, pelo varredor do ambiente (P1.20). */
    @PostMapping("/attachments/{attachmentId}/scan")
    public TicketService.TicketAttachment scanQueuedAttachment(@RequestParam UUID tenantId,
                                               @PathVariable UUID attachmentId) {
        TicketService service = service(tenantId);
        TicketService.Ticket ticket = service.ticketOfAttachment(attachmentId);
        return attachments.scan(service, attachmentId, ticket.number(), scanner);
    }
}
