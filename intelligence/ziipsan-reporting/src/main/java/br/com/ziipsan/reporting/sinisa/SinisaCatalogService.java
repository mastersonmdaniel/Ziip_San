package br.com.ziipsan.reporting.sinisa;

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
 * Materializacao do catalogo SINISA pre-configurado no registro unico de
 * indicadores da P3.41 de um tenant (aceite da ED-48, item do catalogo).
 *
 * <p>A conversao de fonte e o ponto honesto do desenho: a P3.41 so aceita
 * variavel com fonte {@code REPORT_VIEW} (view {@code rpt_*} do nucleo da
 * P3.22) ou {@code MANUAL} com responsavel. O mapa da P4.2 declara a origem
 * por natureza (METERING/BILLING/REGISTRY/DERIVED/MANUAL), mas NAO nomeia
 * view nenhuma — e nenhuma view e inventada aqui:
 * <ul>
 *   <li>variavel de origem automatica exige o mapeamento explicito
 *       {@code chave SINISA -> view rpt_*} informado por quem integra
 *       ({@code reportViewBySinisaKey}); sem mapeamento, a materializacao
 *       recusa nomeando a chave ({@code SINISA_VARIABLE_VIEW_MAPPING_REQUIRED})
 *       em vez de registrar uma fonte falsa;</li>
 *   <li>variavel MANUAL do mapa (hoje: {@code BALANCO.ATIVO_TOTAL}) exige o
 *       responsavel nomeado ({@code SINISA_MANUAL_RESPONSIBLE_REQUIRED}).</li>
 * </ul>
 * O grau de confianca da ficha tambem e parametro de quem materializa: o
 * catalogo nao presume grau (ver {@link SinisaCatalogEntry}).
 *
 * <p>Fechamento e semaforo NAO sao reimplementados: depois de
 * materializado, o indicador SINISA e um indicador comum do registro —
 * {@code close}/{@code compute}/{@code Semaphore} da P3.41 valem para ele
 * (aceite: "fechamento do periodo e semaforo do registro aplicados ao
 * catalogo SINISA"), e o painel da ED-48 le por essa mesma API.
 */
public final class SinisaCatalogService {

    /** Marcacao de perfil dos indicadores SINISA no registro (leitura por perfil da P3.41). */
    public static final String PROFILE_TAG = "SINISA";

    private final IndicatorRegistryService registry;

    public SinisaCatalogService(IndicatorRegistryService registry) {
        this.registry = registry;
    }

    /**
     * Registra todas as entradas do catalogo pre-configurado no tenant.
     * Atomica na pratica desta camada: a PRIMEIRA recusa (mapeamento ou
     * responsavel ausente) acontece na conversao de fontes, ANTES de
     * qualquer {@code define} — validam-se todas as entradas primeiro.
     */
    public List<IndicatorDefinition> registerPreconfigured(IndicatorAccess access, UUID tenantId,
                                                           Map<String, String> reportViewBySinisaKey,
                                                           UUID manualResponsibleId,
                                                           ConfidenceGrade confidenceGrade,
                                                           ParameterVersionRule.Validity formulaValidity,
                                                           UUID actor) {
        if (confidenceGrade == null) {
            throw new IndicatorValidationException("SINISA_CONFIDENCE_GRADE_REQUIRED",
                    "grau de confianca da ficha (P3.41) e obrigatorio na materializacao do "
                            + "catalogo SINISA: o catalogo nao presume grau");
        }
        List<SinisaCatalogEntry> entries = SinisaPreconfiguredCatalog.entries();
        // Conversao de TODAS as fontes antes de gravar qualquer definicao.
        List<List<IndicatorVariable>> converted = new ArrayList<>();
        for (SinisaCatalogEntry entry : entries) {
            converted.add(convertVariables(entry, reportViewBySinisaKey, manualResponsibleId));
        }
        List<IndicatorDefinition> defined = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            SinisaCatalogEntry entry = entries.get(i);
            IndicatorDefinition definition = new IndicatorDefinition(tenantId, entry.code(),
                    entry.name(), entry.definitionText(), entry.unit(), entry.periodicity(),
                    confidenceGrade, entry.context(), null, Set.of(PROFILE_TAG), converted.get(i));
            defined.add(registry.define(access, definition, entry.formulaExpression(),
                    formulaValidity, actor));
        }
        return List.copyOf(defined);
    }

    private List<IndicatorVariable> convertVariables(SinisaCatalogEntry entry,
                                                     Map<String, String> reportViewBySinisaKey,
                                                     UUID manualResponsibleId) {
        List<IndicatorVariable> out = new ArrayList<>();
        for (SinisaVariableRef ref : entry.variables()) {
            if (ref.sourceKind() == SinisaVariableRef.SourceKind.MANUAL) {
                if (manualResponsibleId == null) {
                    throw new IndicatorValidationException("SINISA_MANUAL_RESPONSIBLE_REQUIRED",
                            "variavel " + ref.sinisaVariableKey() + " tem origem MANUAL no mapa "
                                    + "da P4.2 e exige responsavel nomeado para virar fonte da P3.41");
                }
                out.add(IndicatorVariable.manual(ref.variableName(), manualResponsibleId));
            } else {
                String view = reportViewBySinisaKey == null
                        ? null : reportViewBySinisaKey.get(ref.sinisaVariableKey());
                if (view == null || view.isBlank()) {
                    throw new IndicatorValidationException("SINISA_VARIABLE_VIEW_MAPPING_REQUIRED",
                            "variavel " + ref.sinisaVariableKey() + " (origem "
                                    + ref.sourceKind() + " no mapa da P4.2) exige o mapeamento "
                                    + "explicito para a view rpt_* do nucleo da P3.22 que a "
                                    + "alimenta; nenhuma view e presumida pela ED-48");
                }
                // fromReportView valida o prefixo rpt_* (recusa nomeada da P3.41).
                out.add(IndicatorVariable.fromReportView(ref.variableName(), view));
            }
        }
        return out;
    }
}
