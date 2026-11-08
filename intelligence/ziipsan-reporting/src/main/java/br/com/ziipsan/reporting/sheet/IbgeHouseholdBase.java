package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Base de domicilios do IBGE, GLOBAL e VERSIONADA (aceite da ED-77):
 * uma importacao = uma versao, com fonte declarada obrigatoria. Nenhum
 * numero de domicilio e semeado em codigo ou SQL - a base real e importada
 * na integracao a partir da publicacao do IBGE do ciclo. Recorte duplicado
 * na mesma versao e recusa; urbano + rural tem de fechar no total.
 */
public final class IbgeHouseholdBase {

    public record HouseholdEntry(String municipalityCode, long totalHouseholds,
                                 long urbanHouseholds, long ruralHouseholds) {
        public HouseholdEntry {
            if (municipalityCode == null || municipalityCode.isBlank()) {
                throw new IndicatorValidationException("IBGE_MUNICIPALITY_REQUIRED",
                        "linha da base IBGE sem municipio");
            }
            if (totalHouseholds < 0 || urbanHouseholds < 0 || ruralHouseholds < 0) {
                throw new IndicatorValidationException("IBGE_HOUSEHOLDS_NEGATIVE",
                        "domicilios negativos em " + municipalityCode);
            }
            if (urbanHouseholds + ruralHouseholds != totalHouseholds) {
                throw new IndicatorValidationException("IBGE_ZONE_SUM_MISMATCH",
                        "urbano + rural difere do total em " + municipalityCode
                                + " (base IBGE recusada, sem ajuste silencioso)");
            }
        }
    }

    private final String baseVersion;
    private final String sourceDescription;
    private final Map<String, HouseholdEntry> entries = new LinkedHashMap<>();

    public IbgeHouseholdBase(String baseVersion, String sourceDescription) {
        if (baseVersion == null || baseVersion.isBlank()) {
            throw new IndicatorValidationException("IBGE_BASE_VERSION_REQUIRED",
                    "base de domicilios do IBGE sem versao: base global e versionada (ED-77)");
        }
        if (sourceDescription == null || sourceDescription.isBlank()) {
            throw new IndicatorValidationException("IBGE_SOURCE_REQUIRED",
                    "base IBGE " + baseVersion + " sem fonte declarada: importacao recusada");
        }
        this.baseVersion = baseVersion;
        this.sourceDescription = sourceDescription;
    }

    public void add(HouseholdEntry entry) {
        if (entries.putIfAbsent(entry.municipalityCode(), entry) != null) {
            throw new IndicatorValidationException("IBGE_DUPLICATE_MUNICIPALITY",
                    "municipio " + entry.municipalityCode() + " duplicado na base " + baseVersion);
        }
    }

    public Optional<HouseholdEntry> entry(String municipalityCode) {
        return Optional.ofNullable(entries.get(municipalityCode));
    }

    public HouseholdEntry require(String municipalityCode) {
        return entry(municipalityCode).orElseThrow(() -> new IndicatorValidationException(
                "IBGE_MUNICIPALITY_NOT_IN_BASE",
                "municipio " + municipalityCode + " ausente da base IBGE " + baseVersion
                        + ": cobertura nao calculada sem denominador com fonte"));
    }

    public String baseVersion() { return baseVersion; }
    public String sourceDescription() { return sourceDescription; }
    public int size() { return entries.size(); }
}
