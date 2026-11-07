package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O leiaute de arquivo AMR da ED-17: cabeçalho estrito, linha inválida não derruba o arquivo. */
class AmrFileLayoutTest {

    @Test
    @DisplayName("linhas válidas são lidas, inclusive leitura vazia (o caso da estimativa)")
    void parsesValidLines() {
        String content = AmrFileLayout.HEADER + "\n"
                + "AMR-1;PTO-001;VOL;2026-11-10T08:00:00-03:00;1050;MEASURED\n"
                + "AMR-2;PTO-001;VOL;2026-11-11T08:00:00-03:00;;MEASURED\n";

        List<AmrFileLayout.ParsedLine> lines = AmrFileLayout.parse(content);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).line().dialReading()).isEqualTo(1050);
        assertThat(lines.get(0).line().readAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00-03:00"));
        assertThat(lines.get(1).line().dialReading()).isNull();
    }

    @Test
    @DisplayName("linha inválida vira erro nomeado e as demais linhas continuam")
    void badLineDoesNotBreakFile() {
        String content = AmrFileLayout.HEADER + "\n"
                + "AMR-1;PTO-001;VOL;ontem;1050;MEASURED\n"
                + "AMR-2;PTO-001;VOL;2026-11-10T08:00:00-03:00;10;MEASURED\n";

        List<AmrFileLayout.ParsedLine> lines = AmrFileLayout.parse(content);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).isError()).isTrue();
        assertThat(lines.get(1).isError()).isFalse();
    }

    @Test
    @DisplayName("cabeçalho de outro leiaute é erro explícito, nunca interpretação por aproximação")
    void wrongHeaderFails() {
        assertThatThrownBy(() -> AmrFileLayout.parse("A;B;C\n1;2;3"))
                .isInstanceOf(MeteringInputException.class);
    }
}
