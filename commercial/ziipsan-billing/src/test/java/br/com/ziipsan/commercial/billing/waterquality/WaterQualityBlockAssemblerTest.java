package br.com.ziipsan.commercial.billing.waterquality;

import br.com.ziipsan.commercial.billing.parameters.StatementParameters.WaterQualitySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloco mensal de qualidade da agua (P2.24, Decreto 5.440/2005): a fonte e
 * a do parametro bill.water_quality_source; sem resultado da fonte no
 * mes/sistema, nao ha bloco — e nenhuma outra fonte ou outro mes e
 * aproveitado em silencio. Chaves de parametro sinteticas: o plano nao
 * fixa catalogo de parametros, e nenhum nome real e presumido.
 */
class WaterQualityBlockAssemblerTest {

    private static final LocalDate MAY_2026 = LocalDate.of(2026, 5, 1);
    private static final String SYSTEM = "ETA_CENTRAL";

    private static WaterQualityResult result(String key, WaterQualitySource source) {
        return new WaterQualityResult(MAY_2026, SYSTEM, key, "Nome de " + key, null,
                "resultado de " + key, null, source);
    }

    @Test
    @DisplayName("fonte planilha: o bloco sai dos resultados da planilha, ordenado pela chave do parametro")
    void spreadsheetSourceBuildsSortedBlock() {
        List<WaterQualityResult> sheet = List.of(
                result("param_b", WaterQualitySource.LAB_SPREADSHEET),
                result("param_a", WaterQualitySource.LAB_SPREADSHEET));
        Optional<WaterQualityBlock> block = WaterQualityBlockAssembler.assemble(
                WaterQualitySource.LAB_SPREADSHEET, MAY_2026, SYSTEM, sheet, List.of());
        assertThat(block).isPresent();
        assertThat(block.get().results()).extracting(WaterQualityResult::parameterKey)
                .containsExactly("param_a", "param_b");
        assertThat(WaterQualityBlockAssembler.available(
                WaterQualitySource.LAB_SPREADSHEET, MAY_2026, SYSTEM, sheet, List.of())).isTrue();
    }

    @Test
    @DisplayName("fonte modulo (ED-50): valem os resultados do modulo, e os da planilha sao ignorados")
    void qualityModuleSourceIgnoresSpreadsheet() {
        List<WaterQualityResult> sheet = List.of(result("param_sheet", WaterQualitySource.LAB_SPREADSHEET));
        List<WaterQualityResult> module = List.of(result("param_module", WaterQualitySource.QUALITY_MODULE));
        Optional<WaterQualityBlock> block = WaterQualityBlockAssembler.assemble(
                WaterQualitySource.QUALITY_MODULE, MAY_2026, SYSTEM, sheet, module);
        assertThat(block).isPresent();
        assertThat(block.get().results()).extracting(WaterQualityResult::parameterKey)
                .containsExactly("param_module");
    }

    @Test
    @DisplayName("fonte modulo sem resultado do modulo: sem bloco, sem cair para a planilha em silencio")
    void qualityModuleWithoutModuleDataHasNoBlock() {
        List<WaterQualityResult> sheet = List.of(result("param_sheet", WaterQualitySource.LAB_SPREADSHEET));
        assertThat(WaterQualityBlockAssembler.assemble(
                WaterQualitySource.QUALITY_MODULE, MAY_2026, SYSTEM, sheet, List.of())).isEmpty();
        assertThat(WaterQualityBlockAssembler.available(
                WaterQualitySource.QUALITY_MODULE, MAY_2026, SYSTEM, sheet, List.of())).isFalse();
    }

    @Test
    @DisplayName("planilha do mes nao carregada: sem bloco (a mensagem legal da P2.32 nao dispara)")
    void missingSpreadsheetMonthHasNoBlock() {
        assertThat(WaterQualityBlockAssembler.assemble(
                WaterQualitySource.LAB_SPREADSHEET, MAY_2026, SYSTEM, List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("resultado de outro mes ou de outro sistema na lista escolhida e erro explicito")
    void foreignMonthOrSystemIsExplicitError() {
        WaterQualityResult otherMonth = new WaterQualityResult(LocalDate.of(2026, 4, 1), SYSTEM,
                "param_a", "Nome", null, "resultado", null, WaterQualitySource.LAB_SPREADSHEET);
        assertThatThrownBy(() -> WaterQualityBlockAssembler.assemble(
                WaterQualitySource.LAB_SPREADSHEET, MAY_2026, SYSTEM, List.of(otherMonth), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-04-01");

        WaterQualityResult otherSystem = new WaterQualityResult(MAY_2026, "ETA_OUTRA",
                "param_a", "Nome", null, "resultado", null, WaterQualitySource.LAB_SPREADSHEET);
        assertThatThrownBy(() -> WaterQualityBlockAssembler.assemble(
                WaterQualitySource.LAB_SPREADSHEET, MAY_2026, SYSTEM, List.of(otherSystem), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ETA_OUTRA");
    }

    @Test
    @DisplayName("resultado marcado de outra fonte na lista escolhida e erro explicito")
    void mislabeledSourceIsExplicitError() {
        WaterQualityResult mislabeled = result("param_a", WaterQualitySource.QUALITY_MODULE);
        assertThatThrownBy(() -> WaterQualityBlockAssembler.assemble(
                WaterQualitySource.LAB_SPREADSHEET, MAY_2026, SYSTEM, List.of(mislabeled), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QUALITY_MODULE");
    }
}
