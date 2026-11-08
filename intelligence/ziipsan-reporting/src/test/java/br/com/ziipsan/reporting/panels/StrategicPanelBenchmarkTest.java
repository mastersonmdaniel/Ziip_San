package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.integration.erp.ErpIndicators;
import br.com.ziipsan.integration.erp.ErpProvider;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.sinisa.BenchmarkBase;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Painel estrategico da ED-49: o benchmark exibido e o da ED-48
 * (GES-29) comparado sobre o valor do registro, e a secao do ERP e a
 * leitura da ED-62 transportada verbatim. Base e leituras sao massa
 * SINTETICA declarada (documento de importacao de teste, no formato da
 * ED-48) — nenhuma mediana real e afirmada.
 */
class StrategicPanelBenchmarkTest {

    private static final BenchmarkSlice SLICE = new BenchmarkSlice("PORTE-TESTE", "REGIAO-TESTE", "AUTARQUIA-TESTE");
    private static final LocalDate COMPETENCE = LocalDate.of(2026, 1, 1);

    private static BenchmarkBase syntheticBase() {
        String code = PanelFixtures.codeFor(PanelKind.ESTRATEGICO);
        return BenchmarkBase.fromJson("[{\"indicatorCode\": \"" + code + "\", "
                + "\"sizeClass\": \"PORTE-TESTE\", \"region\": \"REGIAO-TESTE\", "
                + "\"legalNature\": \"AUTARQUIA-TESTE\", \"median\": \"30\", "
                + "\"baseVersion\": \"SINTESE-TESTE-1\", "
                + "\"sourceDescription\": \"documento sintetico de teste da ED-49\"}]");
    }

    private static StrategicPanelService service(IndicatorRegistryService registry,
                                                 ErpIndicatorsPort port) {
        return new StrategicPanelService(new StandardPanelService(registry), port);
    }

    @Test
    void benchmarkShownIsExactlyTheEd48ComparisonOverTheRegistryValue() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        StrategicPanelView view = service(registry, null).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                syntheticBase(), SLICE, COMPETENCE);
        assertThat(view.rows()).hasSize(1);
        assertThat(view.benchmark()).hasSize(1);
        var shown = view.benchmark().get(0);
        var direct = syntheticBase().compare(new BigDecimal("42.5"),
                PanelFixtures.codeFor(PanelKind.ESTRATEGICO),
                "PORTE-TESTE", "REGIAO-TESTE", "AUTARQUIA-TESTE").orElseThrow();
        assertThat(shown.median()).isEqualByComparingTo(direct.median());
        assertThat(shown.difference()).isEqualByComparingTo(direct.difference());
        assertThat(shown.position()).isEqualTo(direct.position());
        assertThat(shown.tenantValue()).isEqualByComparingTo(view.rows().get(0).value());
        assertThat(view.benchmarkBaseVersion()).isEqualTo("SINTESE-TESTE-1");
        assertThat(view.pendingSections()).containsExactly(StrategicPanelService.PENDING_ERP);
    }

    @Test
    void withoutBaseOrSliceTheBenchmarkIsANamedPendingNeverAnotherSliceMedian() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        var noBase = service(registry, null).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                null, SLICE, COMPETENCE);
        assertThat(noBase.benchmark()).isEmpty();
        assertThat(noBase.benchmarkBaseVersion()).isNull();
        assertThat(noBase.pendingSections()).contains(StrategicPanelService.PENDING_BENCHMARK);
        var noSlice = service(registry, null).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                syntheticBase(), null, COMPETENCE);
        assertThat(noSlice.benchmark()).isEmpty();
        assertThat(noSlice.pendingSections()).contains(StrategicPanelService.PENDING_BENCHMARK);
    }

    @Test
    void codeWithoutReferenceInTheExactSliceIsNamedAndGetsNoBorrowedMedian() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        BenchmarkSlice other = new BenchmarkSlice("OUTRO-PORTE", "REGIAO-TESTE", "AUTARQUIA-TESTE");
        StrategicPanelView view = service(registry, null).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                syntheticBase(), other, COMPETENCE);
        assertThat(view.benchmark()).isEmpty();
        assertThat(view.pendingSections()).contains(
                StrategicPanelService.PENDING_BENCHMARK + ":" + PanelFixtures.codeFor(PanelKind.ESTRATEGICO));
    }

    @Test
    void erpSectionTransportsTheRealEd62ReadingVerbatim() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        // Leitura REAL da ED-62 (ErpIndicators.strategicPanel), adaptada a porta.
        List<ErpIndicators.ErpIndicator> read = List.of(
                new ErpIndicators.ErpIndicator(PanelFixtures.TENANT, ErpProvider.BETHA,
                        ErpIndicators.Area.PURCHASES, "ERP-COMPRAS-TESTE",
                        new BigDecimal("123.45"), COMPETENCE, "erp://teste/pedido-1"),
                new ErpIndicators.ErpIndicator(PanelFixtures.OTHER_TENANT, ErpProvider.BETHA,
                        ErpIndicators.Area.HR, "ERP-RH-OUTRO-TENANT",
                        new BigDecimal("999"), COMPETENCE, "erp://teste/outro"));
        ErpIndicatorsPort adapter = (tenantId, competence) ->
                ErpIndicators.strategicPanel(read, tenantId, competence).stream()
                        .map(i -> new ErpIndicatorSnapshot(i.provider().connectorName(), i.area().name(),
                                i.indicatorCode(), i.value(), i.competence(), i.sourceReference()))
                        .toList();
        StrategicPanelView view = service(registry, adapter).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                syntheticBase(), SLICE, COMPETENCE);
        assertThat(view.erpIndicators()).hasSize(1);
        var snapshot = view.erpIndicators().get(0);
        assertThat(snapshot.indicatorCode()).isEqualTo("ERP-COMPRAS-TESTE");
        assertThat(snapshot.value()).isEqualByComparingTo("123.45");
        assertThat(snapshot.sourceReference()).isEqualTo("erp://teste/pedido-1");
        assertThat(view.pendingSections()).doesNotContain(StrategicPanelService.PENDING_ERP);
    }

    @Test
    void erpSectionWithoutAdapterOrReadingIsNamedPendingNeverZero() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        var noAdapter = service(registry, null).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                syntheticBase(), SLICE, COMPETENCE);
        assertThat(noAdapter.erpIndicators()).isEmpty();
        assertThat(noAdapter.pendingSections()).contains(StrategicPanelService.PENDING_ERP);
        ErpIndicatorsPort empty = (tenantId, competence) -> List.of();
        var noReading = service(registry, empty).build(PanelFixtures.TENANT,
                List.of(PanelFixtures.request(PanelKind.ESTRATEGICO, "42.5")),
                syntheticBase(), SLICE, COMPETENCE);
        assertThat(noReading.erpIndicators()).isEmpty();
        assertThat(noReading.pendingSections()).contains(StrategicPanelService.PENDING_ERP);
    }

    @Test
    void incompleteSliceIsRefusedByName() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new BenchmarkSlice(" ", "REGIAO-TESTE", "AUTARQUIA-TESTE"))
                .hasMessageContaining("BENCHMARK_SLICE_SIZE_CLASS_REQUIRED");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ErpIndicatorSnapshot("erp-betha", "PURCHASES", "X", null,
                                COMPETENCE, "origem"))
                .hasMessageContaining("ERP_SNAPSHOT_VALUE_REQUIRED");
    }
}
