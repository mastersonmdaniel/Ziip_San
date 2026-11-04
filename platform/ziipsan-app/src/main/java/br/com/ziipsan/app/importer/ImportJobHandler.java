package br.com.ziipsan.app.importer;

import br.com.ziipsan.commercial.registry.importer.ImportService;
import br.com.ziipsan.commercial.registry.importer.ImportStore;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.infra.queue.MessageHandler;
import br.com.ziipsan.platform.infra.queue.OutboxMessage;
import br.com.ziipsan.platform.tenant.TenantCatalog;

import java.util.Map;
import java.util.UUID;

/**
 * P1.40: handler de um tipo de mensagem da importação (a interface da P1.9
 * tem um tipo por handler; os três beans nascem em
 * {@link ImportJobHandlerConfig}). Mesmo modelo do handler da exportação
 * (P1.12): o worker entrega a mensagem com o tenant explícito; o handler
 * resolve o schema pelo catálogo, fixa o contexto e delega ao serviço.
 *
 * <p>Idempotente por construção do serviço (exigência da P1.9, entrega ao
 * menos uma vez): mensagem redelivrada após o efeito não duplica nada —
 * validar/aplicar/reverter job já concluído é no-op, e linhas já
 * aplicadas/revertidas são puladas. O autor dos efeitos é quem enviou o
 * arquivo (created_by do job), não o worker.
 *
 * <p>Pendência fora desta frente, igual à da exportação: o loop do worker em
 * perfil {@code worker} com o DataSource da role {@code ziipsan_worker} ainda
 * não existe no app (a P1.9 entregou a biblioteca e o IT, não o runner);
 * sem ele, o job fica no estado em que o controlador o deixou até o worker
 * rodar. O handler em si está implementado e testado (chamada direta, no IT).
 */
public class ImportJobHandler implements MessageHandler {

    private final TenantCatalog catalog;
    private final ImportService imports;
    private final String type;

    public ImportJobHandler(TenantCatalog catalog, ImportService imports, String type) {
        if (catalog == null || imports == null || type == null || type.isBlank()) {
            throw new IllegalArgumentException("catalog, imports e type sao obrigatorios");
        }
        this.catalog = catalog;
        this.imports = imports;
        this.type = type;
    }

    @Override
    public String handledType() {
        return type;
    }

    @Override
    public void handle(OutboxMessage message) throws Exception {
        Map<String, Object> payload = Json.readMap(message.payloadJson());
        UUID jobId = UUID.fromString(String.valueOf(payload.get("importId")));
        TenantCatalog.Entry entry = catalog.find(message.tenantId())
                .filter(TenantCatalog.Entry::active)
                .orElseThrow(() -> new IllegalStateException("tenant inexistente ou inativo: " + message.tenantId()));
        try {
            TenantContextHolder.setTenant(entry.schema());
            TenantContextHolder.setTenantId(message.tenantId());
            ImportStore.JobView job = imports.getJobStoreView(jobId);
            UUID actorId = job.createdBy();
            if (actorId == null) {
                throw new IllegalStateException("importacao sem autor: " + jobId);
            }
            TenantContextHolder.setUserId(actorId);
            if (type.equals(ImportService.VALIDATE_JOB_TYPE)) {
                imports.validate(jobId, actorId);
            } else if (type.equals(ImportService.APPLY_JOB_TYPE)) {
                imports.apply(jobId, actorId);
            } else if (type.equals(ImportService.REVERSE_JOB_TYPE)) {
                imports.reverse(jobId, actorId);
            } else {
                throw new IllegalStateException("tipo desconhecido: " + type);
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}
