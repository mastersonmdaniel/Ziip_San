package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ServiceConnectionFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra da GIS-05 (P2.23) no motor da GIS-03: ligação ativa sem ramal
 * vira problema de validação ({@code ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE},
 * WARNING do §5.4), e o construtor antigo do Snapshot (sem ligações)
 * continua válido — nenhum teste existente muda.
 */
class GisValidationEngineServiceConnectionTest {

    private static final UUID CONNECTION = UUID.randomUUID();

    private static GisValidationEngine.Snapshot snapshot(List<ServiceConnectionFeature> serviceConnections) {
        return new GisValidationEngine.Snapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), Set.of(), serviceConnections);
    }

    private static ServiceConnectionFeature connection(String role, boolean open, long lines) {
        return new ServiceConnectionFeature(CONNECTION, UUID.randomUUID(), "WATER", role, open, lines);
    }

    @Test
    @DisplayName("ligação ativa (ACTIVE) sem nenhum ramal gera o problema")
    void activeWithoutLineIsFlagged() {
        List<DetectedIssue> issues = GisValidationEngine.detect(
                snapshot(List.of(connection("ACTIVE", true, 0))));
        assertThat(issues).hasSize(1);
        DetectedIssue issue = issues.get(0);
        assertThat(issue.ruleCode()).isEqualTo("ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE");
        assertThat(issue.severity()).isEqualTo("WARNING");
        assertThat(issue.featureTable()).isEqualTo("service_connections");
        assertThat(issue.featureId()).isEqualTo(CONNECTION);
        assertThat(issue.message()).contains("ligacao ativa sem ramal");
    }

    @Test
    @DisplayName("ligação cortada (CUT) também é ativa para a regra: sem ramal, problema")
    void cutWithoutLineIsFlagged() {
        assertThat(GisValidationEngine.detect(snapshot(List.of(connection("CUT", true, 0)))))
                .hasSize(1);
    }

    @Test
    @DisplayName("ligação com ramal não gera problema")
    void withLineIsClean() {
        assertThat(GisValidationEngine.detect(snapshot(List.of(connection("ACTIVE", true, 1)))))
                .isEmpty();
    }

    @Test
    @DisplayName("sem ligação física (NO_CONNECTION) não há ramal a cobrar")
    void noConnectionRoleIsClean() {
        assertThat(GisValidationEngine.detect(snapshot(List.of(connection("NO_CONNECTION", true, 0)))))
                .isEmpty();
    }

    @Test
    @DisplayName("ligação encerrada (ended_on preenchido) não gera problema")
    void endedConnectionIsClean() {
        assertThat(GisValidationEngine.detect(snapshot(List.of(connection("ACTIVE", false, 0)))))
                .isEmpty();
    }

    @Test
    @DisplayName("snapshot antigo (6 argumentos, sem ligações) não detecta a regra nova")
    void legacySnapshotHasNoServiceConnections() {
        GisValidationEngine.Snapshot legacy = new GisValidationEngine.Snapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
        assertThat(legacy.serviceConnections()).isEmpty();
        assertThat(GisValidationEngine.detect(legacy)).isEmpty();
    }
}
