package br.com.ziipsan.commercial.registry.legacyxml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF das contas antigas (P2.19) a partir dos dados importados pela P2.10.
 * Massa 100% sintética declarada aqui. Valores conferidos à mão:
 * 150,00 + 200,50 = 350,50 → total "350,50"; formatação de centavos
 * conferida em 123456 → "1.234,56" e 5 → "0,05".
 */
class LegacyHistoryPdfServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private static LegacyHistoryPdfService serviceCom(List<LegacyHistoryDataPort.ImportedBill> bills) {
        return new LegacyHistoryPdfService((tenantId, unitLegacyKey) -> bills);
    }

    @Test
    @DisplayName("PDF válido com a indicação de histórico importado, as contas ordenadas e o total conferido")
    void pdfDoHistoricoImportado() {
        LegacyHistoryPdfService service = serviceCom(List.of(
                new LegacyHistoryDataPort.ImportedBill("LEG-1", "910001", YearMonth.of(2025, 4),
                        new BigDecimal("12.500"), 20050L, LocalDate.of(2025, 5, 10)),
                new LegacyHistoryDataPort.ImportedBill("LEG-1", "910001", YearMonth.of(2025, 3),
                        new BigDecimal("12.000"), 15000L, LocalDate.of(2025, 4, 10))));
        byte[] pdf = service.gerarPdf(TENANT, "LEG-1");
        String text = new String(pdf, StandardCharsets.US_ASCII);
        assertThat(text).startsWith("%PDF-1.4");
        assertThat(text).contains("%%EOF");
        assertThat(text).contains(LegacyHistoryPdfService.HISTORICO_IMPORTADO_NOTICE);
        assertThat(text).contains("LEGACY_IMPORT");
        assertThat(text).contains("2025-03").contains("150,00").contains("10/04/2025");
        assertThat(text).contains("2025-04").contains("200,50");
        assertThat(text).contains("Total do historico: R$ 350,50");
        assertThat(text.indexOf("2025-03")).isLessThan(text.indexOf("2025-04"));
    }

    @Test
    @DisplayName("unidade sem histórico importado é erro explícito, nunca um PDF vazio")
    void semHistoricoErroExplicito() {
        assertThatThrownBy(() -> serviceCom(List.of()).gerarPdf(TENANT, "LEG-9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem contas no historico importado");
    }

    @Test
    @DisplayName("formatação de centavos calculada à mão")
    void formatacaoDeCentavos() {
        assertThat(LegacyHistoryPdfService.formatCents(123456L)).isEqualTo("1.234,56");
        assertThat(LegacyHistoryPdfService.formatCents(5L)).isEqualTo("0,05");
        assertThat(LegacyHistoryPdfService.formatCents(35050L)).isEqualTo("350,50");
        assertThat(LegacyHistoryPdfService.formatCents(0L)).isEqualTo("0,00");
    }

    @Test
    @DisplayName("correção A3: histórico de 130 meses é PAGINADO — todas as contas, o total e a indicação aparecem")
    void pdfPaginadoHistoricoLongo() {
        // 130 contas mensais a partir de 2025-03 seria pouco; usa-se
        // 2015-01 + i meses (última: 2025-10), valores 100,00 + i centavos.
        // Total calculado à mão: 130 × 10000 + (0+1+...+129) = 1.300.000
        // + 8.385 = 1.308.385 centavos = "13.083,85".
        java.util.List<LegacyHistoryDataPort.ImportedBill> bills = new java.util.ArrayList<>();
        for (int i = 0; i < 130; i++) {
            YearMonth competence = YearMonth.of(2015, 1).plusMonths(i);
            bills.add(new LegacyHistoryDataPort.ImportedBill("LEG-1", "910001", competence,
                    new BigDecimal("10.000"), 10000L + i, competence.atDay(10).plusMonths(1)));
        }
        byte[] pdf = serviceCom(bills).gerarPdf(TENANT, "LEG-1");
        String text = new String(pdf, StandardCharsets.US_ASCII);

        // 130 contas ÷ 42 por página = 4 páginas.
        assertThat(text).contains("/Count 4");
        // NENHUMA conta cai fora: toda competência está no PDF.
        for (int i = 0; i < 130; i++) {
            assertThat(text).contains(YearMonth.of(2015, 1).plusMonths(i).toString());
        }
        assertThat(text).contains("100,00").contains("101,29");
        assertThat(text).contains("Total do historico: R$ 13.083,85");
        assertThat(text).contains("Contas importadas: 130");
        // A indicação de histórico importado aparece em TODA página
        // (cabeçalho repetido) e no rodapé: pelo menos 4 ocorrências.
        int ocorrencias = text.split(
                java.util.regex.Pattern.quote(LegacyHistoryPdfService.HISTORICO_IMPORTADO_NOTICE), -1)
                .length - 1;
        assertThat(ocorrencias).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("correção A9: valor nulo no histórico é exibido como '-' e fica fora do total, com nota no rodapé")
    void valorNuloNaoViraZero() {
        LegacyHistoryPdfService service = serviceCom(List.of(
                new LegacyHistoryDataPort.ImportedBill("LEG-1", "910001", YearMonth.of(2025, 3),
                        new BigDecimal("12.000"), 15000L, LocalDate.of(2025, 4, 10)),
                new LegacyHistoryDataPort.ImportedBill("LEG-1", "910001", YearMonth.of(2025, 4),
                        new BigDecimal("12.000"), null, LocalDate.of(2025, 5, 10))));
        String text = new String(service.gerarPdf(TENANT, "LEG-1"), StandardCharsets.US_ASCII);
        assertThat(text).contains("2025-04  12.000  -  10/05/2025");
        assertThat(text).contains("Total do historico: R$ 150,00");
        assertThat(text).contains("Contas sem valor no historico importado: 1");
    }

    @Test
    @DisplayName("correção A9: contas da unidade com matrículas divergentes são erro explícito, nunca a matrícula do primeiro registro")
    void matriculasDivergentesErroExplicito() {
        LegacyHistoryPdfService service = serviceCom(List.of(
                new LegacyHistoryDataPort.ImportedBill("LEG-1", "910001", YearMonth.of(2025, 3),
                        new BigDecimal("12.000"), 15000L, LocalDate.of(2025, 4, 10)),
                new LegacyHistoryDataPort.ImportedBill("LEG-1", "910002", YearMonth.of(2025, 4),
                        new BigDecimal("12.000"), 15000L, LocalDate.of(2025, 5, 10))));
        assertThatThrownBy(() -> service.gerarPdf(TENANT, "LEG-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matriculas divergentes");
    }
}
