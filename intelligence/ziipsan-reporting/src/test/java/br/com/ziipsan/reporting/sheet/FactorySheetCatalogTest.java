package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.InMemoryIndicatorStore;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorySheetCatalogTest {

    @Test
    void catalogoSoComFonteCadaFichaCompleta() {
        List<SheetCatalogEntry> entries = FactorySheetCatalog.entries();
        assertEquals(5, entries.size());
        for (SheetCatalogEntry e : entries) {
            assertTrue(e.formulaExpression() != null && !e.formulaExpression().isBlank(), e.code());
            assertTrue(!e.variables().isEmpty(), e.code());
            for (SheetCatalogEntry.SheetVariable v : e.variables()) {
                assertTrue(v.origin() != null, e.code() + "/" + v.name());
            }
        }
        assertEquals(List.of("COBERTURA.IAA", "COBERTURA.ICA", "NR9.I04.INTERMITENCIA",
                "NR9.I05.EXTRAVASAMENTO", "MANUAL.ENERGIA_EMISSOES_PESSOAL"),
                entries.stream().map(SheetCatalogEntry::code).toList());
    }

    @Test
    void perfisScComConteudoADefinirNadaInventado() {
        List<FactorySheetCatalog.ScSheetProfile> profiles = FactorySheetCatalog.scProfiles();
        assertEquals(3, profiles.size());
        assertEquals(List.of("ARIS RN 57/2026", "AGIR RN 025/2026", "ARIS RN 36/2022"),
                profiles.stream().map(FactorySheetCatalog.ScSheetProfile::normativeReference).toList());
        for (FactorySheetCatalog.ScSheetProfile p : profiles) {
            assertTrue(p.contentStatus().startsWith("[A DEFINIR]"), p.normativeReference());
        }
    }

    @Test
    void calculoSobreMassaSinteticaViaRegistroP341() {
        // RAS-07: teste de calculo sobre massa sintetica - IAA com 800/1000 = 80.
        IndicatorRegistryService registry = new IndicatorRegistryService(
                new InMemoryIndicatorStore(), Clock.systemUTC());
        SheetService service = new SheetService(registry);
        UUID tenant = UUID.randomUUID();
        UUID resp = UUID.randomUUID();
        Map<String, String> views = Map.of(
                "domicilios_atendidos_agua", "rpt_sint_atendidos",
                "domicilios_totais", "rpt_sint_totais",
                "domicilios_coleta_esgoto", "rpt_sint_coleta",
                "economias_afetadas", "rpt_sint_economias",
                "economias_totais", "rpt_sint_economias_totais",
                "extravasamentos", "rpt_sint_extravasamentos",
                "extensao_rede_km", "rpt_sint_rede_km");
        service.materialize(IndicatorAccess.EDITOR, tenant, ConfidenceGrade.MEDIUM, views, resp,
                new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), null), resp);
        IndicatorRegistryService.Computation iaa = registry.compute(tenant, "COBERTURA.IAA",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null,
                Map.of("domicilios_atendidos_agua", new BigDecimal("800"),
                        "domicilios_totais", new BigDecimal("1000")));
        assertEquals(0, new BigDecimal("80").compareTo(iaa.value()));
        // I-05 da ED-92: 12 extravasamentos / 40 km = 0,3/km.
        IndicatorRegistryService.Computation i05 = registry.compute(tenant, "NR9.I05.EXTRAVASAMENTO",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null,
                Map.of("extravasamentos", new BigDecimal("12"),
                        "extensao_rede_km", new BigDecimal("40")));
        assertEquals(0, new BigDecimal("0.3").compareTo(i05.value()));
        // Ficha lida de volta: variaveis, contexto e grau vem do registro da P3.41.
        IndicatorSheet sheet = service.sheet(tenant, "COBERTURA.IAA",
                SheetFramework.NR9_ANA, SheetLevel.NA, List.of());
        assertEquals(2, sheet.variables().size());
        assertEquals(ConfidenceGrade.MEDIUM, sheet.confidenceGrade());
        // Tema manual nasce LOW forcado, mesmo com grau declarado maior.
        assertEquals(ConfidenceGrade.LOW,
                registry.definition(tenant, "MANUAL.ENERGIA_EMISSOES_PESSOAL").confidenceGrade());
    }

    @Test
    void materializacaoSemMapeamentoDeViewRecusaSemGravarNada() {
        IndicatorRegistryService registry = new IndicatorRegistryService(
                new InMemoryIndicatorStore(), Clock.systemUTC());
        SheetService service = new SheetService(registry);
        UUID tenant = UUID.randomUUID();
        IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                () -> service.materialize(IndicatorAccess.EDITOR, tenant, ConfidenceGrade.HIGH,
                        Map.of(), UUID.randomUUID(),
                        new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), null),
                        UUID.randomUUID()));
        assertEquals("SHEET_VARIABLE_VIEW_MAPPING_REQUIRED", e.code());
        assertThrows(IndicatorValidationException.class,
                () -> registry.definition(tenant, "COBERTURA.IAA"));
    }

    @Test
    void fichaExigeRateioFechado() {
        assertThrows(IndicatorValidationException.class, () -> new ZoneSplit("M",
                new BigDecimal("80"), new BigDecimal("60"), new BigDecimal("10")));
        new ZoneSplit("M", new BigDecimal("80"), new BigDecimal("64"), new BigDecimal("16"));
    }
}
