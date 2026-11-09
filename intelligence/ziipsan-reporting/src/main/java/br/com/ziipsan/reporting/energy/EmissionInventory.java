package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inventario simplificado de emissoes, escopos 1 e 2 (aceite da ED-90,
 * GES-21): cada atividade lancada e multiplicada pelo fator da VERSAO
 * declarada do catalogo global ({@code esg.emission_factors_version}).
 *
 * <p>Regras duras:
 * <ul>
 *   <li>sem a versao declarada no parametro do tenant, o inventario
 *       recusa — nao usa "a ultima versao" nem versao presumida;</li>
 *   <li>atividade sem fator na versao declarada recusa nomeada —
 *       a linha NAO entra com fator de outra versao nem com zero;</li>
 *   <li>a unidade da atividade tem de casar com a unidade declarada
 *       no fator (que veio com a fonte); divergencia e recusa;</li>
 *   <li>os totais so somam linhas da MESMA unidade de emissao —
 *       unidade divergente entre linhas e recusa (correcao D3 da
 *       REVISAO-ED-90), e o resultado carrega a unidade;</li>
 *   <li>fonte e origem sao coerentes pelo mapa declarado em
 *       {@link EmissionActivity} (correcao D6 da REVISAO-ED-90);</li>
 *   <li>cada linha guarda o retrato do fator usado (valor e unidades),
 *       para a apuracao ser refeita e auditada depois;</li>
 *   <li>o inventario NAO exige perfil de relatorio: a exportacao e
 *       voluntaria ({@link SustainabilityExport}) e o aceite declara
 *       que nenhuma obrigacao de relatorio foi identificada para
 *       autarquia municipal.</li>
 * </ul>
 */
public final class EmissionInventory {

    private EmissionInventory() {
    }

    /** Atividade lancada: quantidade medida numa unidade, com a origem do dado. */
    public record EmissionActivity(EmissionSource source, BigDecimal quantity,
                                   String activityUnit, EnergyDataSource origin) {
        public EmissionActivity {
            if (source == null) {
                throw new IndicatorValidationException("EMISSION_ACTIVITY_SOURCE_REQUIRED",
                        "atividade do inventario sem a fonte (eletricidade, metano, combustivel)");
            }
            if (quantity == null || quantity.signum() <= 0) {
                throw new IndicatorValidationException("EMISSION_ACTIVITY_QUANTITY_INVALID",
                        "atividade de " + source + " exige quantidade positiva: " + quantity);
            }
            if (activityUnit == null || activityUnit.isBlank()) {
                throw new IndicatorValidationException("EMISSION_ACTIVITY_UNIT_REQUIRED",
                        "atividade de " + source + " sem a unidade declarada");
            }
            if (origin == null) {
                throw new IndicatorValidationException("EMISSION_ACTIVITY_ORIGIN_REQUIRED",
                        "atividade de " + source + " sem a origem do dado "
                                + "(telemetria, fatura, ED-59 ou manual - grau de confianca da ficha)");
            }
            // CORRECAO D6 (REVISAO-ED-90): coerencia fonte x origem,
            // mapa DECLARADO desta entrega: a origem FLEET_ED59 so
            // alimenta a fonte FLEET_FUEL (sao os abastecimentos da
            // ED-59), e o combustivel so chega pela ED-59 ou pelo
            // ramo manual do aceite (GES-21) — fatura de energia e
            // telemetria de kWh nao sao origem de combustivel.
            if (origin == EnergyDataSource.FLEET_ED59 && source != EmissionSource.FLEET_FUEL) {
                throw new IndicatorValidationException("EMISSION_ACTIVITY_ORIGIN_MISMATCH",
                        "origem FLEET_ED59 (abastecimentos da ED-59) so e coerente com a "
                                + "fonte FLEET_FUEL; recebida para " + source);
            }
            if (source == EmissionSource.FLEET_FUEL
                    && origin != EnergyDataSource.FLEET_ED59
                    && origin != EnergyDataSource.MANUAL) {
                throw new IndicatorValidationException("EMISSION_ACTIVITY_ORIGIN_MISMATCH",
                        "combustivel do inventario vem da ED-59 ou do ramo manual do "
                                + "aceite (GES-21); origem recebida: " + origin);
            }
        }
    }

    /** Linha calculada: atividade x fator (retrato), com escopo e emissao resultante. */
    public record EmissionLine(EmissionSource source, int scope, BigDecimal quantity,
                               String activityUnit, BigDecimal factorValue,
                               String emissionUnit, BigDecimal emissions,
                               EnergyDataSource origin) {
    }

    /**
     * Resultado: linhas, totais por escopo e total, sempre com a versao
     * do catalogo e — CORRECAO D3 (REVISAO-ED-90) — com a UNIDADE DE
     * EMISSAO dos totais ({@code emissionUnit}): um total sem unidade
     * nao e numero auditavel. Como o {@code compute} recusa unidades
     * divergentes entre as linhas, esta e a unidade unica do resultado.
     *
     * <p>CORRECAO R4-m1 (Rodada 4 da auditoria continua): a unidade de
     * emissao e obrigatoria — nula ou em branco recusa — e a forma
     * legada de 5 argumentos sem a unidade foi removida.
     */
    public record InventoryResult(String catalogVersion, List<EmissionLine> lines,
                                  BigDecimal scope1Total, BigDecimal scope2Total,
                                  BigDecimal total, String emissionUnit) {
        public InventoryResult {
            lines = lines == null ? List.of() : List.copyOf(lines);
            if (emissionUnit == null || emissionUnit.isBlank()) {
                throw new IndicatorValidationException("EMISSION_INVENTORY_EMISSION_UNIT_REQUIRED",
                        "resultado do inventario sem a unidade de emissao dos totais: "
                                + "um total sem unidade nao e numero auditavel");
            }
        }
    }

    public static InventoryResult compute(EmissionFactorCatalog catalog, String parametersJson,
                                          List<EmissionActivity> activities) {
        String version = EnergyParameters.emissionFactorsVersion(parametersJson)
                .orElseThrow(() -> new IndicatorValidationException(
                        "ESG_EMISSION_FACTORS_VERSION_REQUIRED",
                        "inventario exige " + EnergyParameters.EMISSION_FACTORS_VERSION
                                + " declarado pelo tenant: sem a versao do catalogo global "
                                + "de fatores, nenhum fator e presumido"));
        if (catalog == null) {
            throw new IndicatorValidationException("EMISSION_FACTOR_CATALOG_REQUIRED",
                    "inventario sem o catalogo global de fatores de emissao");
        }
        if (activities == null || activities.isEmpty()) {
            throw new IndicatorValidationException("EMISSION_ACTIVITY_REQUIRED",
                    "inventario sem atividade lancada: total zero nao e presumido");
        }
        List<EmissionLine> lines = activities.stream().map(activity -> {
            var factor = catalog.find(version, activity.source())
                    .orElseThrow(() -> new IndicatorValidationException(
                            "EMISSION_FACTOR_NOT_FOUND",
                            "sem fator para " + activity.source() + " na versao " + version
                                    + " do catalogo [SEM FONTE]: a linha nao entra com fator "
                                    + "de outra versao nem com zero"));
            if (!factor.activityUnit().equals(activity.activityUnit())) {
                throw new IndicatorValidationException("EMISSION_FACTOR_UNIT_MISMATCH",
                        "atividade de " + activity.source() + " em " + activity.activityUnit()
                                + " nao casa com a unidade do fator (" + factor.activityUnit()
                                + ") declarada com a fonte na versao " + version);
            }
            BigDecimal emissions = activity.quantity().multiply(factor.factorValue());
            return new EmissionLine(activity.source(), activity.source().scope(),
                    activity.quantity(), activity.activityUnit(), factor.factorValue(),
                    factor.emissionUnit(), emissions, activity.origin());
        }).toList();
        // CORRECAO D3 (REVISAO-ED-90): totais so somam linhas da MESMA
        // unidade de emissao. Unidades divergentes (ex.: tCO2e x
        // kgCO2e) sao RECUSADAS: normalizar exigiria um fator de
        // conversao COM FONTE declarada e nenhuma fonte desta tarefa
        // o fornece [SEM FONTE] — somar cru seria fabricar o total.
        // A normalizacao, se um dia houver, e na carga do catalogo
        // (P-ED90-02), com a fonte da conversao citada por fator.
        List<String> units = lines.stream().map(EmissionLine::emissionUnit).distinct().toList();
        if (units.size() > 1) {
            throw new IndicatorValidationException("EMISSION_INVENTORY_EMISSION_UNIT_DIVERGENCE",
                    "totais do inventario recusados: linhas com unidades de emissao "
                            + "divergentes " + units + " — somar unidades distintas sem "
                            + "conversao com fonte seria fabricar o total [SEM FONTE]; "
                            + "normalize na carga do catalogo (P-ED90-02)");
        }
        BigDecimal scope1 = sumScope(lines, 1);
        BigDecimal scope2 = sumScope(lines, 2);
        return new InventoryResult(version, lines, scope1, scope2, scope1.add(scope2),
                units.isEmpty() ? null : units.get(0));
    }

    private static BigDecimal sumScope(List<EmissionLine> lines, int scope) {
        return lines.stream().filter(l -> l.scope() == scope)
                .map(EmissionLine::emissions).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
