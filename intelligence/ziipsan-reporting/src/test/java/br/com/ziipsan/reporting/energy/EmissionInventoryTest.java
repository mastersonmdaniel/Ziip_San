package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inventario de emissoes (GES-21). Os fatores abaixo sao MASSA SINTETICA
 * declarada do teste (2, 3 e 5, com fonte ficticia rotulada como teste):
 * nenhum fator real e afirmado por esta entrega — o catalogo de
 * producao nasce vazio e a carga com fontes e pendencia P-ED90-02.
 */
class EmissionInventoryTest {

    private static final String VERSION = "V-TESTE";
    private static final String PARAMS = "{\"esg.emission_factors_version\": \"V-TESTE\"}";

    private static EmissionFactorCatalog syntheticCatalog() {
        return new EmissionFactorCatalog()
                .add(new EmissionFactorCatalog.EmissionFactor(VERSION, EmissionSource.ELECTRICITY,
                        new BigDecimal("2"), "kWh", "kgCO2e", "FONTE SINTETICA DE TESTE"))
                .add(new EmissionFactorCatalog.EmissionFactor(VERSION, EmissionSource.FLEET_FUEL,
                        new BigDecimal("3"), "L", "kgCO2e", "FONTE SINTETICA DE TESTE"))
                .add(new EmissionFactorCatalog.EmissionFactor(VERSION, EmissionSource.WASTEWATER_METHANE,
                        new BigDecimal("5"), "m3", "kgCO2e", "FONTE SINTETICA DE TESTE"));
    }

    @Test
    void inventarioConhecidoEscoposUmEDois() {
        var result = EmissionInventory.compute(syntheticCatalog(), PARAMS, List.of(
                new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                        new BigDecimal("1000"), "kWh", EnergyDataSource.IMPORTED_BILL),
                new EmissionInventory.EmissionActivity(EmissionSource.FLEET_FUEL,
                        new BigDecimal("100"), "L", EnergyDataSource.FLEET_ED59),
                new EmissionInventory.EmissionActivity(EmissionSource.WASTEWATER_METHANE,
                        new BigDecimal("10"), "m3", EnergyDataSource.MANUAL)));
        // escopo 2: 1000*2 = 2000; escopo 1: 100*3 + 10*5 = 350; total 2350
        assertThat(result.scope2Total()).isEqualByComparingTo("2000");
        assertThat(result.scope1Total()).isEqualByComparingTo("350");
        assertThat(result.total()).isEqualByComparingTo("2350");
        assertThat(result.catalogVersion()).isEqualTo(VERSION);
    }

    @Test
    void semVersaoDeclaradaRecusa() {
        assertThatThrownBy(() -> EmissionInventory.compute(syntheticCatalog(), "{}",
                List.of(new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                        new BigDecimal("1"), "kWh", EnergyDataSource.MANUAL))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ESG_EMISSION_FACTORS_VERSION_REQUIRED");
    }

    @Test
    void fatorAusenteNaVersaoRecusaSemPresumir() {
        EmissionFactorCatalog partial = new EmissionFactorCatalog()
                .add(new EmissionFactorCatalog.EmissionFactor(VERSION, EmissionSource.ELECTRICITY,
                        new BigDecimal("2"), "kWh", "kgCO2e", "FONTE SINTETICA DE TESTE"));
        assertThatThrownBy(() -> EmissionInventory.compute(partial, PARAMS,
                List.of(new EmissionInventory.EmissionActivity(EmissionSource.FLEET_FUEL,
                        new BigDecimal("10"), "L", EnergyDataSource.MANUAL))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_FACTOR_NOT_FOUND")
                .hasMessageContaining("[SEM FONTE]");
    }

    @Test
    void fatorSemFonteCitadaRecusaNaEntradaDoCatalogo() {
        assertThatThrownBy(() -> new EmissionFactorCatalog.EmissionFactor(VERSION,
                EmissionSource.ELECTRICITY, new BigDecimal("2"), "kWh", "kgCO2e", " "))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_FACTOR_SOURCE_REQUIRED");
    }

    @Test
    void unidadeDivergenteDoFatorRecusa() {
        assertThatThrownBy(() -> EmissionInventory.compute(syntheticCatalog(), PARAMS,
                List.of(new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                        new BigDecimal("10"), "MWh", EnergyDataSource.MANUAL))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_FACTOR_UNIT_MISMATCH");
    }

    @Test
    void fatorDuplicadoNaMesmaVersaoRecusa() {
        EmissionFactorCatalog catalog = syntheticCatalog();
        assertThatThrownBy(() -> catalog.add(new EmissionFactorCatalog.EmissionFactor(VERSION,
                EmissionSource.ELECTRICITY, new BigDecimal("9"), "kWh", "kgCO2e",
                "FONTE SINTETICA DE TESTE")))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_FACTOR_DUPLICATED");
    }

    // ---- Correcoes D3/D6 (REVISAO-ED-90, sondas P10 e P16) ----

    @Test
    void unidadesDeEmissaoDivergentesRecusamTotaisP10() {
        EmissionFactorCatalog mixed = new EmissionFactorCatalog()
                .add(new EmissionFactorCatalog.EmissionFactor(VERSION, EmissionSource.FLEET_FUEL,
                        new BigDecimal("3"), "L", "tCO2e", "FONTE SINTETICA DE TESTE"))
                .add(new EmissionFactorCatalog.EmissionFactor(VERSION,
                        EmissionSource.WASTEWATER_METHANE,
                        new BigDecimal("5"), "m3", "kgCO2e", "FONTE SINTETICA DE TESTE"));
        assertThatThrownBy(() -> EmissionInventory.compute(mixed, PARAMS, List.of(
                new EmissionInventory.EmissionActivity(EmissionSource.FLEET_FUEL,
                        new BigDecimal("100"), "L", EnergyDataSource.FLEET_ED59),
                new EmissionInventory.EmissionActivity(EmissionSource.WASTEWATER_METHANE,
                        new BigDecimal("10"), "m3", EnergyDataSource.MANUAL))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_INVENTORY_EMISSION_UNIT_DIVERGENCE");
    }

    @Test
    void resultadoEExportacaoCarregamAUnidadeDeEmissao() {
        var result = EmissionInventory.compute(syntheticCatalog(), PARAMS,
                List.of(new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                        new BigDecimal("1000"), "kWh", EnergyDataSource.IMPORTED_BILL)));
        assertThat(result.emissionUnit()).isEqualTo("kgCO2e");
        String csv = SustainabilityExport.exportCsv(
                "{\"esg.report_profile\": \"PERFIL-TESTE\"}", result, "2026");
        assertThat(csv).contains("unidade_emissao;kgCO2e");
        assertThat(csv).contains("total;;;;;kgCO2e;2000;");
    }

    // ---- Correcao R4-m1 (Rodada 4 da auditoria continua) ----

    @Test
    void resultadoSemUnidadeDeEmissaoRecusaR4m1() {
        assertThatThrownBy(() -> new EmissionInventory.InventoryResult(VERSION, List.of(),
                new BigDecimal("350"), new BigDecimal("2000"), new BigDecimal("2350"), null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_INVENTORY_EMISSION_UNIT_REQUIRED");
    }

    @Test
    void resultadoComUnidadeDeEmissaoEmBrancoRecusaR4m1() {
        assertThatThrownBy(() -> new EmissionInventory.InventoryResult(VERSION, List.of(),
                new BigDecimal("350"), new BigDecimal("2000"), new BigDecimal("2350"), "   "))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_INVENTORY_EMISSION_UNIT_REQUIRED");
    }

    @Test
    void resultadoCanonicoCarregaAUnidadeDeEmissaoR4m1() {
        // A forma legada de 5 argumentos sem a unidade nao existe mais:
        // a unica construcao e a canonica, com unidade real. A remocao
        // e provada pela compilacao da suite inteira (qualquer uso
        // legado nao compilaria) e por esta construcao canonica direta.
        var result = new EmissionInventory.InventoryResult(VERSION, List.of(),
                new BigDecimal("350"), new BigDecimal("2000"), new BigDecimal("2350"), "kgCO2e");
        assertThat(result.emissionUnit()).isEqualTo("kgCO2e");
        assertThat(result.total()).isEqualByComparingTo("2350");
    }

    @Test
    void origemFleetEd59SoCoerenteComCombustivelP16() {
        assertThatThrownBy(() -> new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                new BigDecimal("100"), "kWh", EnergyDataSource.FLEET_ED59))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_ACTIVITY_ORIGIN_MISMATCH");
        assertThatThrownBy(() -> new EmissionInventory.EmissionActivity(EmissionSource.FLEET_FUEL,
                new BigDecimal("100"), "L", EnergyDataSource.IMPORTED_BILL))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("EMISSION_ACTIVITY_ORIGIN_MISMATCH");
    }

    @Test
    void exportacaoSemPerfilRecusaMasInventarioNaoDependeDePerfil() {
        var result = EmissionInventory.compute(syntheticCatalog(), PARAMS,
                List.of(new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                        new BigDecimal("1000"), "kWh", EnergyDataSource.IMPORTED_BILL)));
        assertThat(result.total()).isEqualByComparingTo("2000");
        assertThatThrownBy(() -> SustainabilityExport.exportCsv("{}", result, "2026"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ESG_REPORT_PROFILE_NOT_DECLARED");
    }

    @Test
    void exportacaoComPerfilDeclaradoExporta() {
        var result = EmissionInventory.compute(syntheticCatalog(), PARAMS,
                List.of(new EmissionInventory.EmissionActivity(EmissionSource.ELECTRICITY,
                        new BigDecimal("1000"), "kWh", EnergyDataSource.IMPORTED_BILL)));
        String csv = SustainabilityExport.exportCsv(
                "{\"esg.report_profile\": \"PERFIL-TESTE\"}", result, "2026");
        assertThat(csv).contains("PERFIL-TESTE").contains("V-TESTE").contains("2000");
    }
}
