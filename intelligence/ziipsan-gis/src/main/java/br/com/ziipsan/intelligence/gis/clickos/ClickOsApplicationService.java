package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.ClickInput;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.CreatedView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PayloadView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PropagationView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsPorts.ClickServiceOrderPayload;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverPorts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Serviço de aplicação da II-1: monta o núcleo puro
 * ({@link ClickServiceOrderService}) com as duas portas e expõe as
 * operações ao controller.
 *
 * <p>Retrato da rede ({@code ManeuverPorts.SnapshotPort}): **não
 * existe implementação de produção desta porta em todo o repo** —
 * nem para a II-1 nem para a GIS-11, que a partilha. Sem um bean
 * registado, este serviço usa uma porta que recusa com a pendência
 * nomeada P-PDCAGIS2-01 em vez de falhar o arranque do contexto ou
 * fingir uma leitura: o endpoint fica vivo, a fiação de saída para o
 * núcleo de OS fica real, e a propagação passa a funcionar sem
 * nenhuma outra mudança quando a implementação JDBC do retrato for
 * registada (lote próprio, com o mapeamento BD→retrato documentado
 * nas fontes — operabilidade de válvula e vínculos funcionais da
 * GIS-05 incluídos).
 */
@Service
public class ClickOsApplicationService implements ClickServiceOrderOperations {

    static final String SNAPSHOT_NOT_WIRED =
            "porta de retrato da rede (SnapshotPort) sem implementação registada"
                    + " — pendência P-PDCAGIS2-01";

    private final ClickServiceOrderService delegate;

    public ClickOsApplicationService(ObjectProvider<ManeuverPorts.SnapshotPort> snapshotProvider,
                                     ClickOsPorts.ClickOrderPort orderPort) {
        ManeuverPorts.SnapshotPort snapshot = snapshotProvider.getIfAvailable();
        if (snapshot == null) {
            snapshot = tenantId -> {
                throw new IllegalStateException(SNAPSHOT_NOT_WIRED);
            };
        }
        this.delegate = new ClickServiceOrderService(snapshot, orderPort);
    }

    /** Construtor de prova (testes): portas dadas, sem Spring. */
    ClickOsApplicationService(ManeuverPorts.SnapshotPort snapshotPort,
                              ClickOsPorts.ClickOrderPort orderPort) {
        this.delegate = new ClickServiceOrderService(snapshotPort, orderPort);
    }

    @Override
    public PropagationView propagate(UUID tenantId, ClickInput input) {
        return PropagationView.of(delegate.propagate(
                tenantId, input.target(), input.propagationMode(), input.config()));
    }

    @Override
    public PayloadView buildPayload(UUID tenantId, UUID requestedBy, ClickInput input) {
        return PayloadView.of(delegate.buildPayload(
                tenantId, requestedBy, input.serviceTypeCode(),
                input.target(), input.propagationMode(), input.config()));
    }

    @Override
    public CreatedView createFromClick(UUID tenantId, UUID requestedBy, ClickInput input) {
        ClickServiceOrderPayload payload = delegate.buildPayload(
                tenantId, requestedBy, input.serviceTypeCode(),
                input.target(), input.propagationMode(), input.config());
        UUID orderId = delegate.submit(payload);
        return new CreatedView(orderId, PayloadView.of(payload));
    }
}
