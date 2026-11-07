package br.com.ziipsan.platform.statuspage;

import java.time.Instant;
import java.util.Objects;

/**
 * P3.18: um fato de sonda — a leitura mais recente de um componente com
 * sonda (persistida em {@code support.status_component.last_probe_*}).
 *
 * <p>A derivação de status consome fatos como este, nunca uma flag manual:
 * é isso que faz o status "mudar sozinho com a sonda fora" (aceite da
 * P3.18). Leitura ausente para componente com sonda não é modelada aqui —
 * quem deriva recebe {@code null} e responde {@code UNKNOWN}.
 */
public record ProbeReading(String componentKey, Outcome outcome, Instant observedAt) {

    public enum Outcome { UP, DOWN }

    public ProbeReading {
        if (componentKey == null || componentKey.isBlank()) {
            throw new IllegalArgumentException("component_key é obrigatório na leitura da sonda");
        }
        Objects.requireNonNull(outcome, "outcome da sonda é obrigatório");
        Objects.requireNonNull(observedAt, "observed_at da sonda é obrigatório");
    }
}
