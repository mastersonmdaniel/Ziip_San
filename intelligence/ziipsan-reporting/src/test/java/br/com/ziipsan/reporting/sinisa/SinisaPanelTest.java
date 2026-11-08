package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.InMemoryIndicatorStore;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Painel exportavel da ED-48: apuracao pelo registro (com fechamento),
 * filtro por periodo/setor/categoria/tipo de ligacao e exportacao CSV/JSON.
 */
class SinisaPanelTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 12, 31);
    private static final String CODE = "SINISA.AGUA.AGUA_NAO_FATURADA";

    private IndicatorRegistryService registry;
    private SinisaPanelService panel;

    @BeforeEach
    void setUp() {
        registry = new IndicatorRegistryService(new InMemoryIndicatorStore(),
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC));
        new SinisaCatalogService(registry).registerPreconfigured(IndicatorAccess.EDITOR, TENANT,
                Map.of("AGUA.VOLUME_PRODUZIDO", "rpt_sinisa_volume_produzido",
                        "AGUA.VOLUME_FATURADO", "rpt_sinisa_volume_faturado",
                        "AGUA.LIGACOES_ATIVAS", "rpt_sinisa_ligacoes_ativas",
                        "FIN.RECEITA_OPERACIONAL_AGUA", "rpt_sinisa_receita_agua"),
                UUID.randomUUID(), ConfidenceGrade.MEDIUM,
                new ParameterVersionRule.Validity(START, END), ACTOR);
        panel = new SinisaPanelService(registry);
    }

    private static PanelCell cell(String sector, String category, String connectionType,
                                  String produced, String billed) {
        return new PanelCell(CODE, START, END, sector, category, connectionType,
                Map.of("volume_produzido", new BigDecimal(produced),
                        "volume_faturado", new BigDecimal(billed)));
    }

    private List<PanelCell> cells() {
        return List.of(
                cell("NORTE", "RESIDENCIAL", "AGUA", "10000", "7000"),   // 3000
                cell("SUL", "RESIDENCIAL", "AGUA", "5000", "4000"),      // 1000
                cell("NORTE", "COMERCIAL", "AGUA", "8000", "6000"),      // 2000
                cell("NORTE", "RESIDENCIAL", "ESGOTO", "2000", "1500")); // 500
    }

    @Test
    void panelComputesEveryCellThroughTheRegistry() {
        var rows = panel.buildPanel(TENANT, cells(), PanelFilter.none());
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(r -> r.value().toPlainString())
                .containsExactlyInAnyOrder("3000", "1000", "2000", "500");
        assertThat(rows).allSatisfy(r -> assertThat(r.frozen()).isFalse());
    }

    @Test
    void filterBySectorCategoryAndConnectionType() {
        assertThat(panel.buildPanel(TENANT, cells(),
                new PanelFilter(null, null, "NORTE", null, null))).hasSize(3);
        assertThat(panel.buildPanel(TENANT, cells(),
                new PanelFilter(null, null, null, "COMERCIAL", null))).hasSize(1);
        assertThat(panel.buildPanel(TENANT, cells(),
                new PanelFilter(null, null, null, null, "ESGOTO"))).hasSize(1);
        assertThat(panel.buildPanel(TENANT, cells(),
                new PanelFilter(null, null, "NORTE", "RESIDENCIAL", "AGUA")))
                .singleElement().satisfies(r -> assertThat(r.value()).isEqualByComparingTo("3000"));
    }

    @Test
    void filterByPeriodExcludesOtherPeriodStarts() {
        var rows = panel.buildPanel(TENANT, cells(),
                new PanelFilter(LocalDate.of(2026, 1, 1), null, null, null, null));
        assertThat(rows).isEmpty();
    }

    @Test
    void closedPeriodRowIsFrozenInThePanel() {
        registry.close(IndicatorAccess.EDITOR, TENANT, CODE, START, END, "NORTE",
                Map.of("volume_produzido", new BigDecimal("10000"),
                        "volume_faturado", new BigDecimal("7000")), ACTOR);
        var rows = panel.buildPanel(TENANT,
                List.of(cell("NORTE", "RESIDENCIAL", "AGUA", "99999", "1")),
                PanelFilter.none());
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.frozen()).isTrue();
            assertThat(r.value()).isEqualByComparingTo("3000");
        });
    }

    @Test
    void csvExportCarriesHeaderValuesAndSemaphore() {
        var rows = panel.buildPanel(TENANT, List.of(cell("NORTE", "RESIDENCIAL", "AGUA", "10000", "7000")),
                PanelFilter.none());
        String csv = panel.exportCsv(rows);
        assertThat(csv).startsWith("indicador;periodo_inicio;periodo_fim;setor;categoria;tipo_ligacao;valor;semaforo;fechado\n");
        assertThat(csv).contains(CODE + ";2025-01-01;2025-12-31;NORTE;RESIDENCIAL;AGUA;3000;NO_TARGET;NAO");
    }

    @Test
    void jsonExportIsValidJsonWrittenByTheCoreJsonClass() {
        var rows = panel.buildPanel(TENANT, List.of(cell("SUL", "COMERCIAL", "AGUA", "5000", "4000")),
                PanelFilter.none());
        var tree = Json.readTree(panel.exportJson(rows));
        assertThat(tree.isArray()).isTrue();
        assertThat(tree.get(0).get("indicador").asText()).isEqualTo(CODE);
        assertThat(tree.get(0).get("valor").asText()).isEqualTo("1000");
        assertThat(tree.get(0).get("semaforo").asText()).isEqualTo("NO_TARGET");
    }
}
