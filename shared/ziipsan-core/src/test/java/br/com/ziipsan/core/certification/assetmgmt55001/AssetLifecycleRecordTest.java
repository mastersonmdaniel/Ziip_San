package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetLifecycleRecordTest {

    @Test
    @DisplayName("campo coincidente divergente: prevalece a definição NR/ANA (ED-56)")
    void regulatoryPrevails() {
        var f = AssetLifecycleRecord.CoincidentField.resolve("usefulLifeYears", "25", "30");
        assertThat(f.effectiveValue()).isEqualTo("30");
        assertThat(f.source()).isEqualTo(AssetLifecycleRecord.FieldSource.REGULATORY_NR_ANA);
        assertThat(f.divergent()).isTrue();
        var only = AssetLifecycleRecord.CoincidentField.resolve("material", "PVC", null);
        assertThat(only.source()).isEqualTo(AssetLifecycleRecord.FieldSource.COE05_ONLY);
        // Resolução declarada contra a prevalência é recusada:
        assertThatThrownBy(() -> new AssetLifecycleRecord.CoincidentField("usefulLifeYears",
                "25", "30", "25", AssetLifecycleRecord.FieldSource.COE05_ONLY, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("prevalência");
    }

    @Test
    @DisplayName("risco é LIDO da ED-56: score inconsistente com P×C é recusado, nunca recalculado")
    void riskReadNotRecalculated() {
        assertThatThrownBy(() -> AssetLifecycleRecord.RiskSnapshot.readFromEd56(4, 5, 19, "HIGH", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não o recalcula");
        var excluded = AssetLifecycleRecord.RiskSnapshot.readFromEd56(null, null, null, null,
                "sem grau de condição conhecido");
        assertThat(excluded.score()).isNull();
        assertThatThrownBy(() -> AssetLifecycleRecord.RiskSnapshot.readFromEd56(3, null, 9, null,
                "excluído")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("frota FORA: ativo VEÍCULO é recusado (é da ED-59)")
    void fleetOut() {
        assertThatThrownBy(() -> new AssetLifecycleRecord(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, "PAT-V1", AssetLifecycleRecord.AssetKind.VEHICLE,
                "IN_OPERATION", List.of(),
                AssetLifecycleRecord.RiskSnapshot.readFromEd56(1, 1, 1, "LOW", null),
                LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ED-59");
    }

    @Test
    @DisplayName("ciclo de vida usa os nomes da ED-56; nome estranho é recusado")
    void lifecycleVocabulary() {
        assertThatThrownBy(() -> new AssetLifecycleRecord(Iso55001Fixtures.on(),
                Iso55001Fixtures.ASSET, "PAT-1", AssetLifecycleRecord.AssetKind.LINEAR,
                "ATIVO", List.of(),
                AssetLifecycleRecord.RiskSnapshot.readFromEd56(1, 1, 1, "LOW", null),
                LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
