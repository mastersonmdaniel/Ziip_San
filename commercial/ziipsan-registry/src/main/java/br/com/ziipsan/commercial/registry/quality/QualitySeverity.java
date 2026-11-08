package br.com.ziipsan.commercial.registry.quality;

/**
 * Severidade de um achado de qualidade cadastral (P3.32, CAD-98).
 *
 * <p>Os pontos sao a moeda da pontuacao por rota/setor de
 * {@link QualityScoreCalculator}: a formula e documentada la e os testes a
 * recomputam a mao. A severidade efetiva de cada regra vem do parametro
 * {@code quality.rules} (classe d) quando o tenant a declara; sem declaracao,
 * vale o padrao de fabrica de {@link QualityRuleCode}.
 */
public enum QualitySeverity {

    LOW(1),
    MEDIUM(2),
    HIGH(5),
    CRITICAL(10);

    private final int points;

    QualitySeverity(int points) {
        this.points = points;
    }

    /** Pontos de penalidade do achado na pontuacao do grupo. */
    public int points() {
        return points;
    }

    /** Le a severidade do JSON de {@code quality.rules}; fora do catalogo e erro explicito. */
    public static QualitySeverity fromKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("quality.rules: severidade vazia");
        }
        for (QualitySeverity severity : values()) {
            if (severity.name().equals(key.strip())) {
                return severity;
            }
        }
        throw new IllegalStateException("quality.rules: severidade fora do catalogo "
                + "(LOW, MEDIUM, HIGH, CRITICAL): " + key);
    }
}
