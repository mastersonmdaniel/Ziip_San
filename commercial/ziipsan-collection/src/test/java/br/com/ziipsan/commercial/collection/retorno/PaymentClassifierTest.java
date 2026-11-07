package br.com.ziipsan.commercial.collection.retorno;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.1 - um teste por situacao de classificacao, valores calculados a mao.
 * Base recorrente: conta de 100,00 e tolerancia pay.adjust_tolerance de 5,00.
 */
class PaymentClassifierTest {

    private final PaymentClassifier classifier = new PaymentClassifier();
    private static final UUID BILL = UUID.randomUUID();
    private static final BigDecimal TOLERANCE = new BigDecimal("5.00");

    private static PaymentClassifier.ClassificationInput input(
            PaymentClassifier.BillState state, String bill, String charges, boolean review,
            String paid, UUID transferTo) {
        return new PaymentClassifier.ClassificationInput(BILL, state, new BigDecimal(bill),
                charges == null ? null : new BigDecimal(charges), review,
                new BigDecimal(paid), TOLERANCE, transferTo);
    }

    @Test
    void situacaoExataBaixaIntegral() {
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false, "100.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.EXACT_SETTLEMENT);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.SETTLEMENT);
        assertThat(d.settledAmount()).isEqualByComparingTo("100.00");
        assertThat(d.effectAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void situacaoExataComAcrescimoQuandoNaoHaRevisao() {
        // 100,00 + 4,00 de acrescimo = 106,00 devidos; pago 106,00 baixa integral.
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "4.00", false, "106.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.EXACT_SETTLEMENT);
        assertThat(d.settledAmount()).isEqualByComparingTo("106.00");
    }

    @Test
    void semAcrescimoSobreContaEmRevisaoAberta() {
        // Revisao aberta: os 4,00 de acrescimo sao ignorados; devido = 100,00.
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "4.00", true, "100.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.EXACT_SETTLEMENT);
        assertThat(d.settledAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void situacaoAMenorDentroDaToleranciaGeraDebitoNaContaSeguinte() {
        // Pago 96,00 de 100,00: residual 4,00 <= 5,00 de tolerancia.
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false, "96.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.UNDERPAID_WITHIN_TOLERANCE);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.NEXT_BILL_DEBIT);
        assertThat(d.effectAmount()).isEqualByComparingTo("4.00");
        assertThat(d.settledAmount()).isEqualByComparingTo("96.00");
    }

    @Test
    void situacaoAMaiorDentroDaToleranciaGeraCreditoNaContaSeguinte() {
        // Pago 103,00 de 100,00: sobra 3,00 <= 5,00 de tolerancia.
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false, "103.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.OVERPAID_WITHIN_TOLERANCE);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.NEXT_BILL_CREDIT);
        assertThat(d.effectAmount()).isEqualByComparingTo("3.00");
        assertThat(d.settledAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void diferencaExatamenteNaToleranciaAindaEstaDentroDela() {
        // Fronteira inclusiva de pay.adjust_tolerance: diferenca == 5,00 NAO e "fora".
        // Pago 95,00 de 100,00: residual 5,00 == tolerancia -> debito na conta seguinte.
        var under = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false, "95.00", null));
        assertThat(under.situation()).isEqualTo(PaymentSituation.UNDERPAID_WITHIN_TOLERANCE);
        assertThat(under.effect()).isEqualTo(PaymentClassifier.Effect.NEXT_BILL_DEBIT);
        assertThat(under.effectAmount()).isEqualByComparingTo("5.00");
        // Pago 105,00 de 100,00: sobra 5,00 == tolerancia -> credito na conta seguinte.
        var over = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false, "105.00", null));
        assertThat(over.situation()).isEqualTo(PaymentSituation.OVERPAID_WITHIN_TOLERANCE);
        assertThat(over.effect()).isEqualTo(PaymentClassifier.Effect.NEXT_BILL_CREDIT);
        assertThat(over.effectAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void situacaoDiferencaForaDaToleranciaFicaPendenteDeClassificacaoManual() {
        // Pago 94,00 de 100,00: diferenca 6,00 > 5,00 - nenhum efeito automatico.
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false, "94.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.DIFFERENCE_BEYOND_TOLERANCE);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.NONE_PENDING_MANUAL);
        assertThat(d.effectAmount()).isEqualByComparingTo("6.00");
        assertThat(d.settledAmount()).isEqualByComparingTo("0.00");
        assertThat(d.requiresManualApproval()).isTrue();
    }

    @Test
    void situacaoDuplicidadeAvisaUsuarioERepassaDevolucaoParaP329() {
        var d = classifier.classify(input(PaymentClassifier.BillState.PAID, "100.00", "0.00", false, "100.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.DUPLICATE_PAYMENT);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.DUPLICATE_REFUND_HANDOFF);
        assertThat(d.notifyUserOfDuplicate()).isTrue();
        assertThat(d.refundable()).isTrue();
        assertThat(d.settledAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void situacaoContaPrescritaERecebimentoValidoSemDevolucao() {
        // CC, art. 882: paga divida prescrita nao tem repeticao; evento p/ ED-32.
        var d = classifier.classify(input(PaymentClassifier.BillState.PRESCRIBED, "100.00", "0.00", false, "100.00", null));
        assertThat(d.situation()).isEqualTo(PaymentSituation.PRESCRIBED_BILL_PAYMENT);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.CREDIT_RECOVERY);
        assertThat(d.refundable()).isFalse();
        assertThat(d.notifyUserOfDuplicate()).isFalse();
        assertThat(d.emitsCreditRecoveryEvent()).isTrue();
        assertThat(d.settledAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void situacaoTransferenciaExigeClassificacaoManualComAprovacaoDupla() {
        var d = classifier.classify(input(PaymentClassifier.BillState.OPEN, "100.00", "0.00", false,
                "100.00", UUID.randomUUID()));
        assertThat(d.situation()).isEqualTo(PaymentSituation.PAYMENT_TRANSFER);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.TRANSFER);
        assertThat(d.requiresManualApproval()).isTrue();
        assertThat(d.settledAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void situacaoNaoIdentificadoVaiParaFilaSemBaixa() {
        var in = new PaymentClassifier.ClassificationInput(null, null, null, null, false,
                new BigDecimal("100.00"), TOLERANCE, null);
        var d = classifier.classify(in);
        assertThat(d.situation()).isEqualTo(PaymentSituation.UNIDENTIFIED);
        assertThat(d.effect()).isEqualTo(PaymentClassifier.Effect.UNIDENTIFIED_QUEUE);
        assertThat(d.settledAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void transferenciaParaAPropriaContaERecusada() {
        assertThatThrownBy(() -> classifier.classify(input(PaymentClassifier.BillState.OPEN,
                "100.00", "0.00", false, "100.00", BILL)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
