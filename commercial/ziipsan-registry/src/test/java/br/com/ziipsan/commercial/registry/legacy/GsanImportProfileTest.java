package br.com.ziipsan.commercial.registry.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-09: o perfil GSAN sobre o framework da P1.2.
 *
 * <p>ATENÇÃO À MASSA: o JSON abaixo é um fixture SINTÉTICO de teste — os
 * nomes de coluna da origem ("mat_gsan", "setor_gsan", "saldo_gsan") são
 * nomes de brinquedo para exercitar o parser real da P1.2, NÃO o leiaute de
 * exportação do GSAN, que não consta de nenhuma fonte do projeto e será
 * confirmado com o legado do primeiro cliente de edital (aceite: "GSAN, se
 * confirmado"). Nenhum número deste teste é dado de legado real.
 */
class GsanImportProfileTest {

    private static final String SYNTHETIC_GSAN_MAPPING = """
            {"name": "gsan-sintetico", "version": "0-teste", "sourceSystem": "GSAN",
             "delimiter": ";",
             "fields": [
               {"source": "mat_gsan", "target": "MATRICULA", "transform": "TRIM", "required": true},
               {"source": "setor_gsan", "target": "SECTOR_CODE", "transform": "TRIM"},
               {"source": "saldo_gsan", "target": "BALANCE_CENTS", "transform": "DECIMAL_CENTS"}]}
            """;

    private static StagedUnit unit(String key, String sector, Long balance) {
        // Mesma construção do LegacyProfileAndReconciliationTest da P1.2.
        return new StagedUnit(UUID.randomUUID(), UUID.randomUUID(), 1, key,
                "1001", null, "1", sector, "5", "20", "RES",
                10L, 0L, 1L, balance, null,
                "Fulano Sintético", "CPF", "***.***.777-**", true,
                List.of(), StagedUnit.Status.PENDING, null);
    }

    @Test
    @DisplayName("mapeamento GSAN válido passa pelo parser real da P1.2")
    void validGsanMapping() {
        LegacyMapping mapping = GsanImportProfile.requireGsanMapping(SYNTHETIC_GSAN_MAPPING);
        assertThat(mapping.sourceSystem()).isEqualTo("GSAN");
        assertThat(mapping.fields()).hasSize(3);
    }

    @Test
    @DisplayName("mapeamento de outro legado é recusado por esta porta")
    void otherLegacyRefused() {
        String other = SYNTHETIC_GSAN_MAPPING.replace("\"GSAN\"", "\"PILOTO\"");
        assertThatThrownBy(() -> GsanImportProfile.requireGsanMapping(other))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GSAN");
    }

    @Test
    @DisplayName("conciliação limpa libera; divergência de saldo bloqueia a carga")
    void reconciliationGate() {
        List<StagedUnit> staged = List.of(unit("L1", "10", 1000L), unit("L2", "10", 500L));
        LegacyReconciliation.LegacyTotals ok = new LegacyReconciliation.LegacyTotals(
                Map.of("10", 2L), Map.of("20", 2L), Map.of("RES", 2L),
                1500L, Map.of("10", 1500L));
        assertThat(GsanImportProfile.requireReconciled(staged, ok).allMatch()).isTrue();

        LegacyReconciliation.LegacyTotals divergent = new LegacyReconciliation.LegacyTotals(
                Map.of("10", 2L), Map.of("20", 2L), Map.of("RES", 2L),
                9999L, Map.of("10", 1500L));
        assertThatThrownBy(() -> GsanImportProfile.requireReconciled(staged, divergent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("divergente");
    }

    @Test
    @DisplayName("mapeamento com alvo fora do vocabulário da P1.2 é recusado pelo parser")
    void unknownTargetRefused() {
        String bad = SYNTHETIC_GSAN_MAPPING.replace("\"MATRICULA\"", "\"ALVO_INVENTADO\"");
        assertThatThrownBy(() -> GsanImportProfile.requireGsanMapping(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
