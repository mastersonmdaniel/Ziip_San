package br.com.ziipsan.commercial.collection.pixauto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.5 - agendamento de 2 a 10 dias antes da liquidacao, notificacao ao
 * pagador como pre-condicao do envio e teto declarado na autorizacao.
 * Datas calculadas a mao: liquidacao em 2027-03-20; agendar em 2027-03-18
 * (2 dias) e 2027-03-10 (10 dias) passa; 2027-03-19 (1 dia) e 2027-03-09
 * (11 dias) nao. Notificacao (notice_days = 2) vence em 2027-03-18.
 */
class PixAutoDebitSchedulingTest {

    private static final LocalDate SETTLEMENT = LocalDate.of(2027, 3, 20);

    private PixAutoDebitAuthorization autorizar(PixAutoDebitService service) {
        return service.authorize(PixAutoDebitFixture.request(1, false, false, null));
    }

    @Test
    void agendamentoComDoisEComDezDiasDeAntecedenciaPassa() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var service = PixAutoDebitFixture.service(store, new PixAutoDebitFixture.FakePsp());
        var auth = autorizar(service);
        var dois = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-2D", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 18));
        assertThat(dois.status()).isEqualTo(PixAutoDebitInstruction.Status.AGENDADA);
        var dez = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-10D", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 10));
        assertThat(dez.status()).isEqualTo(PixAutoDebitInstruction.Status.AGENDADA);
    }

    @Test
    void agendamentoComUmOuOnzeDiasERecusado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = autorizar(service);
        assertThatThrownBy(() -> service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-1D", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 19)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 a 10 dias");
        assertThatThrownBy(() -> service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-11D", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 a 10 dias");
    }

    @Test
    void envioSemNotificacaoAoPagadorERecusadoEComNotificacaoPassa() {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(store, psp);
        var auth = autorizar(service);
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-NOTIF", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        assertThat(service.notificationDueDate(instruction)).isEqualTo(LocalDate.of(2027, 3, 18));
        assertThatThrownBy(() -> service.submit(TENANT, instruction.id(), SETTLEMENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notificacao");
        assertThat(psp.calls).containsExactly("registerAuthorization");
        service.notifyPayer(TENANT, instruction.id(), Instant.parse("2027-03-18T09:00:00Z"));
        var enviada = service.submit(TENANT, instruction.id(), SETTLEMENT);
        assertThat(enviada.status()).isEqualTo(PixAutoDebitInstruction.Status.ENVIADA);
        assertThat(enviada.attempts()).isEqualTo(1);
        assertThat(enviada.firstAttemptDate()).isEqualTo(SETTLEMENT);
        assertThat(psp.calls).containsExactly("registerAuthorization", "submitDebit:COB-NOTIF");
    }

    @Test
    void identificadorDeCobrancaDuplicadoERecusado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = autorizar(service);
        service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-DUP", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        assertThatThrownBy(() -> service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-DUP", "90.00", SETTLEMENT), LocalDate.of(2027, 3, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja existe");
    }

    @Test
    void instrucaoAcimaDoTetoDeclaradoNaAutorizacaoERecusada() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var req = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT,
                PixAutoDebitFixture.RECEIVER_CNPJ, null, PixAutoDebitFixture.PAYER_CPF, 1, false,
                PixAutoDebitFixture.PROOF_HASH, "jornada/registro-1", PixAutoDebitFixture.AUTHORIZED_AT,
                true, 3, new java.math.BigDecimal("100.00"), false, null);
        var auth = service.authorize(req);
        assertThatThrownBy(() -> service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-TETO", "100.01", SETTLEMENT), LocalDate.of(2027, 3, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor maximo");
        var noTeto = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-TETO-OK", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        assertThat(noTeto.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void agendarEmAutorizacaoCanceladaERecusado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = autorizar(service);
        service.cancelAuthorization(TENANT, auth.id(), PixAutoDebitCancellation.Actor.PAGADOR,
                Instant.parse("2027-03-05T10:00:00Z"));
        assertThatThrownBy(() -> service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-CANC", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATIVA");
    }
}
