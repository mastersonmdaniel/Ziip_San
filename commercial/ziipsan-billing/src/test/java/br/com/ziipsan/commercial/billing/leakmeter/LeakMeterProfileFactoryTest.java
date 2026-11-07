package br.com.ziipsan.commercial.billing.leakmeter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.28 -- TESTE POR PERFIL (aceite: "valores de fabrica dos perfis ARIS,
 * AGIR e ARESC lidos dos perfis regulatorios carregados pela P2.31
 * (RAS-01); teste por perfil").
 *
 * <p>Os valores de cada cenario foram TRANSCRITOS A MAO de
 * {@code ScRegulatoryProfiles} da P2.31 (fonte unica dos perfis) e
 * conferidos contra a carga {@code R__regulatory_profile_catalog.sql}:
 * <ul>
 *   <li><b>ARIS</b> ({@code ARIS_RN19_2019}): {@code leak.max_discount_pct_water}
 *       = 70 (RN 19/2019, art. 89, §§1º a 8º); a nota do valor registra
 *       "reclamacao em ate 45 dias da leitura" e NAO registra limite de
 *       faturas -- prazo 45, ancora READING_DATE, sem teto (declarado na
 *       ativacao com o nulo JSON explicito da chave obrigatoria -- M1);</li>
 *   <li><b>ARESC</b> ({@code ARESC_RES046_2016}): 70 (Res. 046/2016,
 *       art. 97); a nota registra "2 faturas em 12 meses; pedido em 30 dias
 *       do vencimento" -- prazo 30, ancora DUE_DATE, teto 2 ANY;</li>
 *   <li><b>AGIR</b> ({@code AGIR_RN001_2013}): 70 (RN 001/2013, art. 107);
 *       a nota registra "2 faturas sequenciais em 12 meses; pedido em 30
 *       dias do vencimento" -- prazo 30, ancora DUE_DATE, teto 2
 *       SEQUENTIAL.</li>
 * </ul>
 *
 * <p>O unico valor ESTRUTURADO que a P2.31 carrega e o percentual (70); os
 * demais entram nestes cenarios como o tenant os receberia na ativacao a
 * partir das notas do perfil. {@code leak.sewer_by_average = true} e
 * escolha do tenant do cenario (nenhum perfil o carrega) -- declarada aqui
 * para nao passar por valor de fabrica.
 *
 * <p>Massa comum, conferida a mao: agua medida 50, media 20 (excesso 30;
 * 70% de 30 = 21; revisto por percentual = 29); esgoto medido 40, media 16
 * (pela media = 16). MOST_FAVORABLE compara percentual (29+16=45) com
 * media (20+16=36) e escolhe a media.
 */
class LeakMeterProfileFactoryTest {

    private static final LeakDiscountRule.Volumes VOLUMES = new LeakDiscountRule.Volumes(
            new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("40"), new BigDecimal("16"));

    private static LeakMeterParameters profile(String deadlineDays, String anchor, String maxBills,
                                               String limitMode, String discountRule) {
        Map<String, String> map = new HashMap<>();
        map.put(LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "70");
        map.put(LeakMeterParameters.LEAK_CLAIM_DEADLINE_DAYS, deadlineDays);
        map.put(LeakMeterParameters.LEAK_CLAIM_DEADLINE_ANCHOR, "\"" + anchor + "\"");
        if (maxBills != null) {
            map.put(LeakMeterParameters.LEAK_MAX_BILLS_PER_12_MONTHS, maxBills);
        }
        if (limitMode != null) {
            map.put(LeakMeterParameters.LEAK_BILLS_LIMIT_MODE, "\"" + limitMode + "\"");
        }
        map.put(LeakMeterParameters.LEAK_DISCOUNT_RULE, "\"" + discountRule + "\"");
        map.put(LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true");
        return LeakMeterParameters.fromValues(map);
    }

    @Test
    @DisplayName("ARIS (RN 19/2019 art. 89): 70% do excesso; 45 dias da LEITURA; sem teto de faturas")
    void arisProfile() {
        LeakMeterParameters pct = profile("45", "READING_DATE", "null", null, "PCT_OF_EXCESS");
        assertThat(pct.maxDiscountPctWater()).isEqualByComparingTo("70");
        // Prazo: leitura em 10/03/2026 + 45 dias = 24/04/2026 (ultimo dia tempestivo).
        LocalDate reading = LocalDate.of(2026, 3, 10);
        assertThat(LeakClaimDeadlineRule.evaluate(pct, LocalDate.of(2026, 4, 24), reading, null).timely()).isTrue();
        assertThat(LeakClaimDeadlineRule.evaluate(pct, LocalDate.of(2026, 4, 25), reading, null).timely()).isFalse();
        // Desconto por percentual: 29 de agua e esgoto pela media (16).
        LeakDiscountRule.Result r = LeakDiscountRule.apply(pct, VOLUMES);
        assertThat(r.revisedWater()).isEqualByComparingTo("29");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        // Sem teto no perfil: tres faturas ja beneficiadas nao barram a quarta.
        LeakEligibilityRule.Eligibility e = LeakEligibilityRule.evaluate(pct,
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
                List.of(YearMonth.of(2026, 4)));
        assertThat(e.eligible()).isTrue();
        assertThat(e.maxBills()).isNull();
        // E o mais favoravel do perfil (sem regra declarada, MOST_FAVORABLE) escolhe a media.
        LeakMeterParameters favorable = profile("45", "READING_DATE", "null", null, "MOST_FAVORABLE");
        assertThat(LeakDiscountRule.apply(favorable, VOLUMES).revisedWater()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("ARESC (Res. 046/2016 art. 97): 70% do excesso; 30 dias do VENCIMENTO; teto de 2 faturas em 12 meses")
    void arescProfile() {
        LeakMeterParameters pct = profile("30", "DUE_DATE", "2", "ANY_IN_12_MONTHS", "PCT_OF_EXCESS");
        assertThat(pct.maxDiscountPctWater()).isEqualByComparingTo("70");
        // Prazo: vencimento em 20/03/2026 + 30 dias = 19/04/2026.
        LocalDate due = LocalDate.of(2026, 3, 20);
        assertThat(LeakClaimDeadlineRule.evaluate(pct, LocalDate.of(2026, 4, 19), null, due).timely()).isTrue();
        assertThat(LeakClaimDeadlineRule.evaluate(pct, LocalDate.of(2026, 4, 20), null, due).timely()).isFalse();
        // Desconto por percentual: os mesmos 29/16 da massa comum.
        LeakDiscountRule.Result r = LeakDiscountRule.apply(pct, VOLUMES);
        assertThat(r.revisedWater()).isEqualByComparingTo("29");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        // Teto: 2 ja beneficiadas na janela barram a terceira; 1 + 1 passa.
        assertThat(LeakEligibilityRule.evaluate(pct,
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
                List.of(YearMonth.of(2026, 3))).eligible()).isFalse();
        assertThat(LeakEligibilityRule.evaluate(pct,
                List.of(YearMonth.of(2026, 1)),
                List.of(YearMonth.of(2026, 3))).eligible()).isTrue();
    }

    @Test
    @DisplayName("AGIR (RN 001/2013 art. 107): 70% do excesso; 30 dias do VENCIMENTO; teto de 2 faturas SEQUENCIAIS")
    void agirProfile() {
        LeakMeterParameters pct = profile("30", "DUE_DATE", "2", "SEQUENTIAL_IN_12_MONTHS", "PCT_OF_EXCESS");
        assertThat(pct.maxDiscountPctWater()).isEqualByComparingTo("70");
        LocalDate due = LocalDate.of(2026, 3, 20);
        assertThat(LeakClaimDeadlineRule.evaluate(pct, LocalDate.of(2026, 4, 19), null, due).timely()).isTrue();
        assertThat(LeakClaimDeadlineRule.evaluate(pct, LocalDate.of(2026, 4, 20), null, due).timely()).isFalse();
        LeakDiscountRule.Result r = LeakDiscountRule.apply(pct, VOLUMES);
        assertThat(r.revisedWater()).isEqualByComparingTo("29");
        assertThat(r.revisedSewer()).isEqualByComparingTo("16");
        // Sequencial: o bloco 2026-01 + 2026-02 passa; 2026-01 + 2026-03 (buraco) nao.
        assertThat(LeakEligibilityRule.evaluate(pct, List.of(),
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2))).eligible()).isTrue();
        LeakEligibilityRule.Eligibility gap = LeakEligibilityRule.evaluate(pct, List.of(),
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 3)));
        assertThat(gap.eligible()).isFalse();
        assertThat(gap.sequentialSatisfied()).isFalse();
    }
}
