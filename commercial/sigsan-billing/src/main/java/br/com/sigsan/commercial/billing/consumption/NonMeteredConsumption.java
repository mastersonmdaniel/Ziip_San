package br.com.sigsan.commercial.billing.consumption;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Consumo de ligação sem hidrômetro (relatório 02, seção 3.9). O consumo "por tarifa" é o consumo
 * mínimo da ligação ({@code TariffEngine.minimumConsumption}); aqui ficam os outros dois métodos
 * (por área e por parâmetro) e o consumo mínimo fixado na ligação.
 *
 * <p>Tabelas de consumo por área e por parâmetro são política do prestador: chegam como argumento,
 * nunca como valor padrão.
 */
public final class NonMeteredConsumption {

    private NonMeteredConsumption() {
    }

    /** Linha da tabela por área: até {@code areaFinal} m² o consumo por economia é {@code consumption}. */
    public record AreaBand(BigDecimal areaFinal, int consumption) {
    }

    /** Linha da tabela por parâmetro (pontos de utilização ou moradores): até {@code limit}. */
    public record ParameterBand(int limit, int consumption) {
    }

    /** Resultado com os avisos que a regra gerou (ex.: parâmetro ausente suposto). */
    public record Result(int consumption, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Por área: {@code áreaVirtual = área / economiasVirtuais} (2 casas, HALF_UP); o consumo por
     * economia é o da menor faixa com {@code areaFinal ≥ áreaVirtual}; total = esse consumo ×
     * {@code billableEconomies} (fator de economias, se houver, senão as economias da subcategoria).
     *
     * @throws IllegalArgumentException se nenhuma faixa comporta a área (a tabela está incompleta)
     */
    public static int byArea(BigDecimal builtArea, int virtualEconomies, List<AreaBand> table, int billableEconomies) {
        if (builtArea == null || builtArea.signum() <= 0) {
            throw new IllegalArgumentException("Área construída ausente ou não positiva: " + builtArea);
        }
        if (virtualEconomies < 1) {
            throw new IllegalArgumentException("Economias virtuais deve ser pelo menos 1");
        }
        BigDecimal virtualArea = builtArea.divide(BigDecimal.valueOf(virtualEconomies), 2, RoundingMode.HALF_UP);
        AreaBand band = table.stream()
                .filter(b -> b.areaFinal().compareTo(virtualArea) >= 0)
                .min(Comparator.comparing(AreaBand::areaFinal))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhuma faixa da tabela de consumo por área comporta " + virtualArea + " m²"));
        return band.consumption() * billableEconomies;
    }

    /**
     * Por parâmetro (pontos de utilização ou nº de moradores): o consumo por economia é o da menor faixa
     * com {@code limit ≥ parâmetro}; total = consumo × {@code billableEconomies}.
     *
     * <p>Parâmetro ausente: o legado supunha 1, o que esconde ausência de dado. Aqui o chamador diz de
     * forma explícita o que fazer: {@code valueWhenMissing = null} falha; um número é usado <b>e gera
     * aviso</b> no resultado.
     */
    public static Result byParameter(Integer parameter, List<ParameterBand> table, int billableEconomies,
                                     Integer valueWhenMissing) {
        List<String> warnings = new ArrayList<>();
        int value;
        if (parameter == null) {
            if (valueWhenMissing == null) {
                throw new IllegalArgumentException("Parâmetro do cadastro (pontos ou moradores) ausente");
            }
            value = valueWhenMissing;
            warnings.add("Parâmetro ausente no cadastro; usado " + valueWhenMissing + " por decisão explícita do tenant");
        } else {
            value = parameter;
        }
        ParameterBand band = table.stream()
                .filter(b -> b.limit() >= value)
                .min(Comparator.comparingInt(ParameterBand::limit))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhuma faixa da tabela de consumo por parâmetro comporta " + value));
        return new Result(band.consumption() * billableEconomies, warnings);
    }

    /**
     * Consumo mínimo fixado na ligação: se o consumo apurado é menor que o mínimo fixado, vale o fixado.
     * Sem mínimo fixado ({@code null}), o consumo é mantido.
     */
    public static int applyFixedMinimum(int consumption, Integer fixedMinimum) {
        if (fixedMinimum != null && consumption < fixedMinimum) {
            return fixedMinimum;
        }
        return consumption;
    }
}
