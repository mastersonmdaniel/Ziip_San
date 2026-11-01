package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static org.assertj.core.api.Assertions.assertThat;

/** Casos G-* (seção 10.14): quando gerar e quando não gerar a conta, e idempotência (G-05). */
class BillGenerationRuleTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** água, esgoto, ligados (ambos), fora de condomínio, sem suspensão, sem débito, sem crédito */
    private static BillGenerationRule.Facts facts(BigDecimal water, BigDecimal sewer) {
        return new BillGenerationRule.Facts(water, sewer, true, true, false, false, false, false, false);
    }

    private static BillGenerationRule.Facts with(BillGenerationRule.Facts f, Boolean waterConnected, Boolean sewerConnected,
                                                 Boolean condo, Boolean suspended, Boolean unpaidDebit,
                                                 Boolean pendingCredit, Boolean debitGenerates) {
        return new BillGenerationRule.Facts(f.waterValue(), f.sewerValue(),
                waterConnected != null ? waterConnected : f.waterConnected(),
                sewerConnected != null ? sewerConnected : f.sewerConnected(),
                condo != null ? condo : f.belongsToCondominium(),
                suspended != null ? suspended : f.billingSuspended(),
                unpaidDebit != null ? unpaidDebit : f.hasUnpaidDebitNotInReview(),
                pendingCredit != null ? pendingCredit : f.hasPendingCredit(),
                debitGenerates != null ? debitGenerates : f.hasDebitThatGeneratesBill());
    }

    @Test
    @DisplayName("G-01: água 0, esgoto 0, sem débitos nem créditos, ligado → não gera")
    void g01() {
        assertThat(BillGenerationRule.shouldGenerate(facts(ZERO, ZERO))).isFalse();
    }

    @Test
    @DisplayName("G-02: água 0, esgoto 0, com débito a cobrar 'gera conta' sem pagamento → gera")
    void g02() {
        BillGenerationRule.Facts f = with(facts(ZERO, ZERO), null, null, null, null, true, false, true);

        assertThat(BillGenerationRule.shouldGenerate(f)).isTrue();
    }

    @Test
    @DisplayName("G-03: situação especial paralisa o faturamento → não gera, mesmo com consumo e débito")
    void g03() {
        BillGenerationRule.Facts f = with(facts(d("30.00"), ZERO), null, null, null, true, true, false, true);

        assertThat(BillGenerationRule.shouldGenerate(f)).isFalse();
    }

    @Test
    @DisplayName("G-04: débito a cobrar já pago e nada mais → não gera")
    void g04() {
        BillGenerationRule.Facts f = with(facts(ZERO, ZERO), null, null, null, null, false, false, true);

        assertThat(BillGenerationRule.shouldGenerate(f)).isFalse();
    }

    @Test
    @DisplayName("com valor de água ou de esgoto, gera")
    void consumptionGenerates() {
        assertThat(BillGenerationRule.shouldGenerate(facts(d("30.00"), ZERO))).isTrue();
        assertThat(BillGenerationRule.shouldGenerate(facts(ZERO, d("24.00")))).isTrue();
    }

    @Test
    @DisplayName("débito sem pagamento que NÃO gera conta e sem crédito pendente → não gera; com crédito pendente → gera")
    void debitThatDoesNotGenerateNeedsACredit() {
        BillGenerationRule.Facts base = with(facts(ZERO, ZERO), null, null, null, null, true, false, false);
        assertThat(BillGenerationRule.shouldGenerate(base)).isFalse();

        assertThat(BillGenerationRule.shouldGenerate(with(base, null, null, null, null, null, true, null))).isTrue();
    }

    @Test
    @DisplayName("sem ligação nenhuma e fora de condomínio, mesmo com valor, a situação não gera; crédito pendente sem débito também não")
    void noConnectionOutsideCondominium() {
        BillGenerationRule.Facts f = with(facts(d("30.00"), ZERO), false, false, false, null, null, null, null);
        assertThat(BillGenerationRule.shouldGenerate(f)).isFalse();

        BillGenerationRule.Facts onlyCredit = with(facts(ZERO, ZERO), null, null, null, null, false, true, false);
        assertThat(BillGenerationRule.shouldGenerate(onlyCredit)).isFalse();
    }

    @Test
    @DisplayName("condomínio sem consumo e sem ligação própria gera (para ratear)")
    void condominiumWithoutConsumption() {
        BillGenerationRule.Facts f = with(facts(ZERO, ZERO), false, false, true, null, null, null, null);

        assertThat(BillGenerationRule.shouldGenerate(f)).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // G-05: idempotência
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("G-05: a 2ª execução do faturamento não cria 2ª conta; só a pré-faturada é reprocessada")
    void g05() {
        assertThat(BillIdempotency.decide(List.of())).isEqualTo(BillIdempotency.Decision.CREATE);
        assertThat(BillIdempotency.decide(List.of(BillStatus.NORMAL))).isEqualTo(BillIdempotency.Decision.ALREADY_BILLED);
        assertThat(BillIdempotency.decide(List.of(BillStatus.PRE_BILLED))).isEqualTo(BillIdempotency.Decision.REPLACE_PRE_BILLED);
        assertThat(BillIdempotency.decide(List.of(BillStatus.NORMAL, BillStatus.PAID))).isEqualTo(BillIdempotency.Decision.ALREADY_BILLED);
    }

    @Test
    @DisplayName("conta cancelada, cancelada por retificação ou com erro libera a referência; retificação convive com a substituída")
    void deadStatusesFreeTheReference() {
        assertThat(BillIdempotency.decide(List.of(BillStatus.CANCELLED))).isEqualTo(BillIdempotency.Decision.CREATE);
        assertThat(BillIdempotency.decide(List.of(BillStatus.PROCESSING_ERROR))).isEqualTo(BillIdempotency.Decision.CREATE);
        // retificação: a antiga fica CANCELLED_BY_RECTIFICATION e a nova RECTIFIED: uma só viva
        assertThat(BillIdempotency.decide(List.of(BillStatus.CANCELLED_BY_RECTIFICATION, BillStatus.RECTIFIED)))
                .isEqualTo(BillIdempotency.Decision.ALREADY_BILLED);
    }

    @Test
    @DisplayName("todos os 14 estados da seção 5.3 existem e exatamente 3 liberam a referência")
    void statusCatalog() {
        assertThat(BillStatus.values()).hasSize(14);
        Set<BillStatus> dead = EnumSet.noneOf(BillStatus.class);
        for (BillStatus s : BillStatus.values()) {
            if (!s.live()) {
                dead.add(s);
            }
        }
        assertThat(dead).containsExactlyInAnyOrder(BillStatus.CANCELLED, BillStatus.CANCELLED_BY_RECTIFICATION,
                BillStatus.PROCESSING_ERROR);
    }
}
