package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import br.com.ziipsan.core.protocol.ProtocolService;
import br.com.ziipsan.core.protocol.ProtocolStatus;
import br.com.ziipsan.core.protocol.RequesterRole;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Protocolo de atendimento (P1.36, NR 11, art. 52, §1º): abertura,
 * acompanhamento, encerramento com motivo classificado, prorrogação
 * como evento e a geração da OS a partir do protocolo.
 *
 * <p>Autenticação: token de tenant (o filtro já recusou token de
 * plataforma aqui e resolveu o schema pelo catálogo). Permissão fina:
 * {@code protocol:read} para leitura, {@code protocol:write} para
 * escrita — concessão direta, nenhum perfil de fábrica (perfis por
 * função chegam na P1.37, como {@code gis:catalog-*} e
 * {@code parameters:manage}).
 *
 * <p>Termo: sempre "protocolo de atendimento". O chamado de suporte
 * {@code SUP-AAAA-NNNNNN} da P1.23 (COE-23) é outra entidade, com
 * outra numeração, e nunca aparece nestas rotas.
 *
 * <p>A validação condicional (solicitante sem cadastro exige nome e
 * contato; especificação que exige matrícula exige unidade; sem
 * unidade, o ponto é obrigatório) vive no serviço, não nas anotações:
 * {@code @NotBlank} marca só o que é incondicionalmente obrigatório.
 */
@RestController
@RequestMapping("/api/v1/protocols")
public class ProtocolController {

    public record OpenProtocolRequest(
            @NotBlank String specCode,
            @NotNull ProtocolChannel channel,
            UUID consumerUnitId,
            String locationText,
            UUID requesterCustomerId,
            String requesterName,
            String requesterContact,
            @NotNull RequesterRole requesterRole,
            LocalDate openedDate,
            UUID duplicateOfProtocolId,
            String agencyReference,
            String stateCode,
            String municipalityIbge,
            boolean includeOptionalPoints) {
    }

    public record CloseProtocolRequest(@NotBlank String closeReasonCode) {
    }

    public record CancelProtocolRequest(String closeReasonCode) {
    }

    public record ExtendProtocolRequest(
            @NotNull Integer additionalDays,
            @NotBlank String reason,
            @NotBlank String notice) {
    }

    public record CreateOrderRequest(
            @NotBlank String serviceTypeCode,
            @NotNull ServiceOrderEnums.ExecutionMode executionMode,
            ServiceOrderEnums.Priority priority) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;

    public ProtocolController(DataSource dataSource, TenantCatalog catalog) {
        this.dataSource = dataSource;
        this.catalog = catalog;
    }

    private ProtocolService protocolService() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new ProtocolService(dataSource, schema, tenantId);
    }

    private ServiceOrderService orderService() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new ServiceOrderService(dataSource, schema, tenantId);
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ProtocolView open(@Valid @RequestBody OpenProtocolRequest in,
                                             JwtAuthenticationToken auth) {
        return protocolService().open(new ProtocolService.OpenInput(
                in.specCode(), in.channel(), in.consumerUnitId(), in.locationText(),
                in.requesterCustomerId(), in.requesterName(), in.requesterContact(),
                in.requesterRole(), in.openedDate(), actorId(auth),
                in.duplicateOfProtocolId(), in.agencyReference(),
                in.stateCode(), in.municipalityIbge(), in.includeOptionalPoints()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('protocol:read')")
    public ProtocolService.ProtocolView get(@PathVariable("id") UUID id) {
        return protocolService().get(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('protocol:read')")
    public List<ProtocolService.ProtocolView> list(@RequestParam(value = "status", required = false) ProtocolStatus status) {
        return protocolService().list(status);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ProtocolView start(@PathVariable("id") UUID id, JwtAuthenticationToken auth) {
        return protocolService().startProgress(id, actorId(auth));
    }

    @PostMapping("/{id}/answer-pending")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ProtocolView answerPending(@PathVariable("id") UUID id, JwtAuthenticationToken auth) {
        return protocolService().markAnswerPending(id, actorId(auth));
    }

    @PostMapping("/{id}/answer")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ProtocolView answer(@PathVariable("id") UUID id, JwtAuthenticationToken auth) {
        return protocolService().markAnswered(id, actorId(auth));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ProtocolView close(@PathVariable("id") UUID id,
                                              @Valid @RequestBody CloseProtocolRequest in,
                                              JwtAuthenticationToken auth) {
        return protocolService().close(id, in.closeReasonCode(), actorId(auth));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ProtocolView cancel(@PathVariable("id") UUID id,
                                               @RequestBody(required = false) CancelProtocolRequest in,
                                               JwtAuthenticationToken auth) {
        return protocolService().cancel(id, in == null ? null : in.closeReasonCode(), actorId(auth));
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ProtocolService.ExtensionView extend(@PathVariable("id") UUID id,
                                                @Valid @RequestBody ExtendProtocolRequest in,
                                                JwtAuthenticationToken auth) {
        return protocolService().extend(id, in.additionalDays(), in.reason(), actorId(auth), in.notice());
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAuthority('protocol:read')")
    public List<ProtocolService.OrderRef> orders(@PathVariable("id") UUID id) {
        return protocolService().ordersOf(id);
    }

    /**
     * Toda solicitação externa nasce como protocolo e gera a OS
     * (ADR-0039, item 7): a OS nasce com origem EXTERNAL e este
     * protocolo; o motor recusa EXTERNAL sem protocolo e INTERNAL com
     * protocolo, e a FK (V8_1 + repetível) amarra o vínculo 1:N.
     */
    @PostMapping("/{id}/orders")
    @PreAuthorize("hasAuthority('protocol:write')")
    public ServiceOrderService.OrderView createOrder(@PathVariable("id") UUID id,
                                                     @Valid @RequestBody CreateOrderRequest in,
                                                     JwtAuthenticationToken auth) {
        protocolService().get(id);
        return orderService().createOrder(new ServiceOrderService.OrderInput(
                in.serviceTypeCode(), ServiceOrderEnums.Origin.EXTERNAL, in.executionMode(),
                in.priority(), actorId(auth), id, null, null, null));
    }
}
