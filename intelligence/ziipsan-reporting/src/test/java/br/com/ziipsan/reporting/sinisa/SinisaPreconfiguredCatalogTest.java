package br.com.ziipsan.reporting.sinisa;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integridade do catalogo pre-configurado contra a semente REAL do mapa da
 * P4.2 (sinisa-catalog-seed.json): as 6 chaves, formularios e unidades sao
 * os da semente — nem uma variavel a mais, nem um rotulo diferente.
 */
class SinisaPreconfiguredCatalogTest {

    @Test
    void catalogCoversExactlyTheP42SeedVariables() {
        var entries = SinisaPreconfiguredCatalog.entries();
        assertThat(entries).hasSize(6);
        Set<String> keys = entries.stream().map(SinisaCatalogEntry::sinisaVariableKey)
                .collect(Collectors.toSet());
        assertThat(keys).containsExactlyInAnyOrder(
                "AGUA.VOLUME_PRODUZIDO", "AGUA.VOLUME_FATURADO", "AGUA.AGUA_NAO_FATURADA",
                "AGUA.LIGACOES_ATIVAS", "FIN.RECEITA_OPERACIONAL_AGUA", "BALANCO.ATIVO_TOTAL");
        // Toda variavel de ENTRADA tambem esta no mapa (nenhuma fonte fora da P4.2):
        Set<String> inputKeys = entries.stream().flatMap(e -> e.variables().stream())
                .map(SinisaVariableRef::sinisaVariableKey).collect(Collectors.toSet());
        assertThat(keys).containsAll(inputKeys);
    }

    @Test
    void formsAndUnitsMatchTheSeed() {
        var byKey = SinisaPreconfiguredCatalog.entries().stream()
                .collect(Collectors.toMap(SinisaCatalogEntry::sinisaVariableKey, e -> e));
        assertThat(byKey.get("AGUA.VOLUME_PRODUZIDO").form()).isEqualTo(SinisaPanelForm.TECNICO);
        assertThat(byKey.get("AGUA.VOLUME_PRODUZIDO").unit()).isEqualTo("m3");
        assertThat(byKey.get("AGUA.LIGACOES_ATIVAS").unit()).isEqualTo("ligacoes");
        assertThat(byKey.get("FIN.RECEITA_OPERACIONAL_AGUA").form())
                .isEqualTo(SinisaPanelForm.FINANCEIRO);
        assertThat(byKey.get("FIN.RECEITA_OPERACIONAL_AGUA").unit()).isEqualTo("R$");
        assertThat(byKey.get("BALANCO.ATIVO_TOTAL").form()).isEqualTo(SinisaPanelForm.BALANCO);
    }

    @Test
    void unbilledWaterIsDerivedFromProducedMinusBilled() {
        var entry = SinisaPreconfiguredCatalog.entries().stream()
                .filter(e -> e.code().equals("SINISA.AGUA.AGUA_NAO_FATURADA")).findFirst().orElseThrow();
        assertThat(entry.formulaExpression()).isEqualTo("volume_produzido - volume_faturado");
        assertThat(entry.variables()).extracting(SinisaVariableRef::variableName)
                .containsExactly("volume_produzido", "volume_faturado");
    }

    @Test
    void linksPointEachIndicatorToItsMapVariable() {
        var tenant = java.util.UUID.randomUUID();
        var links = SinisaPreconfiguredCatalog.links(tenant);
        assertThat(links).hasSize(6);
        assertThat(links).allSatisfy(link -> {
            assertThat(link.tenantId()).isEqualTo(tenant);
            assertThat(link.sinisaVariableKey()).isNotBlank();
        });
    }
}
