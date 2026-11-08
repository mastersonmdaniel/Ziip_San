package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.20 - formato acessivel a pedido (RAS-10) e alteracao via protocolo/OS da P3.3. */
class AccessibleAndDataChangeTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();
    private final List<String> downloads = new ArrayList<>();

    private AccessibleBillService accessible(boolean indicator) {
        return new AccessibleBillService(new AccessibleBillService.AccessiblePort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public boolean accessibilityIndicatorActive(UUID t, UUID u) { return indicator; }
            @Override public Optional<AccessibleBillService.AccessibleBill> accessibleBill(UUID t, UUID b) {
                return Optional.of(new AccessibleBillService.AccessibleBill(BILL, "nov/2026", "R$ 90,00",
                        "10/12/2026", "12 m3"));
            }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.of(UNIT); }
        }, (t, s, b, kind) -> downloads.add(kind));
    }

    @Test void acessivelAPedidoSaiEmJsonEstruturado() {
        String json = accessible(false).accessibleBillJson(TENANT, "dono", BILL, true);
        assertThat(json).contains("\"reference\"").contains("R$ 90,00");
        assertThat(downloads).containsExactly("ACCESSIBLE_BILL");
    }
    @Test void acessivelPeloIndicadorSemPedido() {
        assertThat(accessible(true).accessibleBillJson(TENANT, "dono", BILL, false)).contains("nov/2026");
    }
    @Test void semPedidoESemIndicadorERecusado() {
        assertThatThrownBy(() -> accessible(false).accessibleBillJson(TENANT, "dono", BILL, false))
                .isInstanceOf(ApiException.BadRequest.class);
    }
    @Test void contaInexistenteRespondeIgualANaoVinculada() {
        // D7: idem no formato acessivel — 403 com a mesma mensagem.
        var semConta = new AccessibleBillService(new AccessibleBillService.AccessiblePort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public boolean accessibilityIndicatorActive(UUID t, UUID u) { return true; }
            @Override public Optional<AccessibleBillService.AccessibleBill> accessibleBill(UUID t, UUID b) {
                return Optional.empty();
            }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.empty(); }
        }, (t, s, b, kind) -> downloads.add(kind));
        var naoVinculada = new AccessibleBillService(new AccessibleBillService.AccessiblePort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return false; }
            @Override public boolean accessibilityIndicatorActive(UUID t, UUID u) { return true; }
            @Override public Optional<AccessibleBillService.AccessibleBill> accessibleBill(UUID t, UUID b) {
                return Optional.empty();
            }
            @Override public Optional<UUID> unitOfBill(UUID t, UUID b) { return Optional.of(UNIT); }
        }, (t, s, b, kind) -> downloads.add(kind));
        ApiException inexistente = captura(() -> semConta.accessibleBillJson(TENANT, "dono", BILL, true));
        ApiException naoVinc = captura(() -> naoVinculada.accessibleBillJson(TENANT, "dono", BILL, true));
        assertThat(inexistente).isInstanceOf(ApiException.Forbidden.class);
        assertThat(inexistente.getMessage()).isEqualTo(naoVinc.getMessage());
    }

    private ApiException captura(Runnable r) {
        try { r.run(); } catch (ApiException e) { return e; }
        throw new AssertionError("esperava ApiException");
    }

    @Test void alteracaoDeDadosAbreProtocoloENaoAplicaDireto() {
        var svc = new ConsumerDataChangeService(new ConsumerDataChangeService.RequestPort() {
            @Override public boolean isUnitLinkedToConsumer(UUID t, String s, UUID u) { return true; }
            @Override public String openProtocolAndInternalOrder(UUID t, String s, UUID u, String k, String j) {
                return "PROT-2026-1";
            }
        });
        assertThat(svc.requestChange(TENANT, "dono", UNIT, "EMAIL", "{}")).isEqualTo("PROT-2026-1");
    }
}
