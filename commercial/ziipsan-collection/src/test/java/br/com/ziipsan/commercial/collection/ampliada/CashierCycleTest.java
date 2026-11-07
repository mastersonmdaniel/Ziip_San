package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.collection.retorno.PaymentClassifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-26 - ciclo completo do caixa proprio (teste exigido pela spec).
 * Caso a mao: fundo 100,00; especie 150,00 (conta de 150,00, baixa exata P3.1);
 * cartao 80,00 (nao entra no gaveta); sangria 50,00.
 * Esperado em especie = 100 + 150 - 50 = 200,00.
 *
 * <p>Pos-revisao (M3): os casos originais montam a entrada completa da P3.1
 * declarando estado ABERTA, encargos 0,00 e sem revisao - os mesmos valores
 * que a versao anterior fixava por dentro; as assercoes sao as mesmas.
 */
class CashierCycleTest {

    private static PaymentClassifier.ClassificationInput entrada(UUID billId,
            PaymentClassifier.BillState state, String billAmount, String charges,
            boolean underReview, String paid) {
        return new PaymentClassifier.ClassificationInput(billId, state,
                new BigDecimal(billAmount), new BigDecimal(charges), underReview,
                new BigDecimal(paid), new BigDecimal("5.00"), null);
    }

    private static PaymentClassifier.ClassificationInput aberta(UUID billId, String billAmount, String paid) {
        return entrada(billId, PaymentClassifier.BillState.OPEN, billAmount, "0.00", false, paid);
    }

    private CashierCycle caixaAberto() {
        var cx = new CashierCycle();
        cx.open(UUID.randomUUID(), true, new BigDecimal("100.00"));
        return cx;
    }

    @Test
    void cicloCompletoConferidoComComprovantesEClassificacaoDaP31() {
        var cx = caixaAberto();
        var r1 = cx.receive(CashierCycle.Tender.CASH, aberta(UUID.randomUUID(), "150.00", "150.00"));
        var r2 = cx.receive(CashierCycle.Tender.CARD, aberta(UUID.randomUUID(), "80.00", "80.00"));
        assertThat(r1.number()).isEqualTo(1);
        assertThat(r2.number()).isEqualTo(2);
        assertThat(r1.classification().effect()).isEqualTo(PaymentClassifier.Effect.SETTLEMENT);
        cx.bleed(new BigDecimal("50.00"));
        var closing = cx.close(new BigDecimal("200.00"));
        assertThat(closing.expectedCash()).isEqualByComparingTo("200.00");
        assertThat(closing.difference()).isEqualByComparingTo("0.00");
        assertThat(closing.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
        assertThat(closing.receipts()).hasSize(2);
    }

    @Test
    void fechamentoComFaltaRegistraDivergenciaSemAjustar() {
        var cx = caixaAberto();
        cx.receive(CashierCycle.Tender.CASH, aberta(UUID.randomUUID(), "150.00", "150.00"));
        var closing = cx.close(new BigDecimal("245.00"));
        assertThat(closing.expectedCash()).isEqualByComparingTo("250.00");
        assertThat(closing.difference()).isEqualByComparingTo("-5.00");
        assertThat(closing.status()).isEqualTo(CashierCycle.ClosingStatus.DIVERGENCIA);
    }

    @Test
    void sangriaMaiorQueOEspecieERecusada() {
        var cx = caixaAberto();
        assertThatThrownBy(() -> cx.bleed(new BigDecimal("100.01")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void caixaDesligadoNaoAbreENadaOperaSemAbertura() {
        var cx = new CashierCycle();
        assertThatThrownBy(() -> cx.open(UUID.randomUUID(), false, new BigDecimal("0.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cx.receive(CashierCycle.Tender.CASH,
                aberta(UUID.randomUUID(), "10.00", "10.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- Novos pos-revisao ---

    @Test
    void reaberturaAposFechamentoReiniciaOCiclo() {
        // M2: abrir -> fechar -> reabrir -> receber, na MESMA instancia.
        var cx = caixaAberto();
        cx.receive(CashierCycle.Tender.CASH, aberta(UUID.randomUUID(), "150.00", "150.00"));
        var primeiro = cx.close(new BigDecimal("250.00"));
        assertThat(primeiro.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
        assertThat(primeiro.receipts()).hasSize(1);

        UUID operador2 = UUID.randomUUID();
        cx.open(operador2, true, new BigDecimal("50.00"));
        assertThat(cx.operatorId()).isEqualTo(operador2);
        var r = cx.receive(CashierCycle.Tender.CASH, aberta(UUID.randomUUID(), "20.00", "20.00"));
        assertThat(r.number()).isEqualTo(1);
        var segundo = cx.close(new BigDecimal("70.00"));
        assertThat(segundo.expectedCash()).isEqualByComparingTo("70.00");
        assertThat(segundo.difference()).isEqualByComparingTo("0.00");
        assertThat(segundo.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
        assertThat(segundo.receipts()).hasSize(1);
    }

    @Test
    void especieDeContaJaPagaNaoEntraNaGaveta() {
        // M3: conta JA PAGA - duplicidade (P3.1), settledAmount = 0: o especie
        // NAO soma ao esperado da gaveta; fica no comprovante p/ devolucao (P3.29).
        var cx = caixaAberto();
        var r = cx.receive(CashierCycle.Tender.CASH, entrada(UUID.randomUUID(),
                PaymentClassifier.BillState.PAID, "150.00", "0.00", false, "150.00"));
        assertThat(r.classification().effect())
                .isEqualTo(PaymentClassifier.Effect.DUPLICATE_REFUND_HANDOFF);
        assertThat(r.classification().settledAmount()).isEqualByComparingTo("0.00");
        var closing = cx.close(new BigDecimal("100.00"));
        assertThat(closing.expectedCash()).isEqualByComparingTo("100.00");
        assertThat(closing.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
        assertThat(closing.receipts()).hasSize(1);
    }

    @Test
    void especieSemBaixaPorDiferencaForaDaToleranciaNaoEntraNaGaveta() {
        // M3: pago 150,00 em conta de 100,00 com tolerancia 5,00 -> diferenca
        // fora da tolerancia, NONE_PENDING_MANUAL, settledAmount = 0: sem gaveta.
        var cx = caixaAberto();
        var r = cx.receive(CashierCycle.Tender.CASH, aberta(UUID.randomUUID(), "100.00", "150.00"));
        assertThat(r.classification().effect())
                .isEqualTo(PaymentClassifier.Effect.NONE_PENDING_MANUAL);
        assertThat(r.classification().settledAmount()).isEqualByComparingTo("0.00");
        var closing = cx.close(new BigDecimal("100.00"));
        assertThat(closing.expectedCash()).isEqualByComparingTo("100.00");
        assertThat(closing.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
    }

    @Test
    void recebimentoComEncargosReaisBaixaPeloDevidoComAcrescimos() {
        // M3: encargos reais da conta (5,00) entram na classificacao: devido
        // 105,00, pago 105,00 -> baixa exata e a gaveta soma os 105,00 pagos.
        var cx = caixaAberto();
        var r = cx.receive(CashierCycle.Tender.CASH, entrada(UUID.randomUUID(),
                PaymentClassifier.BillState.OPEN, "100.00", "5.00", false, "105.00"));
        assertThat(r.classification().effect()).isEqualTo(PaymentClassifier.Effect.SETTLEMENT);
        assertThat(r.classification().settledAmount()).isEqualByComparingTo("105.00");
        var closing = cx.close(new BigDecimal("205.00"));
        assertThat(closing.expectedCash()).isEqualByComparingTo("205.00");
        assertThat(closing.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
    }

    @Test
    void recebimentoEmRevisaoAbertaIgnoraEncargosComoNaP31() {
        // M3/P3.1: conta em revisao aberta nao gera acrescimo na classificacao:
        // devido = 100,00 (encargos de 5,00 ignorados), pago 100,00 -> baixa exata.
        var cx = caixaAberto();
        var r = cx.receive(CashierCycle.Tender.CASH, entrada(UUID.randomUUID(),
                PaymentClassifier.BillState.OPEN, "100.00", "5.00", true, "100.00"));
        assertThat(r.classification().effect()).isEqualTo(PaymentClassifier.Effect.SETTLEMENT);
        assertThat(r.classification().settledAmount()).isEqualByComparingTo("100.00");
        var closing = cx.close(new BigDecimal("200.00"));
        assertThat(closing.expectedCash()).isEqualByComparingTo("200.00");
        assertThat(closing.status()).isEqualTo(CashierCycle.ClosingStatus.CONFERIDO);
    }
}
