package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fiação viva da porta de saída da II-1 ({@link ClickOsPorts.ClickOrderPort})
 * sobre o motor real de OS da P1.31 — fecha a pendência P-II1-FIACAO
 * no lado da criação da ordem.
 *
 * <p>Tradução (e só dela, como prescreve a javadoc de
 * {@code ClickOsPorts}): ordem com origem {@code INTERNAL} (clique em
 * backoffice — pela regra do núcleo, OS interna nunca carrega
 * protocolo, logo {@code protocolId} é nulo); modo de execução pelo
 * indicador do tipo lido do motor ({@code INTERNAL→INTERNAL},
 * {@code FIELD→FIELD} — o motor recusa qualquer outro emparelhamento);
 * prioridade {@code NORMAL} (o payload não carrega prioridade: prazo
 * e prioridade são do tipo/catálogo do tenant, o motor aplica o
 * prazo do tipo); cada alvo de {@code coreTargets()} registado sobre
 * a ordem; submissão ao fim. Devolve o id criado pelo núcleo.
 *
 * <p>O tenant do payload tem de ser o tenant do contexto da
 * requisição: divergência é recusa nomeada, nunca troca silenciosa
 * de tenant.
 */
@Component
public class CoreClickOrderAdapter implements ClickOsPorts.ClickOrderPort {

    private final Supplier<ClickOrderEngine> engines;

    public CoreClickOrderAdapter(DataSource dataSource) {
        this(() -> CoreClickOrderEngine.fromContext(dataSource));
    }

    CoreClickOrderAdapter(Supplier<ClickOrderEngine> engines) {
        this.engines = Objects.requireNonNull(engines, "adaptador de OS por clique sem motor");
    }

    @Override
    public UUID createFromClickPayload(ClickOsPorts.ClickServiceOrderPayload payload) {
        Objects.requireNonNull(payload, "payload de OS por clique nulo");
        UUID contextTenant = TenantContextHolder.getTenantId();
        if (contextTenant == null || !contextTenant.equals(payload.tenantId())) {
            throw new IllegalStateException(
                    "tenant do payload difere do tenant do contexto da requisição");
        }
        ClickOrderEngine engine = engines.get();
        ServiceOrderEnums.TypeIndicator indicator = engine.typeIndicator(payload.serviceTypeCode());
        ServiceOrderEnums.ExecutionMode mode = indicator == ServiceOrderEnums.TypeIndicator.INTERNAL
                ? ServiceOrderEnums.ExecutionMode.INTERNAL
                : ServiceOrderEnums.ExecutionMode.FIELD;
        UUID orderId = engine.createOrder(payload.serviceTypeCode(), mode, payload.requestedBy());
        for (ClickOsPorts.CoreTargetHint hint : payload.coreTargets()) {
            engine.addTarget(orderId, hint.targetType(), hint.targetId(), payload.requestedBy());
        }
        engine.submit(orderId, payload.requestedBy());
        return orderId;
    }
}
