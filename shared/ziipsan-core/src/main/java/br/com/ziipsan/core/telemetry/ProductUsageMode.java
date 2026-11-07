package br.com.ziipsan.core.telemetry;

/**
 * P4.8 / PLT-42: valor do parâmetro {@code telemetry.product_usage}.
 *
 * <p>O aceite fixa o parâmetro e o padrão: "pelo parâmetro
 * {@code telemetry.product_usage} (padrão agregado)". Os valores aqui são
 * a leitura direta dessa frase: {@link #AGGREGATED} é o padrão (só
 * agregado por tenant e por papel, sem pessoa) e {@link #OFF} desliga a
 * coleta — nenhum outro modo existe, em especial NENHUM modo com
 * identificador de pessoa, gravação de sessão ou conteúdo, que o aceite
 * veda em qualquer configuração. Valor desconhecido vindo do parâmetro
 * NÃO cai no padrão em silêncio: {@link #fromParameter} recusa, porque
 * aceitar um modo não previsto como se fosse o agregado esconderia um
 * erro de configuração de privacidade.
 */
public enum ProductUsageMode {
    AGGREGATED,
    OFF;

    /** Padrão do parâmetro quando ele não está definido para o tenant. */
    public static ProductUsageMode defaultMode() {
        return AGGREGATED;
    }

    public static ProductUsageMode fromParameter(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultMode();
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Valor inválido de telemetry.product_usage: '" + raw
                            + "' (valores aceitos: AGGREGATED, OFF; padrão AGGREGATED)", e);
        }
    }
}
