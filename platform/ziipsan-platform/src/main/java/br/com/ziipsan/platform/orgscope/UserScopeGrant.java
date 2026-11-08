package br.com.ziipsan.platform.orgscope;

import java.time.Instant;
import java.util.UUID;

/**
 * UMA concessão de escopo a um usuário (P3.30, espelho de
 * {@code user_org_scopes} da V1_8). Exatamente UMA forma por concessão, como
 * no CHECK da migration:
 * <ul>
 *   <li>{@code unrestricted = true} — o tenant inteiro (sem unidade nem área);</li>
 *   <li>{@code orgUnitId} — a unidade e sua subárvore, com as áreas de todas;</li>
 *   <li>{@code areaType + areaId} — a área direta (localidade, setor ou rota).</li>
 * </ul>
 * {@code grantedBy} é quem concedeu: a linha é a trilha da concessão, e a
 * {@link GrantRule} garante que o concedente só concedeu o que possuía.
 */
public record UserScopeGrant(UUID id, UUID tenantId, UUID userId, UUID orgUnitId,
                             OrgAreaType areaType, UUID areaId, boolean unrestricted,
                             UUID grantedBy, Instant createdAt) {

    public UserScopeGrant {
        if (id == null || tenantId == null || userId == null || grantedBy == null || createdAt == null) {
            throw new IllegalArgumentException("concessão de escopo exige id, tenant, usuário, concedente e criação");
        }
        boolean byUnit = orgUnitId != null;
        boolean byArea = areaType != null || areaId != null;
        if (unrestricted && (byUnit || byArea)) {
            throw new IllegalArgumentException("concessão irrestrita não combina com unidade nem área");
        }
        if (!unrestricted && byUnit == byArea) {
            throw new IllegalArgumentException("concessão exige exatamente uma forma: unidade OU área");
        }
        if (byArea && (areaType == null || areaId == null)) {
            throw new IllegalArgumentException("concessão por área exige tipo e id da área");
        }
    }

    public static UserScopeGrant unrestrictedGrant(UUID id, UUID tenantId, UUID userId,
                                                     UUID grantedBy, Instant createdAt) {
        return new UserScopeGrant(id, tenantId, userId, null, null, null, true, grantedBy, createdAt);
    }

    public static UserScopeGrant orgUnitGrant(UUID id, UUID tenantId, UUID userId, UUID orgUnitId,
                                              UUID grantedBy, Instant createdAt) {
        return new UserScopeGrant(id, tenantId, userId, orgUnitId, null, null, false, grantedBy, createdAt);
    }

    public static UserScopeGrant areaGrant(UUID id, UUID tenantId, UUID userId,
                                           OrgAreaType areaType, UUID areaId,
                                           UUID grantedBy, Instant createdAt) {
        return new UserScopeGrant(id, tenantId, userId, null, areaType, areaId, false, grantedBy, createdAt);
    }
}
