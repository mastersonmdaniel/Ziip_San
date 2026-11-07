package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Arquivo de envio assistido (P4.2, ADR-0004): pacote de transcricao para o
 * sistema web oficial — declara o canal, traz valor, origem e justificativa de
 * cada variavel e o resumo SHA-256 confere com o conteudo.
 */
class SinisaSubmissionFileTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID RESPONSAVEL = UUID.randomUUID();
    private static final OffsetDateTime T0 = OffsetDateTime.of(2027, 5, 13, 9, 0, 0, 0, ZoneOffset.of("-03:00"));

    private static SinisaCycle preliminaryCycle() {
        SinisaCatalog catalog = SinisaCatalog.fromJson("["
                + "{\"key\": \"AGUA.VOLUME_FATURADO\", \"officialCode\": null, \"form\": \"TECNICO\","
                + " \"name\": \"Volume faturado\", \"unit\": \"m3\", \"sourceKind\": \"METERING\","
                + " \"sourceExpression\": \"metering: soma de billed_consumption do ano-base\"}"
                + "]");
        SinisaCycle cycle = SinisaCycle.open(TENANT, 2026,
                SinisaParameters.fromValues(Map.of("sinisa.provider_scope", "\"LOCAL\"")),
                RESPONSAVEL, "Maria Responsavel", 1, 2025, catalog);
        cycle.prefill((t, y, v) -> Optional.of(new BigDecimal("7000")), RESPONSAVEL, T0);
        return cycle;
    }

    @Test
    @DisplayName("rascunho nao gera arquivo de envio")
    void rascunhoNaoGera() {
        SinisaCycle cycle = preliminaryCycle();

        assertThatThrownBy(() -> SinisaSubmissionFile.generate(cycle, T0))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("preliminar");
    }

    @Test
    @DisplayName("arquivo da versao preliminar declara o canal web, traz valor e origem e o hash confere")
    void arquivoPreliminar() {
        SinisaCycle cycle = preliminaryCycle();
        cycle.advanceToPreliminary(RESPONSAVEL, T0.plusHours(1), "Conferencia concluida.");

        SinisaSubmissionFile.GeneratedFile file = SinisaSubmissionFile.generate(cycle, T0.plusHours(2));

        JsonNode doc = Json.readTree(file.json());
        assertThat(doc.get("system").asText()).isEqualTo("SINISA");
        assertThat(doc.get("channel").asText()).isEqualTo("WEB_SYSTEM_TRANSCRIPTION");
        assertThat(doc.get("baseYear").asInt()).isEqualTo(2026);
        assertThat(doc.get("providerScope").asText()).isEqualTo("LOCAL");
        JsonNode entry = doc.get("values").get(0);
        assertThat(entry.get("key").asText()).isEqualTo("AGUA.VOLUME_FATURADO");
        assertThat(entry.get("value").asText()).isEqualTo("7000");
        assertThat(entry.get("origin").asText()).isEqualTo("AUTOMATIC");
        assertThat(entry.get("officialCode").isNull()).isTrue();
        assertThat(file.sha256()).isEqualTo(SinisaSubmissionFile.sha256(file.json()));
        assertThat(file.sha256()).hasSize(64);
    }

    @Test
    @DisplayName("geracao e deterministica: mesmo ciclo e instante geram o mesmo hash")
    void geracaoDeterministica() {
        SinisaCycle cycle = preliminaryCycle();
        cycle.advanceToPreliminary(RESPONSAVEL, T0.plusHours(1), "ok");

        var first = SinisaSubmissionFile.generate(cycle, T0.plusHours(2));
        var second = SinisaSubmissionFile.generate(cycle, T0.plusHours(2));

        assertThat(second.json()).isEqualTo(first.json());
        assertThat(second.sha256()).isEqualTo(first.sha256());
    }
}
