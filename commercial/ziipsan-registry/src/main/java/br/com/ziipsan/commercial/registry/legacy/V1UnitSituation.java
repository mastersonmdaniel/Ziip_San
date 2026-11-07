package br.com.ziipsan.commercial.registry.legacy;

/**
 * P2.10: a situação de ocupação com que a unidade migrada entra, pela
 * CAD-68 da P1.41. Regra pura:
 *
 * <ul>
 *   <li>unidade SEM usuário vigente (sem nome de titular no staging, ou
 *       com documento de espécie {@code NONE}) → {@code VACANT} — é a
 *       exceção controlada da P1.41: {@code VACANT} só sem usuário
 *       vigente, com o padrão de fábrica do parâmetro
 *       {@code registry.vacant_unit_holder} (sem titular, fatura retida)
 *       aplicado pelo cadastro, não por esta importação;</li>
 *   <li>unidade COM usuário vigente → {@code OCCUPIED};</li>
 *   <li>titular presente mas com documento inválido não chega aqui: a
 *       unidade tem erro no staging da P1.2 e é recusada na aplicação
 *       antes da situação.</li>
 * </ul>
 *
 * <p>Esta regra NÃO duplica a P1.41: ela apenas decide, na borda da
 * importação, qual valor de {@code occupancy_status} a unidade aplicada
 * recebe — a coluna, o parâmetro e o ciclo de vida da situação são da
 * P1.41.
 */
public final class V1UnitSituation {

    public static final String OCCUPIED = "OCCUPIED";
    public static final String VACANT = "VACANT";

    private V1UnitSituation() {
    }

    /** A unidade encenada tem usuário vigente? */
    public static boolean hasCurrentHolder(StagedUnit unit) {
        if (unit == null) {
            return false;
        }
        if ("NONE".equals(unit.holderDocumentKind())) {
            return false;
        }
        return unit.holderName() != null && !unit.holderName().isBlank();
    }

    /** A {@code occupancy_status} (CAD-68) com que a unidade entra. */
    public static String occupancyStatusFor(StagedUnit unit) {
        return hasCurrentHolder(unit) ? OCCUPIED : VACANT;
    }
}
