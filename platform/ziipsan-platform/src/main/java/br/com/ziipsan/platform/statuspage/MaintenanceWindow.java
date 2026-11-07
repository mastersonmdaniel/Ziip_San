package br.com.ziipsan.platform.statuspage;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * P3.18: janela de manutenção publicada na página de status
 * ({@code support.maintenance_window}, core {@code V10_4}).
 *
 * <p>Regra do aceite: a janela só é publicável com pelo menos 72 h de
 * antecedência entre a publicação e o início. A regra é imposta aqui, na
 * única porta de construção ({@link #publish}), e repetida no banco como
 * {@code CHECK (starts_at >= published_at + INTERVAL '72 hours')} — nem
 * um INSERT direto publica janela em cima da hora. Publicar com menos de
 * 72 h é recusa explícita, nunca ajuste silencioso do horário.
 *
 * @param componentKey componente afetado; {@code null} = janela global
 *                     (todos os componentes visíveis)
 */
public record MaintenanceWindow(
        String title,
        String description,
        String componentKey,
        Instant startsAt,
        Instant endsAt,
        Instant publishedAt) {

    /** Antecedência mínima de publicação exigida pelo aceite da P3.18. */
    public static final Duration MINIMUM_NOTICE = Duration.ofHours(72);

    public MaintenanceWindow {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Título da janela de manutenção é obrigatório");
        }
        Objects.requireNonNull(startsAt, "starts_at da janela é obrigatório");
        Objects.requireNonNull(endsAt, "ends_at da janela é obrigatório");
        Objects.requireNonNull(publishedAt, "published_at da janela é obrigatório");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Janela de manutenção deve terminar depois de começar");
        }
        if (componentKey != null && componentKey.isBlank()) {
            throw new IllegalArgumentException("component_key da janela não pode ser vazio (use null = global)");
        }
        if (startsAt.isBefore(publishedAt.plus(MINIMUM_NOTICE))) {
            throw new IllegalArgumentException(
                    "Janela de manutenção recusada: o aceite da P3.18 exige publicação com pelo menos 72 h "
                            + "de antecedência (início " + startsAt + ", publicação " + publishedAt + ")");
        }
    }

    /**
     * Publica uma janela agora ({@code publishedAt} = instante da
     * publicação, entregue por quem chama para o teste usar relógio fixo).
     * É o mesmo construtor — a regra das 72 h vale para qualquer caminho.
     */
    public static MaintenanceWindow publish(String title, String description, String componentKey,
                                            Instant startsAt, Instant endsAt, Instant publishedAt) {
        return new MaintenanceWindow(title, description, componentKey, startsAt, endsAt, publishedAt);
    }

    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public boolean isUpcomingAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return now.isBefore(startsAt);
    }

    public boolean affects(String key) {
        return componentKey == null || componentKey.equals(key);
    }
}
