package br.com.ziipsan.core.access;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ator de plataforma que pede o acesso (P3.17; papéis da P0.43).
 *
 * <p>Os papéis são os do realm de plataforma da P0.43 —
 * {@code PLATFORM_OWNER}, {@code PLATFORM_OPS}, {@code PLATFORM_SUPPORT} e
 * {@code PLATFORM_FINANCE}. Papel fora desse domínio é dado inválido e o
 * construtor recusa: um papel desconhecido nunca vira "algum papel" por
 * omissão (negação por padrão, ADR-0040).
 *
 * <p>{@code mfaStepUpAt} é o instante da reautenticação com segundo fator
 * DESTA requisição, como entregue pela camada de autenticação (a P0.43 já
 * recusa rota {@code /api/v1/platform/*} sem {@code amr} de segundo fator);
 * {@code null} = nenhuma reautenticação apresentada. A regra de emergência
 * ("reautenticação com MFA no momento") compara este instante com o relógio
 * injetado no momento da abertura — ver {@link AccessPolicy}.
 */
public record PlatformActor(UUID userId, String role, Instant mfaStepUpAt) {

    /** Papéis de plataforma da P0.43. */
    public static final Set<String> PLATFORM_ROLES = Set.of(
            "PLATFORM_OWNER", "PLATFORM_OPS", "PLATFORM_SUPPORT", "PLATFORM_FINANCE");

    public static final String PLATFORM_OWNER = "PLATFORM_OWNER";

    public PlatformActor {
        Objects.requireNonNull(userId, "userId do ator de plataforma é obrigatório");
        if (role == null || !PLATFORM_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "role fora do domínio da P0.43 (PLATFORM_OWNER, PLATFORM_OPS, "
                            + "PLATFORM_SUPPORT, PLATFORM_FINANCE): " + role);
        }
    }

    public boolean isPlatformOwner() {
        return PLATFORM_OWNER.equals(role);
    }
}
