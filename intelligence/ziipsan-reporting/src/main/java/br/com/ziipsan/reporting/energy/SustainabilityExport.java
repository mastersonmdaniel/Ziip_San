package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * Exportacao VOLUNTARIA do inventario para o relatorio de
 * sustentabilidade (aceite da ED-90, GES-21: {@code esg.report_profile}).
 *
 * <p>Voluntaria de verdade, nos dois sentidos:
 * <ul>
 *   <li>sem o perfil declarado pelo tenant no parametro, a exportacao
 *       recusa nomeada — nao existe perfil "padrao" presumido;</li>
 *   <li>o inventario ({@link EmissionInventory}) NAO depende do
 *       perfil: ele calcula e guarda sem exportar nada. O aceite
 *       declara que nenhuma obrigacao de relatorio foi identificada
 *       para autarquia municipal — logo nenhum caminho desta entrega
 *       obriga, agenda ou cobra a exportacao.</li>
 * </ul>
 * O formato e um CSV simples e estavel (perfil, versao do catalogo,
 * linhas e totais por escopo): formato de relatorio externo alem
 * disso (layout de framework de relato) nao consta das fontes e nao
 * foi inventado — pendencia apenas se um framework for adotado.
 */
public final class SustainabilityExport {

    private SustainabilityExport() {
    }

    public static String exportCsv(String parametersJson,
                                   EmissionInventory.InventoryResult inventory,
                                   String periodLabel) {
        String profile = EnergyParameters.reportProfile(parametersJson)
                .orElseThrow(() -> new IndicatorValidationException(
                        "ESG_REPORT_PROFILE_NOT_DECLARED",
                        "exportacao voluntaria exige " + EnergyParameters.REPORT_PROFILE
                                + " declarado pelo tenant: sem perfil declarado, nada e "
                                + "exportado (e nada e devido - sem obrigacao de relatorio "
                                + "identificada para autarquia municipal)"));
        if (inventory == null) {
            throw new IndicatorValidationException("ESG_INVENTORY_REQUIRED",
                    "exportacao sem o inventario calculado");
        }
        if (periodLabel == null || periodLabel.isBlank()) {
            throw new IndicatorValidationException("ESG_EXPORT_PERIOD_REQUIRED",
                    "exportacao sem o rotulo do periodo do inventario");
        }
        StringBuilder out = new StringBuilder();
        out.append("perfil;").append(profile).append('\n');
        out.append("periodo;").append(periodLabel).append('\n');
        out.append("catalogo_fatores_versao;").append(inventory.catalogVersion()).append('\n');
        // CORRECAO D3 (REVISAO-ED-90): a exportacao carrega a unidade
        // de emissao dos totais — total sem unidade nao e auditavel.
        String emissionUnit = inventory.emissionUnit() == null ? "" : inventory.emissionUnit();
        out.append("unidade_emissao;").append(emissionUnit).append('\n');
        out.append("fonte;escopo;quantidade;unidade_atividade;fator;unidade_emissao;emissoes;origem\n");
        for (EmissionInventory.EmissionLine line : inventory.lines()) {
            out.append(line.source()).append(';')
                    .append(line.scope()).append(';')
                    .append(line.quantity().toPlainString()).append(';')
                    .append(line.activityUnit()).append(';')
                    .append(line.factorValue().toPlainString()).append(';')
                    .append(line.emissionUnit()).append(';')
                    .append(line.emissions().toPlainString()).append(';')
                    .append(line.origin()).append('\n');
        }
        // Totais na mesma grade das linhas: a unidade vai na coluna
        // unidade_emissao (6a) e o valor na coluna emissoes (7a).
        out.append("total_escopo_1;;;;;").append(emissionUnit).append(';')
                .append(inventory.scope1Total().toPlainString()).append(";\n");
        out.append("total_escopo_2;;;;;").append(emissionUnit).append(';')
                .append(inventory.scope2Total().toPlainString()).append(";\n");
        out.append("total;;;;;").append(emissionUnit).append(';')
                .append(inventory.total().toPlainString()).append(";\n");
        return out.toString();
    }
}
