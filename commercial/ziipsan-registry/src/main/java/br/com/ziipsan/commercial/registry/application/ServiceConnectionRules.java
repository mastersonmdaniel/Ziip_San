package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus.SystemRole;
import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;

import java.util.UUID;

/**
 * Regras puras da ligação (GIS-05, P2.23), sem banco: a âncora do
 * imóvel físico e o papel de sistema derivado das flags do catálogo.
 * São o espelho Java do que a V3_14 faz em SQL na carga inicial — os
 * dois lados são testados contra a mesma tabela de casos.
 */
public final class ServiceConnectionRules {

    private ServiceConnectionRules() {
    }

    /**
     * Âncora do imóvel físico (lote) de uma unidade: a unidade-mãe
     * quando a relação é {@code CONDOMINIUM_MACRO}; a própria unidade
     * nos demais casos (glossário do plano §8, COE-17).
     */
    public static UUID propertyAnchorId(ConsumerUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("unidade é obrigatória");
        }
        if ("CONDOMINIUM_MACRO".equals(unit.getParentRelationType()) && unit.getParentUnitId() != null) {
            return unit.getParentUnitId();
        }
        return unit.getId();
    }

    /**
     * Papel de sistema a partir das flags legadas do catálogo — a mesma
     * regra CASE da V3_14. Água distingue {@code supplies} (ligada mas
     * sem abastecimento = CUT); esgoto não tem essa flag no legado.
     */
    public static SystemRole systemRoleOf(boolean water, boolean active, boolean connected,
                                          boolean supplies, boolean hasConnection) {
        if (!active) {
            return SystemRole.INACTIVE;
        }
        if (connected) {
            if (water && !supplies) {
                return SystemRole.CUT;
            }
            return SystemRole.ACTIVE;
        }
        if (hasConnection) {
            return SystemRole.DISCONNECTED;
        }
        return SystemRole.NO_CONNECTION;
    }

    /** Ligação "enquanto ativa" da regra 1:1: ainda não encerrada. */
    public static boolean isOpen(java.time.LocalDate endedOn) {
        return endedOn == null;
    }
}
