package br.com.ziipsan.commercial.collection.pixauto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static br.com.ziipsan.commercial.collection.pixauto.PixAutoDebitFixture.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4.5 - conciliacao por identificador de cobranca e devolucao de debito
 * indevido em 24 horas pelo banco do pagador.
 *
 * <p>Prazos a mao: pedido de devolucao em 2027-03-20T10:00Z tem prazo
 * 2027-03-21T10:00Z; concluir em 2027-03-21T09:59Z e no prazo; concluir em
 * 2027-03-21T10:01Z e com atraso; ler o estado em 2027-03-21T10:01Z sem
 * conclusao e VENCIDO_SEM_DEVOLUCAO.
 */
class PixAutoDebitReconciliationRefundTest {

    private static final LocalDate SETTLEMENT = LocalDate.of(2027, 3, 20);

    private PixAutoDebitInstruction liquidada(PixAutoDebitService service) {
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-CONC", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        service.notifyPayer(TENANT, instruction.id(), Instant.parse("2027-03-18T09:00:00Z"));
        service.submit(TENANT, instruction.id(), SETTLEMENT);
        return instruction;
    }

    @Test
    void conciliacaoPeloIdentificadorBaixaUmaUnicaVez() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var instruction = liquidada(service);
        var result = service.reconcile(TENANT, "COB-CONC", instruction.endToEndId(),
                new BigDecimal("100.00"), Instant.parse("2027-03-20T10:00:00Z"));
        assertThat(result.outcome()).isEqualTo(PixAutoDebitService.ReconciliationOutcome.CONCILIADO);
        assertThat(result.instructionId()).isEqualTo(instruction.id());
        assertThat(instruction.status()).isEqualTo(PixAutoDebitInstruction.Status.LIQUIDADA);
        var repetido = service.reconcile(TENANT, "COB-CONC", instruction.endToEndId(),
                new BigDecimal("100.00"), Instant.parse("2027-03-20T10:05:00Z"));
        assertThat(repetido.outcome())
                .isEqualTo(PixAutoDebitService.ReconciliationOutcome.REPETIDO_SEM_EFEITO);
    }

    @Test
    void conciliacaoComValorDivergenteNaoBaixa() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var instruction = liquidada(service);
        var result = service.reconcile(TENANT, "COB-CONC", instruction.endToEndId(),
                new BigDecimal("99.99"), Instant.parse("2027-03-20T10:00:00Z"));
        assertThat(result.outcome()).isEqualTo(PixAutoDebitService.ReconciliationOutcome.DIVERGENTE);
        assertThat(instruction.status()).isEqualTo(PixAutoDebitInstruction.Status.ENVIADA);
    }

    @Test
    void conciliacaoDeIdentificadorDesconhecidoNaoIdentifica() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var result = service.reconcile(TENANT, "COB-INEXISTENTE", "E2E-X",
                new BigDecimal("100.00"), Instant.parse("2027-03-20T10:00:00Z"));
        assertThat(result.outcome()).isEqualTo(PixAutoDebitService.ReconciliationOutcome.NAO_IDENTIFICADO);
        assertThat(result.instructionId()).isNull();
    }

    @Test
    void segundoEndToEndIdParaInstrucaoLiquidadaEDivergente() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var instruction = liquidada(service);
        service.reconcile(TENANT, "COB-CONC", instruction.endToEndId(),
                new BigDecimal("100.00"), Instant.parse("2027-03-20T10:00:00Z"));
        var result = service.reconcile(TENANT, "COB-CONC", "E2E-OUTRO",
                new BigDecimal("100.00"), Instant.parse("2027-03-20T11:00:00Z"));
        assertThat(result.outcome()).isEqualTo(PixAutoDebitService.ReconciliationOutcome.DIVERGENTE);
    }

    @Test
    void conciliacaoDeInstrucaoCanceladaNaoBaixa() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-CANC-CONC", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        service.cancelInstruction(TENANT, instruction.id(), PixAutoDebitCancellation.Actor.PAGADOR,
                java.time.LocalDateTime.of(2027, 3, 19, 23, 59));
        var result = service.reconcile(TENANT, "COB-CANC-CONC", "E2E-TARDIO",
                new BigDecimal("100.00"), Instant.parse("2027-03-20T10:00:00Z"));
        assertThat(result.outcome()).isEqualTo(PixAutoDebitService.ReconciliationOutcome.DIVERGENTE);
        assertThat(instruction.status())
                .isEqualTo(PixAutoDebitInstruction.Status.CANCELADA_PELO_PAGADOR);
    }

    @Test
    void conciliacaoDeInstrucaoNaoEnviadaNaoBaixa() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-NAO-ENV", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        var result = service.reconcile(TENANT, "COB-NAO-ENV", "E2E-CEDO",
                new BigDecimal("100.00"), Instant.parse("2027-03-20T10:00:00Z"));
        assertThat(result.outcome()).isEqualTo(PixAutoDebitService.ReconciliationOutcome.DIVERGENTE);
        assertThat(instruction.status()).isEqualTo(PixAutoDebitInstruction.Status.AGENDADA);
    }

    private UndueDebitRefund pedirDevolucao(PixAutoDebitService service) {
        var instruction = liquidada(service);
        service.reconcile(TENANT, "COB-CONC", instruction.endToEndId(),
                new BigDecimal("100.00"), Instant.parse("2027-03-20T09:00:00Z"));
        return service.requestUndueDebitRefund(TENANT, instruction.id(),
                "debito sem autorizacao valida", Instant.parse("2027-03-20T10:00:00Z"));
    }

    @Test
    void devolucaoDeDebitoIndevidoTemPrazoDe24HorasEProtocolo() {
        var psp = new PixAutoDebitFixture.FakePsp();
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(), psp);
        var refund = pedirDevolucao(service);
        assertThat(refund.deadline()).isEqualTo(Instant.parse("2027-03-21T10:00:00Z"));
        assertThat(refund.pspProtocol()).isEqualTo("PSP-REFUND-1");
        assertThat(refund.status()).isEqualTo(UndueDebitRefund.Status.SOLICITADA);
        assertThat(psp.calls).contains("requestRefund:COB-CONC");
    }

    @Test
    void devolucaoConcluidaDentroDas24HorasENoPrazo() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var refund = pedirDevolucao(service);
        service.completeUndueDebitRefund(TENANT, refund.id(), Instant.parse("2027-03-21T09:59:00Z"));
        assertThat(refund.status()).isEqualTo(UndueDebitRefund.Status.DEVOLVIDA_NO_PRAZO);
    }

    @Test
    void devolucaoConcluidaDepoisDas24HorasFicaComAtrasoRegistrado() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var refund = pedirDevolucao(service);
        service.completeUndueDebitRefund(TENANT, refund.id(), Instant.parse("2027-03-21T10:01:00Z"));
        assertThat(refund.status()).isEqualTo(UndueDebitRefund.Status.DEVOLVIDA_COM_ATRASO);
    }

    @Test
    void pedidoVencidoSemDevolucaoNaoApareceComoCumprido() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var refund = pedirDevolucao(service);
        assertThat(refund.statusAt(Instant.parse("2027-03-21T09:59:00Z")))
                .isEqualTo(UndueDebitRefund.Status.SOLICITADA);
        assertThat(refund.statusAt(Instant.parse("2027-03-21T10:01:00Z")))
                .isEqualTo(UndueDebitRefund.Status.VENCIDO_SEM_DEVOLUCAO);
    }

    @Test
    void devolucaoExigeInstrucaoLiquidada() {
        var service = PixAutoDebitFixture.service(new PixAutoDebitFixture.InMemoryStore(),
                new PixAutoDebitFixture.FakePsp());
        var auth = service.authorize(PixAutoDebitFixture.request(1, false, false, null));
        var instruction = service.scheduleInstruction(TENANT, auth.id(),
                PixAutoDebitFixture.draft("COB-SEM-LIQ", "100.00", SETTLEMENT), LocalDate.of(2027, 3, 15));
        assertThatThrownBy(() -> service.requestUndueDebitRefund(TENANT, instruction.id(),
                "motivo", Instant.parse("2027-03-20T10:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIQUIDADA");
    }
}
