package br.com.ziipsan.reporting.panels;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardPanelCatalogTest {

    @Test
    void catalogHasExactlyTheSevenPanelsOfTheAcceptanceInOrder() {
        assertThat(StandardPanelCatalog.all()).extracting(PanelDefinition::kind)
                .containsExactly(PanelKind.ARRECADACAO, PanelKind.ATENDIMENTO, PanelKind.COBRANCA,
                        PanelKind.FATURAMENTO, PanelKind.MEDICAO, PanelKind.OPERACIONAL,
                        PanelKind.ESTRATEGICO);
        assertThat(StandardPanelCatalog.all()).extracting(PanelDefinition::title)
                .containsExactly("Arrecadação", "Atendimento", "Cobrança", "Faturamento",
                        "Medição", "Operacional", "Indicadores estratégicos");
    }

    @Test
    void pocRefsAreTheFiveNamedInTheVisanScriptAndNullWhereTheScriptNamesNone() {
        assertThat(StandardPanelCatalog.byKind(PanelKind.ARRECADACAO).orElseThrow().pocRef())
                .isEqualTo("POC-77");
        assertThat(StandardPanelCatalog.byKind(PanelKind.ATENDIMENTO).orElseThrow().pocRef())
                .isEqualTo("POC-78");
        assertThat(StandardPanelCatalog.byKind(PanelKind.COBRANCA).orElseThrow().pocRef())
                .isEqualTo("POC-79");
        assertThat(StandardPanelCatalog.byKind(PanelKind.FATURAMENTO).orElseThrow().pocRef())
                .isEqualTo("POC-80");
        assertThat(StandardPanelCatalog.byKind(PanelKind.ESTRATEGICO).orElseThrow().pocRef())
                .isEqualTo("POC-81");
        // O roteiro nao nomeia POC propria para medicao/operacional: null declarado, nao inventado.
        assertThat(StandardPanelCatalog.byKind(PanelKind.MEDICAO).orElseThrow().pocRef()).isNull();
        assertThat(StandardPanelCatalog.byKind(PanelKind.OPERACIONAL).orElseThrow().pocRef()).isNull();
    }

    @Test
    void proposedSlugsAreUnique() {
        assertThat(StandardPanelCatalog.all()).extracting(PanelDefinition::supersetSlug)
                .doesNotHaveDuplicates();
    }

    @Test
    void noPanelIsARealtimeConsoleAndRealtimeDefinitionIsRefused() {
        assertThat(StandardPanelCatalog.all()).allMatch(d -> !d.realtimeConsole());
        assertThatThrownBy(() -> new PanelDefinition(PanelKind.OPERACIONAL, "slug-x", true))
                .hasMessageContaining("OPE22_REALTIME_CONSOLE_OUT_OF_BI");
    }

    @Test
    void unboundPanelsNamesEveryPanelWithoutARequest() {
        List<PanelCellRequest> onlyOne = List.of(PanelFixtures.request(PanelKind.COBRANCA, "1"));
        assertThat(StandardPanelCatalog.unboundPanels(onlyOne))
                .containsExactly(PanelKind.ARRECADACAO, PanelKind.ATENDIMENTO, PanelKind.FATURAMENTO,
                        PanelKind.MEDICAO, PanelKind.OPERACIONAL, PanelKind.ESTRATEGICO);
        assertThat(StandardPanelCatalog.unboundPanels(PanelFixtures.oneRequestPerPanel("1")))
                .isEmpty();
    }
}
