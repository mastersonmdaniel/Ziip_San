package br.com.ziipsan.app.gis;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.validation.GisValidationService;
import br.com.ziipsan.platform.infra.queue.MessageHandler;
import br.com.ziipsan.platform.infra.queue.OutboxMessage;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * P2.21 (GIS-03): handler da validação contínua do GIS na outbox da P1.9
 * (tipo {@link GisValidationService#DIRTY_AREA_MESSAGE_TYPE}).
 *
 * <p>Vive na raiz de composição (app), e não no módulo GIS, pelo mesmo
 * desenho do {@code RegistryExportHandler} (P1.12) e do adaptador do
 * importador ({@code QueueConfig}): o módulo de domínio não depende de
 * {@code ziipsan-platform} (regra de fronteira da P1.13); quem implementa
 * {@code MessageHandler} é a app, que já compõe os dois lados.
 *
 * <p>Tenant: o worker entrega a mensagem com o tenant explícito e nunca
 * escreve no {@code TenantContextHolder} (P1.9, ADR-0014; os handlers
 * executam sem tenant ambiente). Quem abre o contexto é o próprio
 * handler, a partir da mensagem — resolve o schema pelo catálogo de
 * tenants, grava schema e tenantId no holder e os limpa no
 * {@code finally} — exatamente o modelo do {@code RegistryExportHandler}.
 * O serviço de validação recebe o tenant como parâmetro explícito de
 * qualquer forma. Conferido contra o contrato real da P1.9 (correção da
 * revisão da P2.21): {@code MessageHandler.handledType()/handle(OutboxMessage)},
 * acessores do record {@code OutboxMessage} ({@code tenantId()},
 * {@code payloadJson()}), {@code TenantCatalog.find} →
 * {@code Entry.active()}/{@code Entry.schema()} e
 * {@code TenantContextHolder.setTenant/setTenantId/clear} conferem,
 * assinatura por assinatura. Sem {@code @Transactional} próprio, de
 * propósito: quem é transacional é o serviço
 * ({@code processDirtyArea}/{@code processPendingAreas}), cuja
 * transação abre já com o contexto montado.
 *
 * <p>Carga: {@code {"dirtyAreaId": "<uuid>"}} valida só aquela área;
 * sem o campo, varre as áreas PENDING do tenant. Só identificadores —
 * nenhum dado pessoal (ADR-0014, item 1).
 *
 * <p><b>PENDÊNCIA DE INTEGRAÇÃO (não publicar a partir do gatilho):</b>
 * hoje NADA publica esta mensagem. O gatilho da V13_8 só cria a linha
 * {@code PENDING} em {@code gis_dirty_area}; a outbox real da P1.9 NÃO é
 * tabela do schema do tenant — é a {@code public.outbox} única por banco
 * da migration {@code V8__outbox.sql} do core (particionada por dia UTC,
 * com RLS, {@code dedup_key} e a função {@code outbox_ensure_partition}),
 * e a publicação desenhada é a do {@code PostgresMessageQueue}
 * ({@code MessageQueue.publish(QueuedMessage)}), na transação de quem
 * publica. Fechar o elo área-suja → outbox é decisão da integração
 * (quem publica, em que momento e com qual {@code dedup_key} — o id da
 * área suja é o candidato natural); este handler está pronto para
 * quando a mensagem passar a existir.
 *
 * <p>Idempotente (exigência da P1.9, entrega ao menos uma vez): área já
 * DONE é no-op no serviço e a reconciliação de problemas não
 * duplica OPEN. Tenant inexistente ou inativo lança exceção e a
 * mensagem volta à fila/dead-letter pelo worker, nunca some em silêncio.
 *
 * <p>Pendência fora desta frente, a mesma do handler de exportação: o
 * loop do worker em perfil {@code worker} com o DataSource da role
 * {@code ziipsan_worker} ainda não existe na app (a P1.9 entregou a
 * biblioteca e o IT, não o runner); sem ele, a mensagem fica na outbox
 * até o worker rodar.
 */
@Component
public class GisDirtyAreaValidationHandler implements MessageHandler {

    private final TenantCatalog catalog;
    private final GisValidationService validation;

    public GisDirtyAreaValidationHandler(TenantCatalog catalog, GisValidationService validation) {
        this.catalog = catalog;
        this.validation = validation;
    }

    @Override
    public String handledType() {
        return GisValidationService.DIRTY_AREA_MESSAGE_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        Map<String, Object> payload = Json.readMap(message.payloadJson());
        Object rawArea = payload.get("dirtyAreaId");
        UUID dirtyAreaId = rawArea == null ? null : UUID.fromString(String.valueOf(rawArea));
        TenantCatalog.Entry entry = catalog.find(message.tenantId())
                .filter(TenantCatalog.Entry::active)
                .orElseThrow(() -> new IllegalStateException(
                        "tenant inexistente ou inativo: " + message.tenantId()));
        try {
            TenantContextHolder.setTenant(entry.schema());
            TenantContextHolder.setTenantId(message.tenantId());
            if (dirtyAreaId != null) {
                validation.processDirtyArea(message.tenantId(), dirtyAreaId);
            } else {
                validation.processPendingAreas(message.tenantId());
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}
