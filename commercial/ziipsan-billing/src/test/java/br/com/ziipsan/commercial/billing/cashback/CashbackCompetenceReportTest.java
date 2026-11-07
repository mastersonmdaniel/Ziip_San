package br.com.ziipsan.commercial.billing.cashback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Relatório por competência e efeito tributário (P2.34), com somas à mão:
 * competência 02/2029 com dois domicílios — A: CBS 30,00 + IBS 2,00 = 32,00;
 * B: CBS 10,00 + IBS 6,67 = 16,67 — totais CBS 40,00, IBS 8,67, geral
 * <b>48,67</b>. Uma devolução de outra competência não entra.
 */
class CashbackCompetenceReportTest {

    private static CashbackStore.DevolucaoRegistrada devolucao(String hash, LocalDate competencia,
                                                               String cbs, String ibs) {
        BigDecimal c = new BigDecimal(cbs);
        BigDecimal i = new BigDecimal(ibs);
        return new CashbackStore.DevolucaoRegistrada(UUID.randomUUID(), UUID.randomUUID(), hash,
                YearMonth.of(2029, 1), UUID.randomUUID(), competencia,
                UUID.randomUUID(), UUID.randomUUID(),
                c, i, c, i, c.add(i), LocalDate.of(2029, 1, 31));
    }

    @Test
    @DisplayName("relatório da competência: valor por domicílio e total (40,00 + 8,67 = 48,67)")
    void reportByCompetence() {
        var a = devolucao("a".repeat(64), LocalDate.of(2029, 2, 1), "30.00", "2.00");
        var b = devolucao("b".repeat(64), LocalDate.of(2029, 2, 1), "10.00", "6.67");
        var outra = devolucao("c".repeat(64), LocalDate.of(2029, 3, 1), "99.00", "99.00");

        var rel = CashbackCompetenceReport.relatorio(YearMonth.of(2029, 2), List.of(b, a, outra));

        assertThat(rel.linhas()).hasSize(2);
        assertThat(rel.linhas().get(0).domicileKeyHash()).startsWith("a");
        assertThat(rel.linhas().get(0).totalDevolvido()).isEqualByComparingTo("32.00");
        assertThat(rel.linhas().get(1).totalDevolvido()).isEqualByComparingTo("16.67");
        assertThat(rel.totalCbs()).isEqualByComparingTo("40.00");
        assertThat(rel.totalIbs()).isEqualByComparingTo("8.67");
        assertThat(rel.totalGeral()).isEqualByComparingTo("48.67");
        // As notas declaram os condicionantes — nunca omitidos do relatório.
        assertThat(rel.notas()).anyMatch(n -> n.contains("LC 227/2026"));
        assertThat(rel.notas()).anyMatch(n -> n.contains("autarquia"));
    }

    @Test
    @DisplayName("efeito tributário por competência (reequilíbrio): agrega por mês da conta, ordenado")
    void taxEffectsByCompetence() {
        var a = devolucao("a".repeat(64), LocalDate.of(2029, 2, 1), "30.00", "2.00");
        var b = devolucao("b".repeat(64), LocalDate.of(2029, 2, 1), "10.00", "6.67");
        var c = devolucao("c".repeat(64), LocalDate.of(2029, 3, 1), "5.00", "1.00");

        var efeitos = CashbackCompetenceReport.efeitosPorCompetencia(List.of(c, b, a));

        assertThat(efeitos).hasSize(2);
        assertThat(efeitos.get(0).competence()).isEqualTo(YearMonth.of(2029, 2));
        assertThat(efeitos.get(0).totalDevolvido()).isEqualByComparingTo("48.67");
        assertThat(efeitos.get(0).devolucoes()).isEqualTo(2);
        assertThat(efeitos.get(1).competence()).isEqualTo(YearMonth.of(2029, 3));
        assertThat(efeitos.get(1).totalDevolvido()).isEqualByComparingTo("6.00");
        assertThat(efeitos.get(1).devolucoes()).isEqualTo(1);
    }
}
