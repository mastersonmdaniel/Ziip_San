package br.com.ziipsan.platform.user;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Atribuição de um perfil de fábrica do catálogo P0.13 a um usuário, com vigência
 * (tabela {@code user_profiles} da V1_9). Um usuário pode ter mais de um perfil; a
 * permissão efetiva é a união das permissões dos perfis vigentes. {@code validUntil} nulo
 * significa vigente sem fim previsto.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
public class UserProfileAssignment extends TenantAwareEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Nome do perfil de fábrica (os 26 do catálogo P1.37), validado no serviço. */
    @Column(name = "profile", nullable = false, length = 20)
    private String profile;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;
}
