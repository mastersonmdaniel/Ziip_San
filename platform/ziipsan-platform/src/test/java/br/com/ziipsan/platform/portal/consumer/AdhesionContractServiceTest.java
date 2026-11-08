package br.com.ziipsan.platform.portal.consumer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.20 - contrato de adesao versionado, entrega nos dois canais e ciencia por versao. */
class AdhesionContractServiceTest {

    private final List<AdhesionContractService.DeliveryRecord> deliveries = new ArrayList<>();

    private AdhesionContractService service(boolean published) {
        return new AdhesionContractService(new AdhesionContractService.ContractPort() {
            @Override public Optional<AdhesionContractService.PublishedContract> currentAdhesionContract(UUID t) {
                return published ? Optional.of(new AdhesionContractService.PublishedContract(
                        "ADHESION", "v7", "hash", Instant.now())) : Optional.empty();
            }
            @Override public Optional<AdhesionContractService.PublishedContract> currentServiceRegulation(UUID t) {
                return published ? Optional.of(new AdhesionContractService.PublishedContract(
                        "REGULATION", "v3", "hash", Instant.now())) : Optional.empty();
            }
            @Override public void recordDelivery(UUID t, AdhesionContractService.DeliveryRecord r) { deliveries.add(r); }
            @Override public Optional<AdhesionContractService.DeliveryRecord> deliveryOf(UUID t, UUID u, String v) {
                return deliveries.stream().filter(d -> d.contractVersion().equals(v)).findFirst();
            }
            @Override public void acknowledgeDelivery(UUID t, UUID u, String v, Instant at) {
                // Semantica de UPDATE do dublê: preenche acknowledgedAt nas
                // linhas existentes da versao, sem inserir linha nova.
                for (int i = 0; i < deliveries.size(); i++) {
                    AdhesionContractService.DeliveryRecord d = deliveries.get(i);
                    if (d.contractVersion().equals(v)) {
                        deliveries.set(i, new AdhesionContractService.DeliveryRecord(
                                d.unitId(), d.contractVersion(), d.trigger(), d.channel(),
                                d.deliveredAt(), at));
                    }
                }
            }
        });
    }

    @Test void semContratoOuRegulamentoPublicadoNadaSeEntrega() {
        assertThatThrownBy(() -> service(false).deliverOnTrigger(UUID.randomUUID(), UUID.randomUUID(),
                AdhesionContractService.DeliveryTrigger.NEW_CONNECTION)).isInstanceOf(IllegalStateException.class);
    }
    @Test void ligacaoNovaETrocaEntregamNosDoisCanaisComVersao() {
        UUID unit = UUID.randomUUID();
        service(true).deliverOnTrigger(UUID.randomUUID(), unit, AdhesionContractService.DeliveryTrigger.NEW_CONNECTION);
        service(true).deliverOnTrigger(UUID.randomUUID(), unit, AdhesionContractService.DeliveryTrigger.HOLDER_CHANGE);
        assertThat(deliveries).hasSize(4);
        assertThat(deliveries).allSatisfy(d -> assertThat(d.contractVersion()).isEqualTo("v7"));
        assertThat(deliveries).extracting(AdhesionContractService.DeliveryRecord::channel)
                .contains(AdhesionContractService.DeliveryChannel.PORTAL,
                        AdhesionContractService.DeliveryChannel.PROTOCOL_RECEIPT);
    }
    @Test void cienciaExigeEntregaDaVersao() {
        assertThatThrownBy(() -> service(true).acknowledge(UUID.randomUUID(), UUID.randomUUID(), "v99"))
                .isInstanceOf(br.com.ziipsan.core.api.ApiException.BadRequest.class);
    }
    @Test void cienciaPreencheAcknowledgedAtSemNovaEntrega() {
        // D3, caminho de SUCESSO: a ciencia preenche acknowledged_at nas
        // entregas existentes (2 canais) — a contagem de entregas NAO muda.
        UUID tenant = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        var svc = service(true);
        svc.deliverOnTrigger(tenant, unit, AdhesionContractService.DeliveryTrigger.NEW_CONNECTION);
        assertThat(deliveries).hasSize(2);
        assertThat(deliveries).allSatisfy(d -> assertThat(d.acknowledgedAt()).isNull());
        svc.acknowledge(tenant, unit, "v7");
        assertThat(deliveries).hasSize(2);
        assertThat(deliveries).allSatisfy(d -> assertThat(d.acknowledgedAt()).isNotNull());
    }
}
