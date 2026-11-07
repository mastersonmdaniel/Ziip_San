package br.com.ziipsan.commercial.collection.pixauto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.5 - retentativas (no maximo 3 em 7 dias, so se previstas na autorizacao)
 * e multa/juros na instrucao do ciclo seguinte.
 *
 * <p>Datas a mao: primeira tentativa em 2027-03-20. Retentativas em
 * 2027-03-21, 2027-03-24 e 2027-03-27 (7 dias depois, no limite) cabem; a 4a
 * nao; retentativa em 2027-03-28 (8 dias) nao.
 *
 * <p>Valores a mao: principal nao pago 100,00, multa de 2% = 2,00; juros
 * simples de 0,033% ao dia por 30 dias = 100,00 x 0,00033 x 30 = 0,99; ciclo
 * seguinte com principal 120,00 totaliza 120,00 + 2,00 + 0,99 = 122,99.
 */
class PixAutoDebitRetryAndNextCycleTest {

    private static final LocalDate SETTLEMENT = LocalDate.of(2027, 3, 20);

    private record Cenario(PixAutoDebitService service, PixAutoDebitFixture.FakePsp psp,
                           PixAutoDebitAuthorization auth, PixAutoDebitInstruction instruction) {
    }

    private Cenario cenarioEnviado(boolean retryForeseen, int authMaxRetries) {
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(store, psp);
        var req = new PixAutoDebitService.AuthorizationRequest(
                AccountDebitAuthorizationGate.DebitChannel.PIX_AUTOMATICO, TENANT,
                PixAutoDebitFixture.RECEIVER_CNPJ, null, PixAutoDebitFixture.PAYER_CPF, 1, false,
                PixAutoDebitFixture.PROOF_HASH, "jornada/registro-1", PixAutoDebitFixture.AUTHORIZED_AT,
                retryForeseen, authMaxRetries, null, false, null);
        var auth = service.authorize(req);
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-RETRY", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        service.notifyPayer(TENANT, instruction.id(), Instant.parse("2027-03-18T09:00:00Z"));
        service.submit(TENANT, instruction.id(), SETTLEMENT);
        return new Cenario(service, psp, auth, instruction);
    }

    @Test
    void retentativasAteTresEmSeteDiasSaoAgendadas() {
        var c = cenarioEnviado(true, 3);
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 21)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.RETENTATIVA_AGENDADA);
        c.service().submit(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 21));
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 24)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.RETENTATIVA_AGENDADA);
        c.service().submit(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 24));
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 27)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.RETENTATIVA_AGENDADA);
        c.service().submit(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 27));
        assertThat(c.instruction().attempts()).isEqualTo(4);
        assertThat(c.instruction().retriesUsed()).isEqualTo(3);
    }

    @Test
    void quartaRetentativaEFalhaDefinitiva() {
        var c = cenarioEnviado(true, 3);
        LocalDate[] retries = {LocalDate.of(2027, 3, 21), LocalDate.of(2027, 3, 24), LocalDate.of(2027, 3, 27)};
        for (LocalDate retry : retries) {
            c.service().registerFailure(TENANT, c.instruction().id(), retry);
            c.service().submit(TENANT, c.instruction().id(), retry);
        }
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 27)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.FALHA_DEFINITIVA);
        assertThat(c.instruction().status())
                .isEqualTo(PixAutoDebitInstruction.Status.FALHA_DEFINITIVA);
    }

    @Test
    void retentativaForaDaJanelaDeSeteDiasEFalhaDefinitiva() {
        var c = cenarioEnviado(true, 3);
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 28)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.FALHA_DEFINITIVA);
    }

    @Test
    void semPrevisaoNaAutorizacaoNaoHaRetentativa() {
        var c = cenarioEnviado(false, 0);
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 21)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.FALHA_DEFINITIVA);
        assertThat(c.instruction().status())
                .isEqualTo(PixAutoDebitInstruction.Status.FALHA_DEFINITIVA);
    }

    @Test
    void tetoDaAutorizacaoMenorQueODaPoliticaVale() {
        var c = cenarioEnviado(true, 1);
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 21)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.RETENTATIVA_AGENDADA);
        c.service().submit(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 21));
        assertThat(c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 22)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.FALHA_DEFINITIVA);
    }

    @Test
    void janelaDaPoliticaDoTenantLimitaARetentativa() {
        // Politica do tenant com janela de 3 dias (pay.auto_debit.retry_policy,
        // dentro do teto normativo de 7): a janela da politica prevalece.
        var store = new PixAutoDebitFixture.InMemoryStore();
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = new PixAutoDebitService(store, psp,
                new br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules.RetryPolicy(3, 3, true), 2);
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-JANELA", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        service.notifyPayer(TENANT, instruction.id(), Instant.parse("2027-03-18T09:00:00Z"));
        service.submit(TENANT, instruction.id(), SETTLEMENT);
        // Dia 3 da primeira tentativa (2027-03-20): dentro da janela da politica.
        assertThat(service.registerFailure(TENANT, instruction.id(), LocalDate.of(2027, 3, 23)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.RETENTATIVA_AGENDADA);
        service.submit(TENANT, instruction.id(), LocalDate.of(2027, 3, 23));
        // Dia 4: fora da janela da politica (3), embora dentro da normativa (7)
        // e com teto de contagem sobrando (1 de 3 usada) - falha definitiva.
        assertThat(service.registerFailure(TENANT, instruction.id(), LocalDate.of(2027, 3, 24)))
                .isEqualTo(PixAutoDebitService.FailureOutcome.FALHA_DEFINITIVA);
    }

    @Test
    void calculadoraDoCicloSeguinteComValoresCalculadosAMao() {
        var charge = NextCycleChargeCalculator.compute(new BigDecimal("100.00"), new BigDecimal("120.00"),
                new BigDecimal("2"), new BigDecimal("0.033"), 30);
        assertThat(charge.fine()).isEqualByComparingTo("2.00");
        assertThat(charge.interest()).isEqualByComparingTo("0.99");
        assertThat(charge.total()).isEqualByComparingTo("122.99");
        var semAtraso = NextCycleChargeCalculator.compute(new BigDecimal("100.00"), new BigDecimal("120.00"),
                new BigDecimal("2"), new BigDecimal("0.033"), 0);
        assertThat(semAtraso.interest()).isEqualByComparingTo("0.00");
        assertThat(semAtraso.total()).isEqualByComparingTo("122.00");
    }

    @Test
    void cicloSeguinteCarregaMultaEJurosDaFalhaDefinitiva() {
        var c = cenarioEnviado(false, 0);
        c.service().registerFailure(TENANT, c.instruction().id(), LocalDate.of(2027, 3, 21));
        // Proxima liquidacao 30 dias depois da original: 2027-04-19; agendamento em 2027-04-10 (9 dias).
        var next = c.service().buildNextCycleInstruction(TENANT, c.instruction().id(),
                PixAutoDebitFixture.draft("COB-SEGUINTE", "120.00", LocalDate.of(2027, 4, 19)),
                new BigDecimal("2"), new BigDecimal("0.033"), LocalDate.of(2027, 4, 10));
        assertThat(next.fine()).isEqualByComparingTo("2.00");
        assertThat(next.interest()).isEqualByComparingTo("0.99");
        assertThat(next.total()).isEqualByComparingTo("122.99");
        assertThat(next.authorizationId()).isEqualTo(c.auth().id());
    }

    @Test
    void cicloSeguinteSemFalhaDefinitivaERecusado() {
        var c = cenarioEnviado(true, 3);
        assertThatThrownBy(() -> c.service().buildNextCycleInstruction(TENANT, c.instruction().id(),
                PixAutoDebitFixture.draft("COB-CEDO", "120.00", LocalDate.of(2027, 4, 19)),
                new BigDecimal("2"), new BigDecimal("0.033"), LocalDate.of(2027, 4, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FALHA_DEFINITIVA");
    }
}
