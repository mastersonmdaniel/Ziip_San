package br.com.ziipsan.platform.user;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Usuário do tenant (P0.13, tabela {@code users} da V1_9).
 *
 * <p>Sem {@code password_hash} nem {@code mfa_secret}: a credencial fica só no Keycloak. O
 * vínculo com o Keycloak é o {@code sub} do token ({@code keycloakSub}): o cadastro no
 * Keycloak é manual e esta API vincula o registro pelo sub. O {@code tenantId} nunca vem do
 * cliente — vem do contexto da requisição (token), e a RLS do schema o reforça.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class TenantUser extends TenantAwareEntity {

    /** {@code sub} do Keycloak; único por tenant. */
    @Column(name = "keycloak_sub", nullable = false, length = 255)
    private String keycloakSub;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "email", length = 255)
    private String email;

    /** Domínio fechado (DAD-31), nunca texto livre. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public enum UserStatus {
        ACTIVE, SUSPENDED, DEACTIVATED
    }
}
