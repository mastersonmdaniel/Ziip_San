package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Materializacao do catalogo de energia/ESG da ED-90 no registro unico
 * de indicadores da P3.41 (aceite: "indicadores registrados no registro
 * da P3.41 e publicados no catalogo da ED-48"). Mesmo desenho do
 * SinisaCatalogService da ED-48:
 * <ul>
 *   <li>variavel AUTOMATIC exige o mapeamento explicito
 *       {@code variavel -> view rpt_*} de quem integra; sem ele, a
 *       materializacao recusa nomeando a variavel ANTES de gravar
 *       qualquer definicao (conversao de todas as fontes primeiro);</li>
 *   <li>variavel MANUAL (se uma entrada futura a declarar) exige o
 *       responsavel nomeado;</li>
 *   <li>o grau de confianca e parametro de quem materializa — o
 *       catalogo nao presume grau; o esperado e o grau ATUALIZADO da
 *       fonte automatica ({@link EnergyDataSource#confidenceGrade()});</li>
 *   <li>fechamento e semaforo sao os do registro da P3.41, sem
 *       reimplementacao; a publicacao no catalogo/painel da ED-48 se
 *       da pela marcacao de perfil {@link #PROFILE_TAG} e pela leitura
 *       do registro que a ED-48 ja faz.</li>
 * </ul>
 */
public final class EnergyCatalogService {

    /** Marcacao de perfil dos indicadores de energia/ESG no registro (leitura por perfil da P3.41). */
    public static final String PROFILE_TAG = "ENERGY_ESG";

    private final IndicatorRegistryService registry;

    public EnergyCatalogService(IndicatorRegistryService registry) {
        this.registry = registry;
    }

    public List<IndicatorDefinition> registerCatalog(IndicatorAccess access, UUID tenantId,
                                                     Map<String, String> reportViewByVariableName,
                                                     UUID manualResponsibleId,
                                                     ConfidenceGrade confidenceGrade,
                                                     ParameterVersionRule.Validity formulaValidity,
                                                     UUID actor) {
        if (confidenceGrade == null) {
            throw new IndicatorValidationException("ENERGY_CONFIDENCE_GRADE_REQUIRED",
                    "grau de confianca da ficha (P3.41) e obrigatorio na materializacao do "
                            + "catalogo de energia/ESG: o catalogo nao presume grau (o esperado "
                            + "e o grau atualizado da fonte automatica)");
        }
        List<EnergyEsgCatalog.EnergyCatalogEntry> entries = EnergyEsgCatalog.entries();
        List<List<IndicatorVariable>> converted = new ArrayList<>();
        for (EnergyEsgCatalog.EnergyCatalogEntry entry : entries) {
            converted.add(convertVariables(entry, reportViewByVariableName, manualResponsibleId));
        }
        List<IndicatorDefinition> defined = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            EnergyEsgCatalog.EnergyCatalogEntry entry = entries.get(i);
            IndicatorDefinition definition = new IndicatorDefinition(tenantId, entry.code(),
                    entry.name(), entry.definitionText(), entry.unit(), entry.periodicity(),
                    confidenceGrade, entry.context(), null, Set.of(PROFILE_TAG), converted.get(i));
            defined.add(registry.define(access, definition, entry.formulaExpression(),
                    formulaValidity, actor));
        }
        return List.copyOf(defined);
    }

    private List<IndicatorVariable> convertVariables(EnergyEsgCatalog.EnergyCatalogEntry entry,
                                                     Map<String, String> reportViewByVariableName,
                                                     UUID manualResponsibleId) {
        List<IndicatorVariable> out = new ArrayList<>();
        for (EnergyEsgCatalog.CatalogVariable variable : entry.variables()) {
            if (variable.origin() == EnergyEsgCatalog.VariableOrigin.MANUAL) {
                if (manualResponsibleId == null) {
                    throw new IndicatorValidationException("ENERGY_MANUAL_RESPONSIBLE_REQUIRED",
                            "variavel " + variable.variableName() + " tem origem MANUAL no "
                                    + "catalogo e exige responsavel nomeado para virar fonte da P3.41");
                }
                out.add(IndicatorVariable.manual(variable.variableName(), manualResponsibleId));
            } else {
                String view = reportViewByVariableName == null
                        ? null : reportViewByVariableName.get(variable.variableName());
                if (view == null || view.isBlank()) {
                    throw new IndicatorValidationException("ENERGY_VARIABLE_VIEW_MAPPING_REQUIRED",
                            "variavel " + variable.variableName() + " (origem automatica) exige "
                                    + "o mapeamento explicito para a view rpt_* do nucleo da "
                                    + "P3.22 que a alimenta; nenhuma view e presumida pela ED-90");
                }
                out.add(IndicatorVariable.fromReportView(variable.variableName(), view));
            }
        }
        return out;
    }
}
