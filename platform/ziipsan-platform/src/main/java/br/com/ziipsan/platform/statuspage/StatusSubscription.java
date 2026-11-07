package br.com.ziipsan.platform.statuspage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * P4.8: assinatura por e-mail das atualizações da página de status da
 * P3.18 (ADR-0020). Quem assina escolhe componentes (vazio = todos os
 * visíveis) e só recebe depois de CONFIRMAR o endereço (dupla
 * confirmação): assinatura não confirmada não recebe nada — sem isso,
 * qualquer um inscreveria o e-mail de terceiros no fluxo de incidentes.
 *
 * <p>Estados: {@code PENDING} (aguardando confirmação) →
 * {@code ACTIVE} (confirmada) → {@code UNSUBSCRIBED} (cancelada, não
 * recebe e não volta por confirmação tardia). O token de confirmação é
 * opaco e gerado por quem chama (a fiação usa o mesmo gerador dos demais
 * tokens da plataforma); aqui só se compara — nenhum token é inventado
 * nem derivado do e-mail.
 */
public record StatusSubscription(
        String email,
        List<String> componentKeys,
        State state,
        String confirmationToken,
        Instant createdAt,
        Instant confirmedAt) {

    public enum State { PENDING, ACTIVE, UNSUBSCRIBED }

    public StatusSubscription {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("E-mail de assinatura inválido: " + email);
        }
        componentKeys = componentKeys == null ? List.of() : List.copyOf(componentKeys);
        Objects.requireNonNull(state, "state é obrigatório");
        Objects.requireNonNull(createdAt, "createdAt é obrigatório");
        if (state == State.PENDING && (confirmationToken == null || confirmationToken.isBlank())) {
            throw new IllegalArgumentException("Assinatura PENDING exige token de confirmação");
        }
        if (state == State.ACTIVE && confirmedAt == null) {
            throw new IllegalArgumentException("Assinatura ACTIVE exige confirmedAt");
        }
    }

    /** Nova assinatura, ainda pendente de confirmação. */
    public static StatusSubscription subscribe(String email, List<String> componentKeys,
                                               String confirmationToken, Instant now) {
        return new StatusSubscription(email, componentKeys, State.PENDING, confirmationToken, now, null);
    }

    /** Confirma com o token certo; token errado ou estado final é recusa explícita. */
    public StatusSubscription confirm(String token, Instant now) {
        if (state == State.UNSUBSCRIBED) {
            throw new IllegalStateException("Assinatura cancelada não pode ser confirmada");
        }
        if (state == State.ACTIVE) {
            return this;
        }
        if (confirmationToken == null || !confirmationToken.equals(token)) {
            throw new IllegalArgumentException("Token de confirmação não confere");
        }
        return new StatusSubscription(email, componentKeys, State.ACTIVE, null, createdAt, now);
    }

    /** Cancela: não recebe mais nada, em nenhum componente. */
    public StatusSubscription unsubscribe() {
        return new StatusSubscription(email, componentKeys, State.UNSUBSCRIBED, null, createdAt, confirmedAt);
    }

    public boolean receivesNotifications() {
        return state == State.ACTIVE;
    }

    /** Interesse neste componente: lista vazia = todos; senão, só os escolhidos. */
    public boolean interestedIn(String componentKey) {
        return componentKeys.isEmpty() || componentKeys.contains(componentKey);
    }

    /** Interesse em um incidente: basta um componente afetado entre os escolhidos. */
    public boolean interestedInAny(List<String> affectedComponentKeys) {
        return componentKeys.isEmpty() || affectedComponentKeys.stream().anyMatch(this::interestedIn);
    }
}
