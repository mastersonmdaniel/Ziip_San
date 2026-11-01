package br.com.sigsan.commercial.billing.tariff;

import java.util.UUID;

/**
 * Economias de uma unidade consumidora numa categoria/subcategoria.
 *
 * @param subcategoryId   {@code null} quando a tarifa é por categoria
 * @param socialEconomies quantas das {@code economies} são enquadradas na tarifa social (0 se nenhuma);
 *                        o enquadramento é decisão do cadastro, não do cálculo
 */
public record TariffGroup(UUID categoryId, UUID subcategoryId, int economies, int socialEconomies) {
    // P0.25: antes EconomyGroup; renomeado para TariffGroup porque o metering ja tem um
    // EconomyGroup (codigo de categoria + regras) e os dois tipos sao incompativeis.
    // Um dono por nome: o grupo da tarifa vive aqui, o do metering vive la.

    public TariffGroup {
        if (categoryId == null) {
            throw new IllegalArgumentException("Grupo de economias sem categoria");
        }
        if (economies < 1) {
            throw new IllegalArgumentException("Economias deve ser pelo menos 1: " + economies);
        }
        if (socialEconomies < 0 || socialEconomies > economies) {
            throw new IllegalArgumentException("Economias sociais fora de [0, " + economies + "]: " + socialEconomies);
        }
    }

    /** Grupo sem economias sociais. */
    public static TariffGroup of(UUID categoryId, int economies) {
        return new TariffGroup(categoryId, null, economies, 0);
    }
}
