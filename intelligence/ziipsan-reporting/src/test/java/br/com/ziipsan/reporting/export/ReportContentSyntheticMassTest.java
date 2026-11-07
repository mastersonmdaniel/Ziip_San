package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de conteudo de CADA relatorio sobre massa sintetica: as linhas
 * abaixo sao o que a view devolveria para a massa, e os totais esperados
 * foram calculados a mao a partir delas (soma das duas linhas). A prova
 * contra o banco real (view x massa em tabelas) e o
 * ReportViewsDatabaseTest, no padrao de integracao do projeto.
 */
class ReportContentSyntheticMassTest {

    private final ReportRegistry registry = new ReportRegistry();

    private void check(String code, List<Map<String, Object>> rows, String column, String expected) {
        var definition = registry.find(code).orElseThrow();
        var totals = ReportSummaryCalculator.summarize(definition, rows);
        assertThat(totals.get("__rows")).as(code + " linhas").isEqualByComparingTo("2");
        assertThat(totals.get(column)).as(code + " total de " + column)
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    private static Map<String, Object> row(Object... kv) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void conteudoRPT001() {
        var rows = List.of(row("pagamentos", 2L, "valor_liquido", new java.math.BigDecimal("345.50")), row("pagamentos", 1L, "valor_liquido", new java.math.BigDecimal("10.00")));
        check("RPT-001", rows, "valor_liquido", "355.50");
        check2("RPT-001", rows, "pagamentos", "3");
    }

    @Test
    void conteudoRPT002() {
        var rows = List.of(row("valor", new java.math.BigDecimal("75.25")), row("valor", new java.math.BigDecimal("24.75")));
        check("RPT-002", rows, "valor", "100.00");
    }

    @Test
    void conteudoRPT003() {
        var rows = List.of(row("valor", new java.math.BigDecimal("100.00")), row("valor", new java.math.BigDecimal("100.00")));
        check("RPT-003", rows, "valor", "200.00");
    }

    @Test
    void conteudoRPT004() {
        var rows = List.of(row("pagamentos", 3L, "valor_liquido", new java.math.BigDecimal("500.00")), row("pagamentos", 2L, "valor_liquido", new java.math.BigDecimal("250.00")));
        check("RPT-004", rows, "valor_liquido", "750.00");
        check2("RPT-004", rows, "pagamentos", "5");
    }

    @Test
    void conteudoRPT005() {
        var rows = List.of(row("linhas", 10L, "valor_total", new java.math.BigDecimal("1000.00")), row("linhas", 5L, "valor_total", new java.math.BigDecimal("500.00")));
        check("RPT-005", rows, "valor_total", "1500.00");
        check2("RPT-005", rows, "linhas", "15");
    }

    @Test
    void conteudoRPT006() {
        var rows = List.of(row("contas", 10L, "valor_total", new java.math.BigDecimal("3000.00")), row("contas", 5L, "valor_total", new java.math.BigDecimal("1500.00")));
        check("RPT-006", rows, "valor_total", "4500.00");
        check2("RPT-006", rows, "contas", "15");
    }

    @Test
    void conteudoRPT007() {
        var rows = List.of(row("contas", 4L, "consumo_m3", 40L, "valor_liquido", new java.math.BigDecimal("800.00")), row("contas", 6L, "consumo_m3", 90L, "valor_liquido", new java.math.BigDecimal("1200.00")));
        check("RPT-007", rows, "valor_liquido", "2000.00");
        check2("RPT-007", rows, "consumo_m3", "130");
    }

    @Test
    void conteudoRPT008() {
        var rows = List.of(row("atividades", 8L, "realizadas", 8L, "pendentes", 0L), row("atividades", 8L, "realizadas", 5L, "pendentes", 3L));
        check("RPT-008", rows, "realizadas", "13");
        check2("RPT-008", rows, "pendentes", "3");
    }

    @Test
    void conteudoRPT009() {
        var rows = List.of(row("contas", 100L, "valor_total", new java.math.BigDecimal("20000.00")), row("contas", 50L, "valor_total", new java.math.BigDecimal("9000.00")));
        check("RPT-009", rows, "valor_total", "29000.00");
        check2("RPT-009", rows, "contas", "150");
    }

    @Test
    void conteudoRPT010() {
        var rows = List.of(row("valor", new java.math.BigDecimal("300.00")), row("valor", new java.math.BigDecimal("150.00")));
        check("RPT-010", rows, "valor", "450.00");
    }

    @Test
    void conteudoRPT011() {
        var rows = List.of(row("retificacoes", 2L, "valor", new java.math.BigDecimal("500.00")), row("retificacoes", 1L, "valor", new java.math.BigDecimal("200.00")));
        check("RPT-011", rows, "valor", "700.00");
        check2("RPT-011", rows, "retificacoes", "3");
    }

    @Test
    void conteudoRPT012() {
        var rows = List.of(row("consumo", 12L), row("consumo", 8L));
        check("RPT-012", rows, "consumo", "20");
    }

    @Test
    void conteudoRPT013() {
        var rows = List.of(row("leituras", 7L), row("leituras", 3L));
        check("RPT-013", rows, "leituras", "10");
    }

    @Test
    void conteudoRPT014() {
        var rows = List.of(row("ligacoes", 40L, "consumo_total", 320L), row("ligacoes", 10L, "consumo_total", 130L));
        check("RPT-014", rows, "consumo_total", "450");
        check2("RPT-014", rows, "ligacoes", "50");
    }

    @Test
    void conteudoRPT015() {
        var rows = List.of(row("ligacoes", 900L, "economias", 900L), row("ligacoes", 100L, "economias", 120L));
        check("RPT-015", rows, "ligacoes", "1000");
        check2("RPT-015", rows, "economias", "1020");
    }

    @Test
    void conteudoRPT016() {
        var rows = List.of(row("ligacoes", 300L, "economias", 300L), row("ligacoes", 50L, "economias", 55L));
        check("RPT-016", rows, "ligacoes", "350");
        check2("RPT-016", rows, "economias", "355");
    }

    @Test
    void conteudoRPT017() {
        var rows = List.of(row("contas_abertas", 20L, "valor_a_receber", new java.math.BigDecimal("6000.00")), row("contas_abertas", 5L, "valor_a_receber", new java.math.BigDecimal("1000.00")));
        check("RPT-017", rows, "valor_a_receber", "7000.00");
        check2("RPT-017", rows, "contas_abertas", "25");
    }

    @Test
    void conteudoRPT018() {
        var rows = List.of(row("pendencias", 4L), row("pendencias", 6L));
        check("RPT-018", rows, "pendencias", "10");
    }

    @Test
    void conteudoRPT019() {
        var rows = List.of(row("contas_vencidas", 3L, "valor_devido", new java.math.BigDecimal("900.00")), row("contas_vencidas", 1L, "valor_devido", new java.math.BigDecimal("100.00")));
        check("RPT-019", rows, "valor_devido", "1000.00");
        check2("RPT-019", rows, "contas_vencidas", "4");
    }

    @Test
    void conteudoRPT020() {
        var rows = List.of(row("avisos", 30L, "valor_total", new java.math.BigDecimal("9000.00")), row("avisos", 10L, "valor_total", new java.math.BigDecimal("2500.00")));
        check("RPT-020", rows, "valor_total", "11500.00");
        check2("RPT-020", rows, "avisos", "40");
    }

    @Test
    void conteudoRPT021() {
        var rows = List.of(row("unidades", 12L), row("unidades", 8L));
        check("RPT-021", rows, "unidades", "20");
    }

    @Test
    void conteudoRPT022() {
        var rows = List.of(row("horas_ate_encerrar", new java.math.BigDecimal("10.5")), row("horas_ate_encerrar", new java.math.BigDecimal("5.5")));
        check("RPT-022", rows, "horas_ate_encerrar", "16.0");
    }

    @Test
    void conteudoRPT023() {
        var rows = List.of(row("inscricoes", 40L, "economias_sociais", 40L), row("inscricoes", 10L, "economias_sociais", 12L));
        check("RPT-023", rows, "inscricoes", "50");
        check2("RPT-023", rows, "economias_sociais", "52");
    }

    @Test
    void conteudoRPT024() {
        var rows = List.of(row("inscricoes", 7L), row("inscricoes", 3L));
        check("RPT-024", rows, "inscricoes", "10");
    }

    @Test
    void conteudoRPT025() {
        var rows = List.of(row("documentos", 80L), row("documentos", 20L));
        check("RPT-025", rows, "documentos", "100");
    }

    @Test
    void conteudoRPT026() {
        var rows = List.of(row("devolucoes", 2L, "valor_total", new java.math.BigDecimal("400.00")), row("devolucoes", 1L, "valor_total", new java.math.BigDecimal("100.00")));
        check("RPT-026", rows, "valor_total", "500.00");
        check2("RPT-026", rows, "devolucoes", "3");
    }

    @Test
    void conteudoRPT027() {
        var rows = List.of(row("encerrados", 10L, "nao_atendidos_no_prazo", 3L, "percentual_nao_atendido", new java.math.BigDecimal("30.00")), row("encerrados", 10L, "nao_atendidos_no_prazo", 1L, "percentual_nao_atendido", new java.math.BigDecimal("10.00")));
        check("RPT-027", rows, "encerrados", "20");
        check2("RPT-027", rows, "nao_atendidos_no_prazo", "4");
    }

    private void check2(String code, List<Map<String, Object>> rows, String column, String expected) {
        var definition = registry.find(code).orElseThrow();
        var totals = ReportSummaryCalculator.summarize(definition, rows);
        assertThat(totals.get(column)).as(code + " total de " + column)
                .isEqualByComparingTo(new BigDecimal(expected));
    }
}
