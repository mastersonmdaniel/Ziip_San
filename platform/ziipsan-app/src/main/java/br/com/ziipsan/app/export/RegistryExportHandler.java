package br.com.ziipsan.app.export;

import br.com.ziipsan.commercial.registry.domain.GeneratedFile;
import br.com.ziipsan.commercial.registry.repository.GeneratedFileRepository;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.infra.queue.MessageHandler;
import br.com.ziipsan.platform.infra.queue.OutboxMessage;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * P1.12: handler da exportação assíncrona (tipo {@code registry.export} da outbox
 * da P1.9). O worker entrega a mensagem com o tenant explícito; o handler resolve
 * o schema pelo catálogo (mesmo modelo do JwtTenantFilter e do job de recálculo
 * de máscara), gera o CSV com a mesma máscara da tela e marca o arquivo READY
 * em "Arquivos gerados".
 *
 * <p>Idempotente (exigência da P1.9, entrega ao menos uma vez): arquivo já READY
 * não é regenerado. Arquivo FAILED volta a ser gerado na nova tentativa do
 * worker — a falha anterior não trava o pedido. Tenant inexistente ou inativo,
 * arquivo inexistente ou recurso desconhecido lançam exceção e a mensagem vai
 * para nova tentativa/dead-letter pelo worker, nunca some em silêncio.
 *
 * <p>Pendência fora desta frente: o loop do worker em perfil {@code worker}
 * com o DataSource da role {@code ziipsan_worker} ainda não existe no app (a
 * P1.9 entregou a biblioteca e o IT, não o runner); sem ele, o arquivo fica
 * QUEUED até o worker rodar. O handler em si está implementado e testado.
 */
@Component
public class RegistryExportHandler implements MessageHandler {

    private final TenantCatalog catalog;
    private final RegistryExportService exports;
    private final GeneratedFileRepository files;

    public RegistryExportHandler(TenantCatalog catalog, RegistryExportService exports,
                                 GeneratedFileRepository files) {
        this.catalog = catalog;
        this.exports = exports;
        this.files = files;
    }

    @Override
    public String handledType() {
        return RegistryExportService.EXPORT_JOB_TYPE;
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void handle(OutboxMessage message) throws Exception {
        Map<String, Object> payload = Json.readMap(message.payloadJson());
        UUID fileId = UUID.fromString(String.valueOf(payload.get("generatedFileId")));
        String resource = String.valueOf(payload.get("resource"));
        Map<String, Object> filters = payload.get("filters") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        TenantCatalog.Entry entry = catalog.find(message.tenantId())
                .filter(TenantCatalog.Entry::active)
                .orElseThrow(() -> new IllegalStateException("tenant inexistente ou inativo: " + message.tenantId()));
        try {
            TenantContextHolder.setTenant(entry.schema());
            TenantContextHolder.setTenantId(message.tenantId());
            GeneratedFile file = files.findByIdAndTenantId(fileId, message.tenantId())
                    .orElseThrow(() -> new IllegalStateException("arquivo gerado inexistente: " + fileId));
            if (file.getStatus() == GeneratedFile.Status.READY) {
                return;
            }
            RegistryExportService.CsvResult result = exports.buildAsyncCsv(resource, filters);
            file.setContent(result.csv());
            file.setRowCount(result.rows());
            file.setStatus(GeneratedFile.Status.READY);
            file.setReadyAt(OffsetDateTime.now());
            file.setError(null);
            files.save(file);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
