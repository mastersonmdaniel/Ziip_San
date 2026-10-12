package br.com.sigsan.commercial.billing.tariff;

import java.util.UUID;

/**
 * Economias de uma unidade consumidora numa categoria/subcategoria.
 *
 * @param subcategoryId   {@code null} quando a tarifa é por categoria
 * @param socialEconomies quantas das {@code economies} são enquadradas na tarifa social (0 se nenhuma);
 *                        o enquadramento é decisão do cadastro, não do cálculo
 */
public record EconomyGroup(UUID categoryId, UUID subcategoryId, int economies, int socialEconomies) {

    public EconomyGroup {
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
    public static EconomyGroup of(UUID categoryId, int economies) {
        return new EconomyGroup(categoryId, null, economies, 0);
    }
}
