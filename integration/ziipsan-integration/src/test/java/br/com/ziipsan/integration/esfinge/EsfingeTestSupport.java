package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.integration.esfinge.EsfingeLayout.ConsistencyRule;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutField;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutTable;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.RuleKind;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.AccountingSnapshot;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.ActiveDebtSnapshot;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.DatasetRecord;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.RemittanceSourceData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Apoio dos testes da ED-33. O leiaute aqui e FIXTURE DE TESTE (tabelas
 * CABECALHO/LINHA_TRIBUTARIA inventadas para exercitar o motor) — NAO e o
 * leiaute do TCE/SC, que segue pendente (PENDENTE_LEIAUTE_OFICIAL_TCE_SC).
 */
final class EsfingeTestSupport {

    static final YearMonth COMPETENCE = YearMonth.of(2026, 10);
    static final String LAYOUT_VERSION = "FIXTURE-1.0";

    private EsfingeTestSupport() {}

    static EsfingeLayout fixtureLayout(boolean official) {
        var cabecalho = new LayoutTable("CABECALHO", "competencia", List.of(
                LayoutField.text("competencia", true, 7),
                LayoutField.decimal("totalLinhas", true, 2, true),
                LayoutField.date("dataGeracao", true)));
        var linha = new LayoutTable("LINHA_TRIBUTARIA", "linhaId", List.of(
                LayoutField.text("linhaId", true, 20),
                LayoutField.text("cabecalhoCompetencia", true, 7),
                LayoutField.decimal("valor", true, 2, true),
                LayoutField.date("dataFato", true)));
        var rules = List.of(
                new ConsistencyRule(RuleKind.SUM_EQUALS, "LINHA_TRIBUTARIA", "valor",
                        "CABECALHO", "totalLinhas", "soma das linhas iguala o total do cabecalho"),
                new ConsistencyRule(RuleKind.DATE_WITHIN_COMPETENCE, "LINHA_TRIBUTARIA", "dataFato",
                        null, null, "data do fato dentro da competencia"),
                new ConsistencyRule(RuleKind.REFERENCE_EXISTS, "LINHA_TRIBUTARIA", "cabecalhoCompetencia",
                        "CABECALHO", "competencia", "linha referencia o cabecalho da competencia"));
        return new EsfingeLayout(LAYOUT_VERSION, null, official,
                LocalDate.of(2026, 1, 1), null, List.of(cabecalho, linha), rules);
    }

    static EsfingeParameters params() {
        return EsfingeParameters.fromValues(Map.of(
                EsfingeParameters.LAYOUT_VERSION, "\"" + LAYOUT_VERSION + "\""));
    }

    static RemittanceSourceData validData() {
        return data(
                new DatasetRecord("CABECALHO",
                        Map.of("competencia", "2026-10", "totalLinhas", "300.00", "dataGeracao", "2026-11-05"),
                        "FIXTURE:cabecalho"),
                List.of(
                        new DatasetRecord("LINHA_TRIBUTARIA",
                                Map.of("linhaId", "L1", "cabecalhoCompetencia", "2026-10", "valor", "100.00", "dataFato", "2026-10-03"),
                                "ED31:inscricao-1"),
                        new DatasetRecord("LINHA_TRIBUTARIA",
                                Map.of("linhaId", "L2", "cabecalhoCompetencia", "2026-10", "valor", "200.00", "dataFato", "2026-10-20"),
                                "ED31:inscricao-2")),
                new ActiveDebtSnapshot(new BigDecimal("300.00"), new BigDecimal("0.00"),
                        new BigDecimal("300.00"), 2, true),
                new AccountingSnapshot(new BigDecimal("1000.00"), new BigDecimal("1000.00"), 4));
    }

    static RemittanceSourceData data(DatasetRecord header, List<DatasetRecord> lines,
                                     ActiveDebtSnapshot da, AccountingSnapshot acc) {
        var all = new java.util.ArrayList<DatasetRecord>();
        if (header != null) all.add(header);
        all.addAll(lines);
        return new RemittanceSourceData(all, da, acc);
    }

    static ActiveDebtSnapshot frozenDa() {
        return new ActiveDebtSnapshot(new BigDecimal("300.00"), BigDecimal.ZERO,
                new BigDecimal("300.00"), 2, true);
    }

    static AccountingSnapshot balancedAcc() {
        return new AccountingSnapshot(new BigDecimal("1000.00"), new BigDecimal("1000.00"), 4);
    }
}
