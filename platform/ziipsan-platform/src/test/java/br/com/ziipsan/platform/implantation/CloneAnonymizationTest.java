package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloneAnonymizationTest {

    private static CloneAnonymization.SourceUnit source() {
        return new CloneAnonymization.SourceUnit("MAT-1001", "Nome Sintético", "DOC-SINTETICO",
                "Endereço sintético", "contato@sintetico.invalid",
                new BigDecimal("-26.1000"), new BigDecimal("-48.1000"), "REDE-A/TRECHO-7");
    }

    @Test
    @DisplayName("anonimização: substituto no lugar da matrícula, técnico real intacto, sintético marcado")
    void anonymizeHappyPath() {
        CloneAnonymization.ClonedUnit cloned = CloneAnonymization.anonymize(source(), "SUB-0001",
                new BigDecimal("-26.1005"), new BigDecimal("-48.1003"),
                CloneAnonymization.CoordinateMethod.DISPLACED_WITHIN_BLOCK);
        assertThat(cloned.substituteCode()).isEqualTo("SUB-0001");
        assertThat(cloned.technicalNetworkRef()).isEqualTo("REDE-A/TRECHO-7");
        assertThat(cloned.synthetic()).isTrue();
        assertThat(cloned.coordinateMethod())
                .isEqualTo(CloneAnonymization.CoordinateMethod.DISPLACED_WITHIN_BLOCK);
    }

    @Test
    @DisplayName("código substituto igual à matrícula de origem é recusado")
    void substituteEqualsOriginalRefused() {
        assertThatThrownBy(() -> CloneAnonymization.anonymize(source(), "MAT-1001",
                new BigDecimal("-26.1005"), new BigDecimal("-48.1003"),
                CloneAnonymization.CoordinateMethod.BLOCK_CENTROID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matrícula de origem");
    }

    @Test
    @DisplayName("sem coordenada deslocada, a anonimização recusa (a de origem não atravessa)")
    void missingDisplacedCoordinateRefused() {
        assertThatThrownBy(() -> CloneAnonymization.anonymize(source(), "SUB-0001",
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deslocada");
    }

    @Test
    @DisplayName("coordenada do clone igual à do lote de origem é recusada")
    void sameCoordinateRefused() {
        assertThatThrownBy(() -> CloneAnonymization.anonymize(source(), "SUB-0001",
                new BigDecimal("-26.1000"), new BigDecimal("-48.1000"),
                CloneAnonymization.CoordinateMethod.DISPLACED_WITHIN_BLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coincide");
    }

    @Test
    @DisplayName("unidade sem coordenada anonimiza sem coordenada e sem método")
    void noCoordinateUnit() {
        CloneAnonymization.SourceUnit noCoord = new CloneAnonymization.SourceUnit(
                "MAT-2002", null, null, null, null, null, null, "REDE-B");
        CloneAnonymization.ClonedUnit cloned =
                CloneAnonymization.anonymize(noCoord, "SUB-0002", null, null, null);
        assertThat(cloned.latitude()).isNull();
        assertThat(cloned.coordinateMethod()).isNull();
    }

    @Test
    @DisplayName("método de coordenada sem coordenada alguma é recusado, não ignorado")
    void methodWithoutCoordinateRefused() {
        CloneAnonymization.SourceUnit noCoord = new CloneAnonymization.SourceUnit(
                "MAT-3003", null, null, null, null, null, null, "REDE-C");
        assertThatThrownBy(() -> CloneAnonymization.anonymize(noCoord, "SUB-0003",
                null, null, CloneAnonymization.CoordinateMethod.BLOCK_CENTROID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descartada");
    }

    @Test
    @DisplayName("varredura limpa num clone bem anonimizado; varredura acusa cada resíduo")
    void sweep() {
        CloneAnonymization.RawCloneRow clean = new CloneAnonymization.RawCloneRow(
                "SUB-0001", null, null, null, null, null,
                new BigDecimal("-26.1005"), new BigDecimal("-48.1003"),
                new BigDecimal("-26.1000"), new BigDecimal("-48.1000"), true);
        assertThat(CloneAnonymization.sweep(List.of(clean))).isEmpty();

        CloneAnonymization.RawCloneRow dirty = new CloneAnonymization.RawCloneRow(
                "SUB-0009", "Nome Vazado", null, null, null, "MAT-1001",
                new BigDecimal("-26.1000"), new BigDecimal("-48.1000"),
                new BigDecimal("-26.1000"), new BigDecimal("-48.1000"), false);
        List<CloneAnonymization.Violation> violations = CloneAnonymization.sweep(List.of(dirty));
        assertThat(violations).extracting(CloneAnonymization.Violation::kind).containsExactlyInAnyOrder(
                CloneAnonymization.ViolationKind.PERSONAL_FIELD_PRESENT,
                CloneAnonymization.ViolationKind.ORIGINAL_ENROLLMENT_PRESENT,
                CloneAnonymization.ViolationKind.COORDINATE_EQUALS_ORIGIN,
                CloneAnonymization.ViolationKind.NOT_MARKED_SYNTHETIC);
    }
}
