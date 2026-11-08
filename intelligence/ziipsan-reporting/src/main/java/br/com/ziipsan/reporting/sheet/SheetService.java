package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;
import br.com.ziipsan.reporting.sheet.SheetCatalogEntry.DataOrigin;
import br.com.ziipsan.reporting.sheet.SheetCatalogEntry.SheetVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Motor de fichas da ED-77: materializa o catalogo de fabrica NO registro
 * unico da P3.41 (sem registro proprio) e le as fichas de volta como
 * {@link IndicatorSheet}. A conversao de origens segue o precedente da
 * ED-48: variavel automatica (IBGE, ED-92 ou view) exige o mapeamento
 * explicito para a view rpt_* do nucleo da P3.22 que a alimenta na
 * integracao (nenhuma view e presumida); variavel MANUAL exige responsavel
 * nomeado e entra com o grau da ficha declarado por quem materializa -
 * para o tema manual da ED-77 o grau e forcado LOW por
 * {@link ManualIndicatorInputs}. TODAS as conversoes acontecem antes de
 * qualquer define: recusa nao deixa meio catalogo gravado.
 * Objetivos da P3.31 leem os mesmos indicadores pelo registro (a ligacao
 * objetivo-indicador da P3.31 nao e reimplementada aqui).
 */
public final class SheetService {

    private final IndicatorRegistryService registry;

    public SheetService(IndicatorRegistryService registry) {
        this.registry = registry;
    }

    public List<IndicatorDefinition> materialize(IndicatorAccess access, UUID tenantId,
                                                 ConfidenceGrade declaredGrade,
                                                 Map<String, String> reportViewByVariable,
                                                 UUID manualResponsibleId,
                                                 ParameterVersionRule.Validity formulaValidity,
                                                 UUID actor) {
        List<SheetCatalogEntry> entries = FactorySheetCatalog.entries();
        // 1) Converte tudo antes de gravar qualquer definicao.
        List<List<IndicatorVariable>> converted = new ArrayList<>();
        for (SheetCatalogEntry entry : entries) {
            converted.add(convert(entry, reportViewByVariable, manualResponsibleId));
        }
        // 2) Grava no registro unico da P3.41.
        List<IndicatorDefinition> defined = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            SheetCatalogEntry entry = entries.get(i);
            ConfidenceGrade grade = entry.code().startsWith("MANUAL.")
                    ? ManualIndicatorInputs.FORCED_GRADE : declaredGrade;
            if (grade == null) {
                throw new IndicatorValidationException("SHEET_CONFIDENCE_GRADE_REQUIRED",
                        "materializacao exige o grau de confianca declarado (ficha da P3.41)");
            }
            IndicatorDefinition definition = new IndicatorDefinition(tenantId, entry.code(),
                    entry.title(), entry.definitionText(), entry.unit(), entry.periodicity(),
                    grade, entry.context(), entry.framework().name(), Set.of(), converted.get(i));
            defined.add(registry.define(access, definition, entry.formulaExpression(),
                    formulaValidity, actor));
        }
        return List.copyOf(defined);
    }

    /** Le a ficha de um indicador ja definido no registro (sem registro proprio). */
    public IndicatorSheet sheet(UUID tenantId, String code, SheetFramework framework,
                                SheetLevel level, List<ZoneSplit> splits) {
        return new IndicatorSheet(registry.definition(tenantId, code), framework, level, splits);
    }

    private List<IndicatorVariable> convert(SheetCatalogEntry entry,
                                            Map<String, String> reportViewByVariable,
                                            UUID manualResponsibleId) {
        List<IndicatorVariable> out = new ArrayList<>();
        for (SheetVariable variable : entry.variables()) {
            if (variable.origin() == DataOrigin.MANUAL_LOW_CONFIDENCE) {
                if (manualResponsibleId == null) {
                    throw new IndicatorValidationException("SHEET_MANUAL_RESPONSIBLE_REQUIRED",
                            "variavel " + variable.name() + " e manual e exige responsavel nomeado");
                }
                out.add(ManualIndicatorInputs.energy(variable.name(), manualResponsibleId));
            } else {
                String view = reportViewByVariable == null ? null : reportViewByVariable.get(variable.name());
                if (view == null || view.isBlank()) {
                    throw new IndicatorValidationException("SHEET_VARIABLE_VIEW_MAPPING_REQUIRED",
                            "variavel " + variable.name() + " (origem " + variable.origin()
                                    + ") exige o mapeamento explicito para a view rpt_* do nucleo "
                                    + "da P3.22 que a alimenta; nenhuma view e presumida pela ED-77");
                }
                out.add(IndicatorVariable.fromReportView(variable.name(), view));
            }
        }
        return out;
    }
}
