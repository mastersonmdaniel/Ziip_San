package br.com.ziipsan.commercial.registry.quality;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.quality.QualityFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.32 - fila de correcao: deduplicacao, OS de lote e a trava central do
 * aceite — correcao SO ocorre por OS (a mesma que agrupou o item).
 */
class CorrectionQueueServiceTest {

    /** Gateway falso: registra as chamadas e devolve OS com id fixo por chamada. */
    private static final class FakeGateway implements BatchOrderGateway {
        final List<Map<UUID, List<String>>> calls = new ArrayList<>();
        UUID nextOrderId = id(9000);

        @Override
        public OpenedCorrectionOrder openCorrectionBatch(Map<UUID, List<String>> keysByUnit, UUID actorId) {
            calls.add(keysByUnit);
            return new OpenedCorrectionOrder(nextOrderId, "SUBMITTED");
        }
    }

    private CorrectionQueueService service(InMemoryCorrectionQueueStore store, FakeGateway gateway) {
        // Cliente id(700) esta ligado a unidade id(70); os demais, a nenhuma.
        return new CorrectionQueueService(store, gateway,
                customerId -> customerId.equals(id(700)) ? List.of(id(70)) : List.of());
    }

    private QualityFinding unitFinding(QualityRuleCode code, UUID unitId) {
        return new QualityFinding(code, QualitySeverity.HIGH, unitId, null, "evidencia");
    }

    @Test
    void enqueueDeduplicaAbertoEReincidenciaCriaNovo() {
        var store = new InMemoryCorrectionQueueStore();
        var service = service(store, new FakeGateway());
        var finding = unitFinding(QualityRuleCode.UNIT_WITHOUT_USER, id(1));

        var first = service.enqueue(List.of(finding));
        var second = service.enqueue(List.of(finding));
        assertThat(first.created()).isEqualTo(1);
        assertThat(second.created()).isZero();
        assertThat(second.duplicatesSkipped()).isEqualTo(1);

        // Depois de resolvido pela OS, o mesmo achado numa varredura nova
        // REABRE como item novo (reincidencia nao e escondida).
        var item = store.all().get(0);
        service.openCorrectionBatch(List.of(item.id()), id(999));
        service.resolveByServiceOrder(id(9000), id(999));
        var third = service.enqueue(List.of(finding));
        assertThat(third.created()).isEqualTo(1);
        assertThat(store.all()).hasSize(2);
    }

    @Test
    void agrupamentoAbreUmaOsComAlvoPorUnidadeEChavesPorAlvo() {
        var store = new InMemoryCorrectionQueueStore();
        var gateway = new FakeGateway();
        var service = service(store, gateway);
        service.enqueue(List.of(
                unitFinding(QualityRuleCode.UNIT_WITHOUT_USER, id(1)),
                unitFinding(QualityRuleCode.ACTIVE_UNIT_WITHOUT_METER, id(1)),
                unitFinding(QualityRuleCode.UNIT_WITHOUT_USER, id(2))));
        var itemIds = store.all().stream().map(CorrectionQueueItem::id).toList();

        var result = service.openCorrectionBatch(itemIds, id(999));

        assertThat(result.opened()).hasSize(1);
        assertThat(result.opened().get(0).serviceOrderId()).isEqualTo(id(9000));
        assertThat(result.opened().get(0).targetUnitIds()).containsExactlyInAnyOrder(id(1), id(2));
        assertThat(result.ungroupable()).isEmpty();
        assertThat(gateway.calls).hasSize(1);
        assertThat(gateway.calls.get(0).get(id(1))).hasSize(2);
        assertThat(gateway.calls.get(0).get(id(2))).hasSize(1);
        assertThat(store.all()).allMatch(i -> i.status() == CorrectionQueueItem.Status.BATCHED);
        assertThat(store.all()).allMatch(i -> id(9000).equals(i.serviceOrderId()));
    }

    @Test
    void itemDeClienteUsaAsUnidadesLigadasESemLigacaoVoltaNomeado() {
        var store = new InMemoryCorrectionQueueStore();
        var gateway = new FakeGateway();
        var service = service(store, gateway);
        var doClienteLigado = new QualityFinding(QualityRuleCode.DOCUMENT_MISSING,
                QualitySeverity.HIGH, null, id(700), "documento ausente");
        var doClienteSolto = new QualityFinding(QualityRuleCode.DOCUMENT_MISSING,
                QualitySeverity.HIGH, null, id(701), "documento ausente");
        service.enqueue(List.of(doClienteLigado, doClienteSolto));
        var itemIds = store.all().stream().map(CorrectionQueueItem::id).toList();

        var result = service.openCorrectionBatch(itemIds, id(999));

        assertThat(result.opened()).hasSize(1);
        assertThat(result.opened().get(0).targetUnitIds()).containsExactly(id(70));
        assertThat(result.ungroupable()).hasSize(1);
        assertThat(result.ungroupable().get(0).customerId()).isEqualTo(id(701));
        assertThat(result.ungroupable().get(0).status()).isEqualTo(CorrectionQueueItem.Status.OPEN);
    }

    @Test
    void correcaoSoOcorrePelaOsQueAgrupouOItem() {
        var store = new InMemoryCorrectionQueueStore();
        var service = service(store, new FakeGateway());
        service.enqueue(List.of(unitFinding(QualityRuleCode.UNIT_WITHOUT_USER, id(1))));
        var item = store.all().get(0);

        // OPEN nao resolve: sem OS de lote, nao ha correcao.
        assertThatThrownBy(() -> service.resolveItem(item.id(), id(9000), id(999)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("so ocorre por OS de lote");

        service.openCorrectionBatch(List.of(item.id()), id(999));

        // Outra OS nao resolve o item.
        assertThatThrownBy(() -> service.resolveItem(item.id(), id(9001), id(999)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("so pode ser resolvido pela OS que o agrupou");

        // A OS certa resolve.
        var resolved = service.resolveItem(item.id(), id(9000), id(999));
        assertThat(resolved.status()).isEqualTo(CorrectionQueueItem.Status.RESOLVED);
    }

    @Test
    void resolverPorOsSemItemAgrupadoEErroExplicito() {
        var store = new InMemoryCorrectionQueueStore();
        var service = service(store, new FakeGateway());
        assertThatThrownBy(() -> service.resolveByServiceOrder(id(4242), id(999)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum item da fila aguarda a OS");
    }

    @Test
    void restoreUsaAChavePersistidaENaoRecalculaEmSilencio() {
        // Correcao D4: a chave persistida e a verdade do item restaurado.
        var finding = unitFinding(QualityRuleCode.UNIT_WITHOUT_USER, id(1));
        var restored = CorrectionQueueItem.restore(id(9100), finding.key(),
                QualityRuleCode.UNIT_WITHOUT_USER, QualitySeverity.HIGH, id(1), null,
                "evidencia", CorrectionQueueItem.Status.BATCHED, id(9000), null);
        assertThat(restored.findingKey()).isEqualTo(finding.key());
        assertThat(restored.status()).isEqualTo(CorrectionQueueItem.Status.BATCHED);
        assertThat(restored.serviceOrderId()).isEqualTo(id(9000));

        // Chave persistida divergente: erro explicito, nunca substituicao.
        assertThatThrownBy(() -> CorrectionQueueItem.restore(id(9101), "CHAVE-PERSISTIDA-DIVERGENTE",
                QualityRuleCode.UNIT_WITHOUT_USER, QualitySeverity.HIGH, id(1), null,
                "evidencia", CorrectionQueueItem.Status.OPEN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHAVE-PERSISTIDA-DIVERGENTE");

        // Detalhe nulo nao e mascarado com "(sem detalhe)".
        assertThatThrownBy(() -> CorrectionQueueItem.restore(id(9102), finding.key(),
                QualityRuleCode.UNIT_WITHOUT_USER, QualitySeverity.HIGH, id(1), null,
                null, CorrectionQueueItem.Status.OPEN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem detalhe persistido");

        // Estado inconsistente com o CHECK do DDL (RESOLVED sem OS) e recusado.
        assertThatThrownBy(() -> CorrectionQueueItem.restore(id(9103), finding.key(),
                QualityRuleCode.UNIT_WITHOUT_USER, QualitySeverity.HIGH, id(1), null,
                "evidencia", CorrectionQueueItem.Status.RESOLVED, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service_order_id");
    }

    @Test
    void triagemExigeMotivoENaoResolve() {
        var store = new InMemoryCorrectionQueueStore();
        var service = service(store, new FakeGateway());
        service.enqueue(List.of(unitFinding(QualityRuleCode.UNIT_WITHOUT_USER, id(1))));
        var item = store.all().get(0);

        assertThatThrownBy(() -> service.dismiss(item.id(), " ", id(999)))
                .isInstanceOf(IllegalArgumentException.class);

        var dismissed = service.dismiss(item.id(), "unidade demolida, achado nao procede", id(999));
        assertThat(dismissed.status()).isEqualTo(CorrectionQueueItem.Status.DISMISSED);
        assertThat(dismissed.serviceOrderId()).isNull();
    }
}
