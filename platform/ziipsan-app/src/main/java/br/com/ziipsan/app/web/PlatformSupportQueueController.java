package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.TenantTime;
import br.com.ziipsan.core.support.SupportSlaService;
import br.com.ziipsan.core.support.TicketService;
import br.com.ziipsan.platform.documents.TicketAttachmentService;
import br.com.ziipsan.platform.infra.events.BusinessEventPublisher;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public record ApplySlaInput(String policyKey) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;
    private final TicketAttachmentService attachments;
    private final FileScanner scanner;
    private final BusinessEventPublisher events;

    public PlatformSupportQueueController(DataSource dataSource, TenantCatalog catalog,
                                          TicketAttachmentService attachments, @Qualifier("ticketFileScanner") FileScanner scanner,
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

    private SupportSlaService slaService(UUID tenantId) {
        catalog.find(tenantId)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new SupportSlaService(dataSource, tenantId, Clock.systemUTC());
    }

    /** Fuso do cálculo de SLA: o padrão de fábrica (P0.19); ler o tenant.timezone vigente é pendência da P2.15. */
    private static ZoneId zone() {
        return TenantTime.FACTORY_DEFAULT;
    }

    /**
     * Feriados nacionais do schema do tenant (P2.15): sem localidade
     * conhecida no catálogo, só eles contam — a convenção do
     * DeadlineCalculator. O feriado municipal entra pelo conjunto
     * explícito do serviço, não por esta carga.
     */
    private Set<LocalDate> nationalHolidays(UUID tenantId) {
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElse(null);
        if (schema == null) {
            return Set.of();
        }
        String safe = schema.replace("\"", "");
        Set<LocalDate> out = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT holiday_date FROM \"" + safe + "\".holidays"
                             + " WHERE scope = 'NATIONAL' AND kind = 'HOLIDAY'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getDate(1).toLocalDate());
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao carregar os feriados do tenant: " + e.getMessage(), e);
        }
        return out;
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
        SupportSlaService sla = slaService(tenantId);
        SupportSlaService.TicketSlaView slaBefore = sla.slaOf(number);
        TicketService.Ticket updated = service.agentUpdate(number, actorId(auth),
                in.status(), in.severity(), in.level(), in.assigneeId());
        if (in.status() != null && !in.status().equals(before.status())) {
            events.publish(tenantId, "support.ticket.status", 1, Json.write(Map.of(
                    "ticketId", before.id().toString(), "ticketNumber", before.number(),
                    "tenantId", tenantId.toString(), "fromStatus", before.status(),
                    "toStatus", updated.status())));
            // P2.15: WAITING_CUSTOMER pausa o relógio de SLA; sair dele
            // retoma e estende os prazos pelas horas úteis da pausa.
            OffsetDateTime now = OffsetDateTime.now(Clock.systemUTC());
            if ("WAITING_CUSTOMER".equals(updated.status()) && !slaBefore.pauseOpen()) {
                sla.startPause(number, now);
            } else if ("WAITING_CUSTOMER".equals(before.status()) && slaBefore.pauseOpen()) {
                sla.endPauseAndExtend(number, now, nationalHolidays(tenantId), zone());
            }
        }
        return updated;
    }

    /**
     * Aplica a política de SLA ao chamado (P2.15): calcula primeira
     * resposta e solução a partir da abertura, no calendário da política.
     */
    @PostMapping("/tickets/{number}/sla")
    public SupportSlaService.TicketSlaView applySla(@RequestParam UUID tenantId, @PathVariable String number,
                                                    @RequestBody ApplySlaInput in) {
        service(tenantId);
        return slaService(tenantId).applyToTicket(number, in.policyKey(), nationalHolidays(tenantId), zone());
    }

    /** Prazos e nível de alerta do chamado (P2.15), sem gravar nada. */
    @GetMapping("/tickets/{number}/sla")
    public SupportSlaService.SlaEvaluation getSla(@RequestParam UUID tenantId, @PathVariable String number) {
        service(tenantId);
        return slaService(tenantId).evaluate(number, OffsetDateTime.now(Clock.systemUTC()),
                nationalHolidays(tenantId), zone());
    }

    /**
     * Avaliação de alertas com registro (P2.15): WARNING a 80% e BREACHED
     * no estouro entram na trilha do chamado, uma vez por nível. O
     * acionamento periódico (job) é pendência declarada; até lá, a
     * Central dispara esta verificação.
     */
    @PostMapping("/tickets/{number}/sla/alerts")
    public SupportSlaService.SlaEvaluation checkSlaAlerts(@RequestParam UUID tenantId, @PathVariable String number) {
        service(tenantId);
        return slaService(tenantId).evaluateAndRecord(number, OffsetDateTime.now(Clock.systemUTC()),
                nationalHolidays(tenantId), zone());
    }

    @PostMapping("/tickets/{number}/messages")
    public TicketService.TicketMessage postQueuedMessage(@RequestParam UUID tenantId, @PathVariable String number,
                                               @RequestBody AgentMessageInput in, JwtAuthenticationToken auth) {
        TicketService service = service(tenantId);
        TicketService.Ticket ticket = service.queueGet(number).ticket();
        TicketService.TicketMessage message = service.addAgentMessage(number, actorId(auth),
                in.body(), in.internal());
        // P2.15: a primeira mensagem pública do agente é a primeira
        // resposta (FRT); nota interna não conta.
        if ("PUBLIC".equals(message.visibility())) {
            slaService(tenantId).markFirstResponse(number, OffsetDateTime.now(Clock.systemUTC()));
        }
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
