package br.com.ziipsan.operations.metering.field.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.operations.metering.field.FieldReadingSubmission;
import br.com.ziipsan.operations.metering.field.FieldDeviceGate;
import br.com.ziipsan.operations.metering.field.FieldSyncContext;
import br.com.ziipsan.operations.metering.field.FieldSyncReceipt;
import br.com.ziipsan.operations.metering.field.FieldSyncContextProvider;
import br.com.ziipsan.operations.metering.field.FieldSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sincronizacao de campo (ED-18), sob /api/v1 (P0.10). O aparelho se identifica pelo
 * cabecalho X-Device-Id alem do token do usuario (realm ziipsan): os dois sao
 * conferidos — o token diz quem e, o registro diz se ESTE aparelho pode sincronizar.
 *
 * <ul>
 *   <li>GET /directive: contato leve; devolve NONE, RETIRE ou BLOCK;</li>
 *   <li>POST /sync: envia as tarefas da rota; um recibo por task_id, com
 *       duplicate=true nas tarefas reenviadas.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/field")
public class FieldSyncController {

    public record SyncRequest(@NotNull List<FieldReadingSubmission> submissions) {
    }

    public record DirectiveView(String directive) {
    }

    private final FieldSyncService syncService;
    private final FieldDeviceGate gate;
    private final FieldSyncContextProvider contexts;

    public FieldSyncController(FieldSyncService syncService, FieldDeviceGate gate,
                               FieldSyncContextProvider contexts) {
        this.syncService = syncService;
        this.gate = gate;
        this.contexts = contexts;
    }

    @GetMapping("/directive")
    public DirectiveView directive(@RequestHeader("X-Device-Id") UUID deviceId) {
        return new DirectiveView(gate.directiveFor(TenantContextHolder.getTenantId(), deviceId).name());
    }

    @PostMapping("/sync")
    public List<FieldSyncReceipt> sync(@RequestHeader("X-Device-Id") UUID deviceId,
                                       @RequestBody @Valid SyncRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        FieldSyncContext context = contexts.contextFor(tenantId, request.submissions());
        return syncService.syncRoute(tenantId, deviceId, request.submissions(),
                context.parameters(), context.meterDigitsByInstallation(),
                context.previousReadingByInstallation(), OffsetDateTime.now());
    }
}
