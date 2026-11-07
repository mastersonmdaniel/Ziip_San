package br.com.ziipsan.operations.metering.exchange;

import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O leiaute do fornecedor do piloto (P2.7): ida e volta sao o mesmo arquivo --
 * o que a exportacao escreve, a importacao le de volta, campo a campo.
 */
class VendorReadingLayoutTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INSTALLATION = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final YearMonth REFERENCE = YearMonth.of(2026, 11);

    private static VendorReadingLayout.VendorLine taskLine() {
        return new VendorReadingLayout.VendorLine(1, "LEITURASUL", "R-01", REFERENCE, UNIT, INSTALLATION,
                MeasurementKind.WATER, LocalDate.of(2026, 11, 5), 1200, null, null, null, null);
    }

    @Test
    @DisplayName("a ida sai com o cabecalho do leiaute e uma linha por tarefa, na ordem da rota")
    void exportWritesTheLayoutHeaderAndOneLinePerTask() {
        String file = VendorReadingLayout.formatTasks(List.of(taskLine()));

        String[] lines = file.split("\n");
        assertThat(lines[0]).isEqualTo(VendorReadingLayout.HEADER);
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).startsWith("1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION + ";WATER;2026-11-05;1200;");
    }

    @Test
    @DisplayName("a volta e lida no mesmo leiaute: a linha da ida preenchida volta com os valores da leitura")
    void returnFileParsesInTheSameLayout() {
        String returned = VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;1234;;\n";

        List<VendorReadingLayout.ParsedLine> parsed = VendorReadingLayout.parse(returned);

        assertThat(parsed).hasSize(1);
        VendorReadingLayout.VendorLine line = parsed.get(0).line();
        assertThat(line.unitId()).isEqualTo(UNIT);
        assertThat(line.kind()).isEqualTo(MeasurementKind.WATER);
        assertThat(line.reference()).isEqualTo(REFERENCE);
        assertThat(line.readOn()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(line.reading()).isEqualTo(1234);
        assertThat(line.anomalyCode()).isNull();
    }

    @Test
    @DisplayName("linha sem leitura e com anormalidade e valida no leiaute (e o caso NOT_READ da volta)")
    void lineWithAnomalyAndNoReadingParses() {
        String returned = VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;;IMPEDIMENTO;\n";

        VendorReadingLayout.VendorLine line = VendorReadingLayout.parse(returned).get(0).line();

        assertThat(line.reading()).isNull();
        assertThat(line.anomalyCode()).isEqualTo("IMPEDIMENTO");
    }

    @Test
    @DisplayName("cabecalho de outro leiaute e recusado, nunca interpretado por aproximacao")
    void foreignHeaderIsRefused() {
        assertThatThrownBy(() -> VendorReadingLayout.parse("UNIDADE;LEITURA\n" + UNIT + ";1234\n"))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining(VendorReadingLayout.LAYOUT_CODE);
    }

    @Test
    @DisplayName("linha com valor ilegivel vira linha de erro nomeada, sem derrubar o arquivo")
    void unreadableLineBecomesANamedErrorLine() {
        String returned = VendorReadingLayout.HEADER + "\n"
                + "1;LEITURASUL;R-01;2026-11;" + UNIT + ";" + INSTALLATION
                + ";WATER;;;2026-11-05;ABC;;\n";

        List<VendorReadingLayout.ParsedLine> parsed = VendorReadingLayout.parse(returned);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).isError()).isTrue();
        assertThat(parsed.get(0).error()).contains("LEITURA");
        assertThat(parsed.get(0).lineNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("o que a ida escreve, a volta le de volta: round-trip direto parse(formatTasks(linha)) sobre a mesma linha")
    void formatThenParseRoundTripsTheSameLine() {
        VendorReadingLayout.VendorLine full = new VendorReadingLayout.VendorLine(
                3, "LEITURASUL", "R-01", REFERENCE, UNIT, INSTALLATION, MeasurementKind.WELL,
                LocalDate.of(2026, 11, 6), 900, LocalDate.of(2026, 11, 6), 945, "IMPEDIMENTO", 45);

        List<VendorReadingLayout.ParsedLine> parsed =
                VendorReadingLayout.parse(VendorReadingLayout.formatTasks(List.of(full)));

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).isError()).isFalse();
        assertThat(parsed.get(0).line()).isEqualTo(full);
    }

    @Test
    @DisplayName("a chave do registro e estavel e muda com qualquer campo da identidade")
    void recordKeyIsStableAndIdentityBound() {
        String key = VendorReadingLayout.recordKey("LEITURASUL", "R-01", REFERENCE, UNIT, MeasurementKind.WATER);

        assertThat(key).isEqualTo(VendorReadingLayout.recordKey("LEITURASUL", "R-01", REFERENCE, UNIT,
                MeasurementKind.WATER));
        assertThat(key).hasSizeLessThanOrEqualTo(120);
        assertThat(VendorReadingLayout.recordKey("LEITURASUL", "R-01", REFERENCE.plusMonths(1), UNIT,
                MeasurementKind.WATER)).isNotEqualTo(key);
        assertThat(VendorReadingLayout.recordKey("LEITURASUL", "R-02", REFERENCE, UNIT,
                MeasurementKind.WATER)).isNotEqualTo(key);
        assertThat(VendorReadingLayout.recordKey("OUTRASUL", "R-01", REFERENCE, UNIT,
                MeasurementKind.WATER)).isNotEqualTo(key);
        assertThat(VendorReadingLayout.recordKey("LEITURASUL", "R-01", REFERENCE, INSTALLATION,
                MeasurementKind.WATER)).isNotEqualTo(key);
        assertThat(VendorReadingLayout.recordKey("LEITURASUL", "R-01", REFERENCE, UNIT,
                MeasurementKind.WELL)).isNotEqualTo(key);
    }

    @Test
    @DisplayName("a chave cabe nos 120 mesmo com os codigos NOS LIMITES das colunas (fornecedor 30, rota 40, unidade 36)")
    void recordKeyFitsTheLimitWithCodesAtTheirColumnLimits() {
        String key = VendorReadingLayout.recordKey("V".repeat(30), "R".repeat(40), REFERENCE, UNIT,
                MeasurementKind.WATER);

        // Pior caso: 30 (fornecedor) + 40 (rota) + 6 (yyyyMM) + 32 (UUID sem hifens)
        // + 2 (tipo) + 4 separadores = 114 -- dentro dos 120 de
        // reading_import_records.record_key (V5_3) e de meter_readings.idempotency_key (V5_4).
        assertThat(key).hasSize(114);
        assertThat(key).hasSizeLessThanOrEqualTo(120);
        assertThat(VendorReadingLayout.recordKey("V".repeat(30), "R".repeat(40), REFERENCE, UNIT,
                MeasurementKind.WELL)).hasSize(114);
    }

    @Test
    @DisplayName("o hash do arquivo identifica o conteudo exato: mesmo conteudo, mesmo hash")
    void fileHashIdentifiesTheExactContent() {
        String file = VendorReadingLayout.formatTasks(List.of(taskLine()));

        assertThat(VendorReadingLayout.sha256Hex(file)).isEqualTo(VendorReadingLayout.sha256Hex(file));
        assertThat(VendorReadingLayout.sha256Hex(file)).hasSize(64);
        assertThat(VendorReadingLayout.sha256Hex(file + " ")).isNotEqualTo(VendorReadingLayout.sha256Hex(file));
    }
}
