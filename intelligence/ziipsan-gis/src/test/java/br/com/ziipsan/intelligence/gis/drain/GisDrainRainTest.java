package br.com.ziipsan.intelligence.gis.drain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chuva: importação do download mensal com fonte citada e acumulado do período. */
class GisDrainRainTest {

    private static final String CSV = """
            relatorio mensal - estacao sintetica
            datahora;chuva
            2026-10-01 00:00:00;1,5
            2026-10-01 01:00:00;2,5
            2026-10-02 00:00:00;10,0
            """;

    @Test
    void importaCsvComFonteCitadaETotaisDiarios() {
        GisDrainRain.RainImportResult r = GisDrainRain.importCsv(
                "CEMADEN_IMPORT", "download mensal Cemaden 10/2026 (arquivo sintetico)", CSV);
        assertThat(r.readings()).hasSize(3);
        assertThat(r.totalMm()).isEqualTo(14.0);
        assertThat(GisDrainRain.dailyTotals(r.readings()))
                .containsEntry(LocalDate.of(2026, 10, 1), 4.0)
                .containsEntry(LocalDate.of(2026, 10, 2), 10.0);
    }

    @Test
    void semFonteCitadaNaoImporta() {
        assertThatThrownBy(() -> GisDrainRain.importCsv("CEMADEN_IMPORT", " ", CSV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fonte citada");
        assertThatThrownBy(() -> GisDrainRain.importCsv("API_CEMADEN", "x", CSV))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acumuladoDoPeriodoELigacaoDoAlagamento() {
        List<GisDrainRain.RainReading> readings = GisDrainRain.importCsv(
                "OWN_GAUGE", "pluviometro proprio - sitio sintetico", CSV).readings();
        // Janela do alagamento: dia 01 inteiro -> 4,0 mm conhecidos.
        assertThat(GisDrainRain.accumulatedMm(readings,
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 23, 59))).isEqualTo(4.0);
        // Sem leitura na janela: desconhecido (null), nunca zero.
        assertThat(GisDrainRain.accumulatedMm(readings,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 23, 59))).isNull();
    }

    @Test
    void linhaInvalidaAbortaImportacaoInteira() {
        String bad = "datahora;chuva\n2026-10-01 00:00:00;abc\n";
        assertThatThrownBy(() -> GisDrainRain.importCsv("OWN_GAUGE", "fonte", bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linha 2");
    }
}
