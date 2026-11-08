package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Catalogo GLOBAL e VERSIONADO de fatores de emissao (aceite da ED-90,
 * GES-21: {@code esg.emission_factors_version}).
 *
 * <p>FRONTEIRA DURA — nenhum valor fabricado: esta entrega NAO semeia
 * fator nenhum. Cada fator so entra com a FONTE CITADA (descricao da
 * fonte obrigatoria: publicacao, tabela e vigencia de onde o numero
 * saiu); entrada sem fonte e recusada com o marcador [SEM FONTE].
 * O catalogo nasce vazio e a carga dos fatores reais (com as fontes)
 * e da integracao/implantacao — pendencia nomeada P-ED90-02.
 *
 * <p>Uma importacao = uma versao: o inventario so calcula com a versao
 * declarada no parametro do tenant ({@link EnergyParameters}), e fator
 * ausente naquela versao e recusa nomeada — nunca o fator de outra
 * versao nem um fator "padrao".
 */
public final class EmissionFactorCatalog {

    /**
     * Um fator: quanto emite (na unidade de emissao declarada) por
     * unidade de atividade, numa versao do catalogo, com a fonte.
     * A unidade de atividade (ex.: a unidade em que a atividade e
     * medida) e declarada COM a fonte — o inventario exige o casamento
     * exato com a unidade da atividade lancada.
     */
    public record EmissionFactor(String catalogVersion, EmissionSource source,
                                 BigDecimal factorValue, String activityUnit,
                                 String emissionUnit, String sourceDescription) {
        public EmissionFactor {
            if (catalogVersion == null || catalogVersion.isBlank()) {
                throw new IndicatorValidationException("EMISSION_FACTOR_VERSION_REQUIRED",
                        "fator de emissao sem a versao do catalogo global");
            }
            if (source == null) {
                throw new IndicatorValidationException("EMISSION_FACTOR_SOURCE_KIND_REQUIRED",
                        "fator de emissao sem a fonte do inventario (eletricidade, metano, combustivel)");
            }
            if (factorValue == null || factorValue.signum() <= 0) {
                throw new IndicatorValidationException("EMISSION_FACTOR_VALUE_INVALID",
                        "fator de emissao deve ser positivo: " + factorValue);
            }
            if (activityUnit == null || activityUnit.isBlank()) {
                throw new IndicatorValidationException("EMISSION_FACTOR_ACTIVITY_UNIT_REQUIRED",
                        "fator de emissao sem a unidade de atividade declarada com a fonte");
            }
            if (emissionUnit == null || emissionUnit.isBlank()) {
                throw new IndicatorValidationException("EMISSION_FACTOR_EMISSION_UNIT_REQUIRED",
                        "fator de emissao sem a unidade de emissao declarada com a fonte");
            }
            if (sourceDescription == null || sourceDescription.isBlank()) {
                throw new IndicatorValidationException("EMISSION_FACTOR_SOURCE_REQUIRED",
                        "fator de emissao sem a fonte citada [SEM FONTE]: nenhum fator "
                                + "entra no catalogo sem a publicacao/tabela de origem");
            }
        }
    }

    private final List<EmissionFactor> factors = new ArrayList<>();

    /** Acrescenta um fator; o mesmo (versao, fonte) duas vezes e recusa. */
    public EmissionFactorCatalog add(EmissionFactor factor) {
        if (find(factor.catalogVersion(), factor.source()).isPresent()) {
            throw new IndicatorValidationException("EMISSION_FACTOR_DUPLICATED",
                    "catalogo " + factor.catalogVersion() + " ja tem fator para "
                            + factor.source() + ": uma versao e imutavel; correcao e versao nova");
        }
        factors.add(factor);
        return this;
    }

    public Optional<EmissionFactor> find(String catalogVersion, EmissionSource source) {
        return factors.stream()
                .filter(f -> f.catalogVersion().equals(catalogVersion) && f.source() == source)
                .findFirst();
    }

    public List<EmissionFactor> factors() {
        return List.copyOf(factors);
    }
}
