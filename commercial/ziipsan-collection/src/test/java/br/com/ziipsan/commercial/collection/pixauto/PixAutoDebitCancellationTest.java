package br.com.ziipsan.commercial.collection.pixauto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.5 - cancelamento: pagador ate 23h59 da vespera da liquidacao, recebedor
 * ate 22h do dia da liquidacao (liquidacao em 2027-03-20), e cancelamento da
 * autorizacao pelos dois atores.
 */
class PixAutoDebitCancellationTest {

    private static final LocalDate SETTLEMENT = LocalDate.of(2027, 3, 20);

    private PixAutoDebitInstruction agendar(PixAutoDebitService service, PixAutoDebitAuthorization auth,
                                            String chargeId) {
        return service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft(chargeId, "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
    }

    @Test
    void prazosLimiteCalculadosAMao() {
        assertThat(PixAutoDebitCancellation.payerDeadline(SETTLEMENT))
                .isEqualTo(LocalDateTime.of(2027, 3, 19, 23, 59));
        assertThat(PixAutoDebitCancellation.receiverDeadline(SETTLEMENT))
                .isEqualTo(LocalDateTime.of(2027, 3, 20, 22, 0));
    }

    @Test
    void pagadorCancelaAte23h59DaVesperaENaoDepois() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(store, psp);
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var noLimite = agendar(service, auth, "COB-PAG-OK");
        service.cancelInstruction(TENANT, noLimite.id(), PixAutoDebitCancellation.Actor.PAGADOR,
                LocalDateTime.of(2027, 3, 19, 23, 59));
        assertThat(noLimite.status()).isEqualTo(PixAutoDebitInstruction.Status.CANCELADA_PELO_PAGADOR);
        assertThat(psp.calls).contains("cancelDebit:COB-PAG-OK");
        var depois = agendar(service, auth, "COB-PAG-TARDE");
        assertThatThrownBy(() -> service.cancelInstruction(TENANT, depois.id(),
                PixAutoDebitCancellation.Actor.PAGADOR, LocalDateTime.of(2027, 3, 20, 0, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora do prazo");
        assertThat(depois.status()).isEqualTo(PixAutoDebitInstruction.Status.AGENDADA);
    }

    @Test
    void recebedorCancelaAte22hDoDiaENaoDepois() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var service = PixAutoDebitFixture.service(store, new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var noLimite = agendar(service, auth, "COB-REC-OK");
        service.cancelInstruction(TENANT, noLimite.id(), PixAutoDebitCancellation.Actor.RECEBEDOR,
                LocalDateTime.of(2027, 3, 20, 22, 0));
        assertThat(noLimite.status()).isEqualTo(PixAutoDebitInstruction.Status.CANCELADA_PELO_RECEBEDOR);
        var depois = agendar(service, auth, "COB-REC-TARDE");
        assertThatThrownBy(() -> service.cancelInstruction(TENANT, depois.id(),
                PixAutoDebitCancellation.Actor.RECEBEDOR, LocalDateTime.of(2027, 3, 20, 22, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora do prazo");
    }

    @Test
    void cancelamentoDaAutorizacaoPeloPagadorCancelaInstrucoesPendentes() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(store, psp);
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var pendente = agendar(service, auth, "COB-AUTH-CANC");
        service.cancelAuthorization(TENANT, auth.id(), PixAutoDebitCancellation.Actor.PAGADOR,
                Instant.parse("2027-03-16T10:00:00Z"));
        assertThat(auth.status()).isEqualTo(PixAutoDebitAuthorization.Status.CANCELADA_PELO_PAGADOR);
        assertThat(auth.cancelledAt()).isEqualTo(Instant.parse("2027-03-16T10:00:00Z"));
        assertThat(pendente.status()).isEqualTo(PixAutoDebitInstruction.Status.CANCELADA_PELO_PAGADOR);
        assertThat(psp.calls).contains("cancelDebit:COB-AUTH-CANC");
    }

    @Test
    void cancelamentoDaAutorizacaoPeloRecebedorRegistraOAtor() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        service.cancelAuthorization(TENANT, auth.id(), PixAutoDebitCancellation.Actor.RECEBEDOR,
                Instant.parse("2027-03-16T10:00:00Z"));
        assertThat(auth.status()).isEqualTo(PixAutoDebitAuthorization.Status.CANCELADA_PELO_RECEBEDOR);
    }

    @Test
    void cancelarInstrucaoJaCanceladaERecusadoSemNovoCancelamentoNoPsp() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(store, psp);
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = agendar(service, auth, "COB-DUP-CANC");
        service.cancelInstruction(TENANT, instruction.id(), PixAutoDebitCancellation.Actor.PAGADOR,
                LocalDateTime.of(2027, 3, 19, 23, 0));
        assertThatThrownBy(() -> service.cancelInstruction(TENANT, instruction.id(),
                PixAutoDebitCancellation.Actor.PAGADOR, LocalDateTime.of(2027, 3, 19, 23, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao pode ser cancelada");
        assertThat(psp.calls.stream().filter(c -> c.equals("cancelDebit:COB-DUP-CANC")).count())
                .isEqualTo(1);
    }

    @Test
    void instrucaoLiquidadaNaoPodeSerCancelada() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var service = PixAutoDebitFixture.service(store, new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = agendar(service, auth, "COB-LIQ");
        service.notifyPayer(TENANT, instruction.id(), Instant.parse("2027-03-18T09:00:00Z"));
        service.submit(TENANT, instruction.id(), SETTLEMENT);
        service.reconcile(TENANT, "COB-LIQ", instruction.endToEndId(),
                new java.math.BigDecimal("100.00"), Instant.parse("2027-03-20T10:00:00Z"));
        assertThatThrownBy(() -> service.cancelInstruction(TENANT, instruction.id(),
                PixAutoDebitCancellation.Actor.RECEBEDOR, LocalDateTime.of(2027, 3, 20, 21, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao pode ser cancelada");
    }
}
