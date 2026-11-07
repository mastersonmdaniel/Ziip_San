package br.com.ziipsan.platform.observability;

/**
 * P3.11: definição de SLO usada no cálculo de burn rate.
 *
 * @param name          nome do SLI/SLO (ex.: {@code api.availability})
 * @param targetPercent meta de disponibilidade no período, ex.: {@code 99.9}
 * @param windowDays    janela do orçamento de erro, em dias (padrão do projeto: 30)
 */
public record SloDefinition(String name, double targetPercent, int windowDays) {

    public SloDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do SLO é obrigatório");
        }
        if (!(targetPercent > 0.0 && targetPercent < 100.0)) {
            throw new IllegalArgumentException(
                    "Meta do SLO deve estar entre 0 e 100 (exclusivo): " + targetPercent);
        }
        if (windowDays < 1) {
            throw new IllegalArgumentException("Janela do SLO deve ser >= 1 dia");
        }
    }

    /** Fração de erro permitida: SLO 99,9% → 0,001. */
    public double allowedErrorRate() {
        return (100.0 - targetPercent) / 100.0;
    }
}
