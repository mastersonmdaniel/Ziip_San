package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutField;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutTable;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.AccountingSnapshot;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.ActiveDebtSnapshot;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.DatasetRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsfingeLayoutAndValidatorTest {

    @Test
    void leiauteRejeitaTabelaDuplicadaECampoDuplicado() {
        var t1 = new LayoutTable("T", null, List.of(LayoutField.text("a", true, 5)));
        assertThatThrownBy(() -> new EsfingeLayout("V1", null, false, LocalDate.of(2026, 1, 1), null,
                List.of(t1, t1), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicada");
        assertThatThrownBy(() -> new LayoutTable("T", null,
                List.of(LayoutField.text("a", true, 5), LayoutField.text("a", true, 5))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicado");
    }

    @Test
    void leiauteRejeitaRegraParaCampoInexistente() {
        var t = new LayoutTable("T", null, List.of(LayoutField.text("a", true, 5)));
        assertThatThrownBy(() -> new EsfingeLayout("V1", null, false, LocalDate.of(2026, 1, 1), null,
                List.of(t), List.of(new EsfingeLayout.ConsistencyRule(
                        EsfingeLayout.RuleKind.SUM_EQUALS, "T", "zzz", "T", "a", "regra de teste"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inexistente");
    }

    @Test
    void dadosValidosPassamSemErros() {
        var result = EsfingeValidator.validate(EsfingeTestSupport.fixtureLayout(false),
                EsfingeTestSupport.COMPETENCE, EsfingeTestSupport.validData());
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void campoObrigatorioAusenteEEscalaErradaSaoErrosNomeados() {
        var data = EsfingeTestSupport.data(
                new DatasetRecord("CABECALHO",
                        Map.of("competencia", "2026-10", "totalLinhas", "300.00", "dataGeracao", "2026-11-05"), "F:h"),
                List.of(new DatasetRecord("LINHA_TRIBUTARIA",
                        Map.of("linhaId", "L1", "cabecalhoCompetencia", "2026-10", "valor", "100.001", "dataFato", "2026-10-03"), "ED31:1"),
                        new DatasetRecord("LINHA_TRIBUTARIA",
                                Map.of("cabecalhoCompetencia", "2026-10", "valor", "200.00", "dataFato", "2026-10-20"), "ED31:2")),
                EsfingeTestSupport.frozenDa(), EsfingeTestSupport.balancedAcc());
        var result = EsfingeValidator.validate(EsfingeTestSupport.fixtureLayout(false),
                EsfingeTestSupport.COMPETENCE, data);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.tableCode()).isEqualTo("LINHA_TRIBUTARIA");
            assertThat(e.fieldCode()).isEqualTo("linhaId");
            assertThat(e.message()).contains("obrigatorio");
        });
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("escala"));
    }

    @Test
    void somaDivergenteChaveDuplicadaEDataForaDaCompetencia() {
        var data = EsfingeTestSupport.data(
                new DatasetRecord("CABECALHO",
                        Map.of("competencia", "2026-10", "totalLinhas", "999.00", "dataGeracao", "2026-11-05"), "F:h"),
                List.of(new DatasetRecord("LINHA_TRIBUTARIA",
                                Map.of("linhaId", "L1", "cabecalhoCompetencia", "2026-10", "valor", "100.00", "dataFato", "2026-09-03"), "ED31:1"),
                        new DatasetRecord("LINHA_TRIBUTARIA",
                                Map.of("linhaId", "L1", "cabecalhoCompetencia", "2026-10", "valor", "200.00", "dataFato", "2026-10-20"), "ED31:2")),
                EsfingeTestSupport.frozenDa(), EsfingeTestSupport.balancedAcc());
        var result = EsfingeValidator.validate(EsfingeTestSupport.fixtureLayout(false),
                EsfingeTestSupport.COMPETENCE, data);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("soma das linhas"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("chave duplicada"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("fora da competencia"));
    }

    @Test
    void referenciaInexistenteEValorNegativoSaoRecusados() {
        var data = EsfingeTestSupport.data(
                new DatasetRecord("CABECALHO",
                        Map.of("competencia", "2026-10", "totalLinhas", "300.00", "dataGeracao", "2026-11-05"), "F:h"),
                List.of(new DatasetRecord("LINHA_TRIBUTARIA",
                        Map.of("linhaId", "L1", "cabecalhoCompetencia", "2026-11", "valor", "-5.00", "dataFato", "2026-10-03"), "ED31:1")),
                EsfingeTestSupport.frozenDa(), EsfingeTestSupport.balancedAcc());
        var result = EsfingeValidator.validate(EsfingeTestSupport.fixtureLayout(false),
                EsfingeTestSupport.COMPETENCE, data);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("nao existe em CABECALHO"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("negativo"));
    }

    @Test
    void balanceteDesequilibradoEMesDeDividaAtivaAbertoBloqueiam() {
        var base = EsfingeTestSupport.validData();
        var data = new br.com.ziipsan.integration.esfinge.EsfingeSourceData.RemittanceSourceData(
                base.records(),
                new ActiveDebtSnapshot(new BigDecimal("300.00"), BigDecimal.ZERO, new BigDecimal("300.00"), 2, false),
                new AccountingSnapshot(new BigDecimal("1000.00"), new BigDecimal("900.00"), 4));
        var result = EsfingeValidator.validate(EsfingeTestSupport.fixtureLayout(false),
                EsfingeTestSupport.COMPETENCE, data);
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("desequilibrado"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("nao congelado"));
    }

    @Test
    void competenciaForaDaVigenciaDoLeiauteEErro() {
        var result = EsfingeValidator.validate(EsfingeTestSupport.fixtureLayout(false),
                java.time.YearMonth.of(2025, 12), EsfingeTestSupport.validData());
        assertThat(result.errors()).anySatisfy(e -> assertThat(e.message()).contains("fora da vigencia"));
    }
}
