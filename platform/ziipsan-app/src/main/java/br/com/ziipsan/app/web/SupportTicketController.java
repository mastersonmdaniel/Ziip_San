package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.support.TicketService;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.documents.TicketAttachmentService;
import br.com.ziipsan.platform.infra.events.BusinessEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.InputStream;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chamados de suporte do tenant (P1.23; AJUDA §6, lado do tenant).
 *
 * <p>Autenticação: token de tenant (o filtro já recusou token de plataforma
 * aqui). Permissões (concessão direta, P1.37 ainda não distribui perfis):
 * {@code support:write} abre e responde os próprios chamados;
 * {@code support:read} é a porta das leituras (lista, detalhe, download) e
 * vê os próprios; quem tem {@code support:manage} (concedido junto com
 * {@code support:read}, como em todo o projeto uma permissão por rota)
 * vê todos os do tenant — a distinção vive em {@code canSeeAll}, não no
 * gate. Número de outro tenant é 404: a linha não existe no contexto RLS
 * do solicitante.
 *
 * <p>Notificações: cada mutação publica um evento na outbox
 * ({@code support.ticket.*@v1}, só identificadores e códigos — NUNCA o corpo
 * da conversa nem o anexo). O e-mail de aviso (protocolo, estado e link, sem
 * conteúdo nem anexo, ADR-0020 decisão 5) é renderizado pelo consumidor da
 * outbox a partir do evento; como a carga não carrega conteúdo, o e-mail não
 * tem como vazar conversa ou anexo. O consumidor de e-mail do worker ainda
 * não existe neste código — é pendência declarada da integração, não um
 * envio fingido aqui.
 *
 * <p>A API REST de ajuda ({@code /api/v1/help/...}, AJUDA §6) NÃO está aqui:
 * ela é pendência declarada de outra frente — esta classe entrega SÓ
 * chamados e a tela /support.
 */
@RestController
@RequestMapping("/api/v1/support/tickets")
public class SupportTicketController {

    public record CreateInput(String subject, String category, String module, String originRoute,
                              String appVersion, String correlationId, String problemCode,
                              String severity, String initialMessage) {
    }

    public record MessageInput(String body) {
    }

    public record AttachmentInput(UUID messageId, String fileName, String contentBase64) {
    }

    private final DataSource dataSource;
    private final TicketAttachmentService attachments;
    private final BusinessEventPublisher events;

    public SupportTicketController(DataSource dataSource, TicketAttachmentService attachments,
                                   BusinessEventPublisher events) {
        this.dataSource = dataSource;
        this.attachments = attachments;
        this.events = events;
    }

    private TicketService service() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        return new TicketService(dataSource, tenantId, Clock.systemUTC());
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    private static boolean canSeeAll(JwtAuthenticationToken auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority().equals("support:manage")) {
                return true;
            }
        }
        return false;
    }

    private static String payload(Map<String, Object> fields) {
        return Json.write(fields);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('support:write')")
    public TicketService.Ticket createTicket(@RequestBody CreateInput in, JwtAuthenticationToken auth) {
        UUID tenantId = TenantContextHolder.getTenantId();
        TicketService.Ticket ticket = service().create(actorId(auth), new TicketService.CreateInput(
                in.subject(), in.category(), in.module(), in.originRoute(), in.appVersion(),
                in.correlationId(), in.problemCode(), in.severity(), in.initialMessage()));
        events.publish(tenantId, "support.ticket.created", 1, payload(Map.of(
                "ticketId", ticket.id().toString(), "ticketNumber", ticket.number(),
                "tenantId", tenantId.toString(), "status", ticket.status(),
                "category", ticket.category(), "severity", ticket.severity())));
        return ticket;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('support:read')")
    public List<TicketService.Ticket> listTickets(@RequestParam(required = false) String status,
                                           JwtAuthenticationToken auth) {
        return service().list(actorId(auth), canSeeAll(auth), status);
    }

    @GetMapping("/{number}")
    @PreAuthorize("hasAuthority('support:read')")
    public TicketService.TicketDetail getTicket(@PathVariable String number, JwtAuthenticationToken auth) {
        return service().get(number, actorId(auth), canSeeAll(auth));
    }

    @PostMapping("/{number}/messages")
    @PreAuthorize("hasAuthority('support:write')")
    public TicketService.TicketMessage postTicketMessage(@PathVariable String number, @RequestBody MessageInput in,
                                               JwtAuthenticationToken auth) {
        UUID tenantId = TenantContextHolder.getTenantId();
        TicketService service = service();
        TicketService.Ticket before = service.get(number, actorId(auth), canSeeAll(auth)).ticket();
        TicketService.TicketMessage message = service.addRequesterMessage(number, actorId(auth), in.body());
        events.publish(tenantId, "support.ticket.message", 1, payload(Map.of(
                "ticketId", before.id().toString(), "ticketNumber", before.number(),
                "tenantId", tenantId.toString(), "messageId", message.id().toString(),
                "authorType", "REQUESTER", "visibility", "PUBLIC")));
        if (before.status().equals("RESOLVED")) {
            events.publish(tenantId, "support.ticket.status", 1, payload(Map.of(
                    "ticketId", before.id().toString(), "ticketNumber", before.number(),
                    "tenantId", tenantId.toString(), "fromStatus", "RESOLVED", "toStatus", "IN_PROGRESS")));
        }
        return message;
    }

    @PostMapping("/{number}/cancel")
    @PreAuthorize("hasAuthority('support:write')")
    public TicketService.Ticket cancelTicket(@PathVariable String number, JwtAuthenticationToken auth) {
        UUID tenantId = TenantContextHolder.getTenantId();
        TicketService.Ticket ticket = service().cancel(number, actorId(auth));
        events.publish(tenantId, "support.ticket.status", 1, payload(Map.of(
                "ticketId", ticket.id().toString(), "ticketNumber", ticket.number(),
                "tenantId", tenantId.toString(), "fromStatus", "NEW", "toStatus", "CANCELLED")));
        return ticket;
    }

    @PostMapping("/{number}/attachments")
    @PreAuthorize("hasAuthority('support:write')")
    public TicketService.TicketAttachment uploadTicketAttachment(@PathVariable String number,
                                                     @RequestBody AttachmentInput in,
                                                     JwtAuthenticationToken auth) {
        TicketService service = service();
        TicketService.TicketDetail detail = service.get(number, actorId(auth), canSeeAll(auth));
        boolean messageBelongs = detail.messages().stream().anyMatch(m -> m.id().equals(in.messageId()));
        if (!messageBelongs) {
            throw new ApiException.NotFound("A mensagem não existe neste chamado.");
        }
        byte[] content;
        try {
            content = in.contentBase64() == null ? null : Base64.getDecoder().decode(in.contentBase64());
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("contentBase64", "invalid", "Conteúdo em base64 inválido."));
        }
        if (content == null) {
            throw ApiException.Validation.of(new ApiException.FieldError("contentBase64", "required", "Campo obrigatório."));
        }
        // Transporte v0: JSON com base64, sob o limite de corpo de 1 MiB
        // (não há multipart no código). Anexo grande pela API REST v0 só
        // depois do transporte multipart — pendência declarada.
        return attachments.upload(service, detail.ticket().id(), in.messageId(), number, in.fileName(), content);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAuthority('support:read')")
    public ResponseEntity<InputStreamResource> downloadTicketAttachment(@PathVariable UUID attachmentId,
                                                        JwtAuthenticationToken auth) {
        TicketService service = service();
        TicketService.Ticket owner = service.ticketOfAttachment(attachmentId);
        if (!canSeeAll(auth) && !owner.requesterId().equals(actorId(auth))) {
            throw new ApiException.NotFound("O anexo não existe.");
        }
        TicketService.TicketAttachment attachment = service.attachmentForDownload(attachmentId);
        InputStream bytes = attachments.download(attachment);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.fileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(attachment.detectedMime()))
                .contentLength(attachment.sizeBytes())
                .body(new InputStreamResource(bytes));
    }
}
