package br.com.ziipsan.platform.orgscope;

import java.util.UUID;

/**
 * A ligação de UMA unidade organizacional a UMA área de território
 * (espelho de {@code org_unit_areas}, V1_8). A área é referência
 * polimórfica ao registry ({@code localities} / {@code commercial_sectors}
 * / {@code reading_routes}); a existência dela é conferida na ligação pela
 * porta de território — link para área inexistente não se grava.
 */
public record OrgAreaLink(UUID orgUnitId, OrgAreaType areaType, UUID areaId) {

    public OrgAreaLink {
        if (orgUnitId == null || areaType == null || areaId == null) {
            throw new IllegalArgumentException("ligação unidade–área exige unidade, tipo e id da área");
        }
    }
}
