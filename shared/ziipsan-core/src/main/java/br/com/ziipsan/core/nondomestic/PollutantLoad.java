package br.com.ziipsan.core.nondomestic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Carga poluidora de um lancamento: a massa de um parametro no
 * volume lancado, a partir da concentracao medida.
 *
 * <p>Formula declarada (conversao de unidades, nao regra de
 * negocio): concentracao em mg/L equivale a g/m3; logo
 * {@code carga (kg) = concentracao (mg/L) x volume (m3) / 1000}.
 * Concentracao e volume sao SEMPRE medidos/declarados do processo
 * (amostragem do prestador ou automonitoramento avaliado) — nenhuma
 * concentracao tipica por ramo e presumida aqui [SEM FONTE].
 */
public final class PollutantLoad {

    /** Uma medicao de carga: concentracao de um parametro no volume do periodo. */
    public record LoadMeasurement(String parameterKey, BigDecimal concentrationMgPerL,
                                  BigDecimal volumeM3) {
        public LoadMeasurement {
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey da carga e obrigatorio");
            if (concentrationMgPerL == null || concentrationMgPerL.signum() < 0)
                throw new IllegalArgumentException("concentracao de " + parameterKey
                        + " deve ser informada e nao negativa (medida, nunca presumida)");
            if (volumeM3 == null || volumeM3.signum() <= 0)
                throw new IllegalArgumentException("volume do lancamento de " + parameterKey
                        + " deve ser maior que zero");
        }

        /** Carga deste parametro, em kg, com 4 casas (conversao declarada no tipo). */
        public BigDecimal loadKg() {
            return concentrationMgPerL.multiply(volumeM3)
                    .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
    }

    private PollutantLoad() {}

    /** Carga total do periodo: soma das cargas por parametro, em kg (4 casas). */
    public static BigDecimal totalLoadKg(List<LoadMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty())
            throw new IllegalArgumentException("carga por parametro nao informada:"
                    + " cobranca por carga nao se calcula sem medicao");
        return measurements.stream().map(LoadMeasurement::loadKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
