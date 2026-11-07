package br.com.ziipsan.platform.field.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.field.AppDevice;
import br.com.ziipsan.platform.field.DeviceArea;
import br.com.ziipsan.platform.field.DeviceDirective;
import br.com.ziipsan.platform.field.FieldDeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Area do tenant para aparelhos do app de campo (ED-18): listar, aprovar, bloquear e
 * retirar. A Central usa os mesmos servicos com {@code area = CENTRAL} a partir do seu
 * console (a rota da Central vive no console da plataforma, nao aqui). A permissao fina
 * segue o catalogo recurso:acao do projeto (a amarracao @PreAuthorize entra na integracao,
 * com a permissao de aparelhos criada no catalogo — ver relatorio da ED-18).
 *
 * <p>Rotas de negocio sob /api/v1 (P0.10). O {@code sub} do usuario do aparelho vem do
 * token de quem registra o aparelho em campo (o principal autenticado), nunca do corpo: o corpo so carrega
 * o que o aparelho declara sobre si (versao e atestacao).
 */
@RestController
@RequestMapping("/api/v1/field-devices")
public class FieldDeviceController {

    public record RegisterRequest(@NotNull UUID deviceId, String appVersion, String attestationVerdict) {
    }

    public record StatusRequest(String reason) {
    }

    public record DeviceView(UUID id, UUID deviceId, String status, String directive,
                             OffsetDateTime approvedAt, OffsetDateTime lastContactAt, String appVersion) {
        static DeviceView of(AppDevice d) {
            return new DeviceView(d.getId(), d.getDeviceId(), d.getStatus().name(),
                    DeviceDirective.forStatus(d.getStatus()).name(),
                    d.getApprovedAt(), d.getLastContactAt(), d.getAppVersion());
        }
    }

    private final FieldDeviceService service;

    public FieldDeviceController(FieldDeviceService service) {
        this.service = service;
    }

    @PostMapping
    public DeviceView register(@RequestBody @Valid RegisterRequest request, Principal principal) {
        // O `sub` e o nome do principal autenticado (token do realm ziipsan): nunca vem do corpo.
        AppDevice device = service.register(TenantContextHolder.getTenantId(), request.deviceId(),
                principal.getName(), request.appVersion(), request.attestationVerdict(), OffsetDateTime.now());
        return DeviceView.of(device);
    }

    @GetMapping
    public List<DeviceView> list() {
        return service.list(TenantContextHolder.getTenantId()).stream().map(DeviceView::of).toList();
    }

    @PostMapping("/{deviceId}/approve")
    public DeviceView approve(@PathVariable UUID deviceId) {
        return DeviceView.of(service.approve(TenantContextHolder.getTenantId(), deviceId,
                TenantContextHolder.getUserId(), DeviceArea.TENANT, OffsetDateTime.now()));
    }

    @PostMapping("/{deviceId}/block")
    public DeviceView block(@PathVariable UUID deviceId, @RequestBody(required = false) StatusRequest request) {
        return DeviceView.of(service.block(TenantContextHolder.getTenantId(), deviceId,
                TenantContextHolder.getUserId(), DeviceArea.TENANT,
                request == null ? null : request.reason(), OffsetDateTime.now()));
    }

    @PostMapping("/{deviceId}/retire")
    public DeviceView retire(@PathVariable UUID deviceId, @RequestBody(required = false) StatusRequest request) {
        return DeviceView.of(service.retire(TenantContextHolder.getTenantId(), deviceId,
                TenantContextHolder.getUserId(), DeviceArea.TENANT,
                request == null ? null : request.reason(), OffsetDateTime.now()));
    }
}
