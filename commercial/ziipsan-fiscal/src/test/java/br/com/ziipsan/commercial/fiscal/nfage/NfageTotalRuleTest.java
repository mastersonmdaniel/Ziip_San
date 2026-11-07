package br.com.ziipsan.commercial.fiscal.nfage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regra de total da NT 2026.004 com somas calculadas À MÃO: itens de 60,00 (IBS 1,50;
 * CBS 5,40) e 40,00 (IBS 1,00; CBS 3,60) somam líquido 100,00, IBS 2,50 e CBS 9,00.
 * Antes de 2027 o total é 100,00; de 01/01/2027 em diante é 100,00 + 2,50 + 9,00 = 111,50.
 */
class NfageTotalRuleTest {

    private static final List<NfageTotalRule.ItemTotal> ITENS = List.of(
            new NfageTotalRule.ItemTotal(new BigDecimal("60.00"), new BigDecimal("1.50"), new BigDecimal("5.40")),
            new NfageTotalRule.ItemTotal(new BigDecimal("40.00"), new BigDecimal("1.00"), new BigDecimal("3.60")));

    @Test
    @DisplayName("fronteira exata: 31/12/2026 NÃO soma IBS/CBS; 01/01/2027 soma (NT 2026.004)")
    void boundary2027() {
        assertThat(NfageTotalRule.somaIbsCbsNoTotal(LocalDate.of(2026, 12, 31))).isFalse();
        assertThat(NfageTotalRule.somaIbsCbsNoTotal(LocalDate.of(2027, 1, 1))).isTrue();
        assertThat(NfageTotalRule.INICIO_SOMA_IBS_CBS).isEqualTo(LocalDate.of(2027, 1, 1));

        assertThat(NfageTotalRule.totalDocumento(LocalDate.of(2026, 12, 15), ITENS))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(NfageTotalRule.totalDocumento(LocalDate.of(2026, 12, 31), ITENS))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(NfageTotalRule.totalDocumento(LocalDate.of(2027, 1, 1), ITENS))
                .isEqualByComparingTo(new BigDecimal("111.50"));
        assertThat(NfageTotalRule.totalDocumento(LocalDate.of(2027, 6, 30), ITENS))
                .isEqualByComparingTo(new BigDecimal("111.50"));
    }

    @Test
    @DisplayName("antes de 2027, item sem o grupo IBS/CBS (condicional ao MOC) soma só o líquido")
    void before2027MissingGroupIsNetOnly() {
        List<NfageTotalRule.ItemTotal> semGrupo = List.of(
                new NfageTotalRule.ItemTotal(new BigDecimal("60.00"), null, null),
                new NfageTotalRule.ItemTotal(new BigDecimal("40.00"), null, null));
        assertThat(NfageTotalRule.totalDocumento(LocalDate.of(2026, 12, 1), semGrupo))
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("de 2027 em diante, item sem valor de IBS ou de CBS é erro explícito, nunca zero presumido")
    void from2027MissingTaxIsAnError() {
        List<NfageTotalRule.ItemTotal> semIbs = List.of(
                new NfageTotalRule.ItemTotal(new BigDecimal("60.00"), null, new BigDecimal("5.40")));
        assertThatThrownBy(() -> NfageTotalRule.totalDocumento(LocalDate.of(2027, 1, 1), semIbs))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IBS");
        List<NfageTotalRule.ItemTotal> semCbs = List.of(
                new NfageTotalRule.ItemTotal(new BigDecimal("60.00"), new BigDecimal("1.50"), null));
        assertThatThrownBy(() -> NfageTotalRule.totalDocumento(LocalDate.of(2027, 1, 1), semCbs))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IBS");
    }

    @Test
    @DisplayName("valor de IBS/CBS negativo é erro em qualquer ano, inclusive antes de 2027 (revisão cruzada)")
    void negativeTaxIsAlwaysAnError() {
        List<NfageTotalRule.ItemTotal> ibsNegativo = List.of(
                new NfageTotalRule.ItemTotal(new BigDecimal("60.00"), new BigDecimal("-1.50"), new BigDecimal("5.40")));
        assertThatThrownBy(() -> NfageTotalRule.totalDocumento(LocalDate.of(2026, 12, 1), ibsNegativo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativo");
        List<NfageTotalRule.ItemTotal> cbsNegativo = List.of(
                new NfageTotalRule.ItemTotal(new BigDecimal("60.00"), new BigDecimal("1.50"), new BigDecimal("-5.40")));
        assertThatThrownBy(() -> NfageTotalRule.totalDocumento(LocalDate.of(2027, 1, 1), cbsNegativo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativo");
    }

    @Test
    @DisplayName("sem itens ou com valor negativo não há total")
    void invalidInput() {
        assertThatThrownBy(() -> NfageTotalRule.totalDocumento(LocalDate.of(2026, 12, 1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NfageTotalRule.totalDocumento(LocalDate.of(2026, 12, 1),
                List.of(new NfageTotalRule.ItemTotal(new BigDecimal("-1.00"), null, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
