package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D1 da revisao cruzada: chave SINISA fora do mapa da P4.2 (semente
 * vigente) e recusada na construcao dos tres records que carregam chave
 * — nao basta nao estar em branco.
 */
class SinisaMapMembershipTest {

    @Test
    void inventedKeyIsRefusedInLinkVariableRefAndEntry() {
        var tenant = UUID.randomUUID();
        assertThatThrownBy(() -> new SinisaIndicatorLink(tenant, "IND-X", "CHAVE.INVENTADA",
                SinisaPanelForm.TECNICO))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("SINISA_VARIABLE_NOT_IN_MAP");
        assertThatThrownBy(() -> new SinisaVariableRef("valor", "ESGOTO.VOLUME_COLETADO",
                SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.METERING))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("SINISA_VARIABLE_NOT_IN_MAP");
        assertThatThrownBy(() -> new SinisaCatalogEntry("SINISA.X", "AGUA.CHAVE_NOVA",
                SinisaPanelForm.TECNICO, "Nome", "Definicao", "m3",
                IndicatorPeriodicity.ANNUAL, "valor",
                List.of(new SinisaVariableRef("valor", "AGUA.VOLUME_PRODUZIDO",
                        SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.METERING)),
                "Contexto"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("SINISA_VARIABLE_NOT_IN_MAP");
    }

    @Test
    void knownBoundaryIsExactlyThePreconfiguredCatalogKeys() {
        var entryKeys = SinisaPreconfiguredCatalog.entries().stream()
                .map(SinisaCatalogEntry::sinisaVariableKey).collect(Collectors.toSet());
        var inputKeys = SinisaPreconfiguredCatalog.entries().stream()
                .flatMap(e -> e.variables().stream())
                .map(SinisaVariableRef::sinisaVariableKey).collect(Collectors.toSet());
        assertThat(SinisaMapCatalog.knownKeys()).containsExactlyInAnyOrderElementsOf(entryKeys);
        assertThat(SinisaMapCatalog.knownKeys()).containsAll(inputKeys);
        // E as 6 chaves reais constroem normalmente:
        assertThat(SinisaMapCatalog.knownKeys()).allSatisfy(k -> {
            new SinisaIndicatorLink(UUID.randomUUID(), "IND", k, SinisaPanelForm.TECNICO);
            assertThat(SinisaMapCatalog.isKnown(k)).isTrue();
        });
    }

    @Test
    void displayNamesMatchTheSeedLiterally() {
        var byKey = SinisaPreconfiguredCatalog.entries().stream()
                .collect(Collectors.toMap(SinisaCatalogEntry::sinisaVariableKey, e -> e));
        // Nomes literais de sinisa-catalog-seed.json da P4.2 (D3):
        assertThat(byKey.get("AGUA.VOLUME_PRODUZIDO").name())
                .isEqualTo("Volume de agua produzido no ano-base");
        assertThat(byKey.get("AGUA.VOLUME_FATURADO").name())
                .isEqualTo("Volume de agua faturado no ano-base");
        assertThat(byKey.get("AGUA.AGUA_NAO_FATURADA").name())
                .isEqualTo("Agua nao faturada (volume de entrada menos volume faturado)");
        assertThat(byKey.get("AGUA.LIGACOES_ATIVAS").name())
                .isEqualTo("Quantidade de ligacoes ativas de agua no fim do ano-base");
        assertThat(byKey.get("FIN.RECEITA_OPERACIONAL_AGUA").name())
                .isEqualTo("Receita operacional direta de abastecimento de agua no ano-base");
        assertThat(byKey.get("BALANCO.ATIVO_TOTAL").name())
                .isEqualTo("Ativo total do balanco patrimonial no fim do ano-base");
    }
}
