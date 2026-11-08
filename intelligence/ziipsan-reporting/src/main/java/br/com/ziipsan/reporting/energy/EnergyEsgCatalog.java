package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.List;

/**
 * Catalogo pre-configurado de indicadores de energia e ESG da ED-90
 * (aceite: "indicadores registrados no registro da P3.41 e publicados
 * no catalogo da ED-48"). As entradas sao o modelo (codigo, nome,
 * definicao, unidade, periodicidade, formula e variaveis com a origem
 * declarada) que o {@link EnergyCatalogService} materializa no registro
 * unico da P3.41; materializados, sao indicadores comuns do registro —
 * e por ele que o painel/catalogo da ED-48 os publica (a ED-48 le o
 * registro, nao tem lista propria de indicadores).
 *
 * <p>Formulas na gramatica fechada do FormulaEvaluator da P3.41
 * (numeros, variaveis, {@code + - * /}, parenteses). Variaveis com
 * origem AUTOMATIC viram fonte {@code REPORT_VIEW} via mapeamento de
 * quem integra; nenhuma view e presumida aqui (desenho da ED-48).
 * Grau de confianca e meta NAO sao do catalogo: sao do tenant na
 * materializacao (precedente ED-48) — o grau esperado aqui e o
 * ATUALIZADO da fonte automatica ({@link EnergyDataSource}).
 */
public final class EnergyEsgCatalog {

    private EnergyEsgCatalog() {
    }

    /** Origem declarada de uma variavel do catalogo. */
    public enum VariableOrigin {
        AUTOMATIC,
        MANUAL
    }

    public record CatalogVariable(String variableName, VariableOrigin origin) {
        public CatalogVariable {
            if (variableName == null || variableName.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_VARIABLE_REQUIRED",
                        "variavel do catalogo de energia/ESG sem nome");
            }
            if (origin == null) {
                throw new IndicatorValidationException("ENERGY_CATALOG_VARIABLE_ORIGIN_REQUIRED",
                        "variavel " + variableName + " sem a origem declarada (automatica ou manual)");
            }
        }
    }

    public record EnergyCatalogEntry(String code, String name, String definitionText,
                                     String unit, IndicatorPeriodicity periodicity,
                                     String formulaExpression, List<CatalogVariable> variables,
                                     String context) {
        public EnergyCatalogEntry {
            if (code == null || code.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_CODE_REQUIRED",
                        "entrada do catalogo de energia/ESG sem codigo");
            }
            if (name == null || name.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_NAME_REQUIRED",
                        "entrada " + code + " sem nome");
            }
            if (definitionText == null || definitionText.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_DEFINITION_REQUIRED",
                        "entrada " + code + " sem definicao");
            }
            if (unit == null || unit.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_UNIT_REQUIRED",
                        "entrada " + code + " sem unidade");
            }
            if (periodicity == null) {
                throw new IndicatorValidationException("ENERGY_CATALOG_PERIODICITY_REQUIRED",
                        "entrada " + code + " sem periodicidade");
            }
            if (formulaExpression == null || formulaExpression.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_FORMULA_REQUIRED",
                        "entrada " + code + " sem formula sobre as variaveis declaradas");
            }
            if (variables == null || variables.isEmpty()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_VARIABLES_REQUIRED",
                        "entrada " + code + " sem variaveis");
            }
            if (context == null || context.isBlank()) {
                throw new IndicatorValidationException("ENERGY_CATALOG_CONTEXT_REQUIRED",
                        "entrada " + code + " sem contexto (ficha da P3.41)");
            }
            variables = List.copyOf(variables);
        }
    }

    private static CatalogVariable auto(String name) {
        return new CatalogVariable(name, VariableOrigin.AUTOMATIC);
    }

    /** As 7 entradas do aceite: 4 de energia (OPE-08) e 3 de emissoes (GES-21). */
    public static List<EnergyCatalogEntry> entries() {
        return List.of(
                new EnergyCatalogEntry("ENERGY.KWH_PER_M3",
                        "Consumo especifico de energia",
                        "kWh consumidos por m3 produzido ou bombeado, por sistema, ETA e elevatoria",
                        "kWh/m3", IndicatorPeriodicity.MONTHLY,
                        "kwh_total / volume_m3",
                        List.of(auto("kwh_total"), auto("volume_m3")),
                        "Eficiencia energetica operacional (OPE-08): numerador da fatura/telemetria, "
                                + "denominador do volume da unidade no periodo"),
                new EnergyCatalogEntry("ENERGY.PEAK_SHARE",
                        "Participacao do consumo na ponta",
                        "Percentual do consumo total ocorrido no horario de ponta",
                        "%", IndicatorPeriodicity.MONTHLY,
                        "consumo_ponta_kwh * 100 / kwh_total",
                        List.of(auto("consumo_ponta_kwh"), auto("kwh_total")),
                        "Estrutura de consumo da fatura (OPE-08)"),
                new EnergyCatalogEntry("ENERGY.DEMAND_RATIO",
                        "Demanda medida sobre a contratada",
                        "Razao entre a demanda medida e a demanda contratada da instalacao",
                        "razao", IndicatorPeriodicity.MONTHLY,
                        "demanda_medida_kw / demanda_contratada_kw",
                        List.of(auto("demanda_medida_kw"), auto("demanda_contratada_kw")),
                        "Acompanhamento do risco de multa por ultrapassagem (OPE-08); o alerta "
                                + "usa energy.demand_alert_pct em EnergyEfficiency"),
                new EnergyCatalogEntry("ENERGY.POWER_FACTOR",
                        "Fator de potencia",
                        "Fator de potencia medido na fatura da instalacao",
                        "adimensional", IndicatorPeriodicity.MONTHLY,
                        "fator_potencia",
                        List.of(auto("fator_potencia")),
                        "Acompanhamento do risco de multa por fator de potencia baixo (OPE-08); "
                                + "o alerta usa energy.pf_min em EnergyEfficiency"),
                new EnergyCatalogEntry("ESG.SCOPE1_EMISSIONS",
                        "Emissoes de escopo 1",
                        "Emissoes diretas do inventario simplificado (metano de ETE e combustivel), "
                                + "na unidade de emissao do catalogo de fatores declarado",
                        "unidade do catalogo de fatores", IndicatorPeriodicity.ANNUAL,
                        "emissoes_escopo1",
                        List.of(auto("emissoes_escopo1")),
                        "Inventario simplificado de emissoes (GES-21), calculado por EmissionInventory "
                                + "com esg.emission_factors_version"),
                new EnergyCatalogEntry("ESG.SCOPE2_EMISSIONS",
                        "Emissoes de escopo 2",
                        "Emissoes indiretas de energia eletrica comprada do inventario simplificado, "
                                + "na unidade de emissao do catalogo de fatores declarado",
                        "unidade do catalogo de fatores", IndicatorPeriodicity.ANNUAL,
                        "emissoes_escopo2",
                        List.of(auto("emissoes_escopo2")),
                        "Inventario simplificado de emissoes (GES-21), calculado por EmissionInventory "
                                + "com esg.emission_factors_version"),
                new EnergyCatalogEntry("ESG.TOTAL_EMISSIONS",
                        "Emissoes totais (escopos 1 e 2)",
                        "Soma das emissoes dos escopos 1 e 2 do inventario simplificado",
                        "unidade do catalogo de fatores", IndicatorPeriodicity.ANNUAL,
                        "emissoes_escopo1 + emissoes_escopo2",
                        List.of(auto("emissoes_escopo1"), auto("emissoes_escopo2")),
                        "Inventario simplificado de emissoes (GES-21)"));
    }
}
